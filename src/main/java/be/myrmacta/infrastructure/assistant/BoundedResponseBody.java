package be.myrmacta.infrastructure.assistant;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InterruptedIOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodySubscriber;
import java.net.http.HttpTimeoutException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Flow;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * TODO §106 (revue adversariale de #211, F1) — réponse d'un fournisseur de modèle lue <b>en entier et bornée</b> avant
 * d'être rendue au SDK : l'échange n'aboutit qu'une fois le corps reçu, si bien que le délai couvre aussi le corps. Avant,
 * le délai s'arrêtait aux en-têtes : une passerelle qui distillait le corps retenait sans fin le thread appelant (job
 * Flowable, relais, requête de l'assistant). Au-delà du délai, l'échange est annulé.
 */
public final class BoundedResponseBody {

    private BoundedResponseBody() {}

    /**
     * @return la réponse, corps compris, reçue dans {@code timeout}
     * @throws HttpTimeoutException au-delà du délai (l'échange est annulé)
     * @throws IOException corps de plus de {@code maxBytes} octets, ou échec du transport
     */
    public static HttpResponse<byte[]> send(HttpClient client, HttpRequest request, Duration timeout, int maxBytes)
            throws IOException {
        CompletableFuture<HttpResponse<byte[]>> exchange = client.sendAsync(request, info -> new Bounded(maxBytes));
        try {
            return exchange.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException late) {
            exchange.cancel(true);
            throw new HttpTimeoutException("the provider did not answer within " + timeout.toSeconds() + " s");
        } catch (ExecutionException failed) {
            throw new IOException("the provider call failed", failed.getCause());
        } catch (InterruptedException interrupted) {
            exchange.cancel(true);
            Thread.currentThread().interrupt();
            throw new InterruptedIOException("the provider call was interrupted");
        }
    }

    /** Accumule le corps ; au-delà de la borne, abandonne l'échange plutôt que de tout mettre en mémoire. */
    private static final class Bounded implements BodySubscriber<byte[]> {
        private final int max;
        private final ByteArrayOutputStream body = new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result = new CompletableFuture<>();
        private Flow.Subscription subscription;

        Bounded(int max) {
            this.max = max;
        }

        @Override
        public void onSubscribe(Flow.Subscription subscription) {
            this.subscription = subscription;
            subscription.request(Long.MAX_VALUE);
        }

        @Override
        public void onNext(List<ByteBuffer> items) {
            if (result.isDone()) {
                return;
            }
            for (ByteBuffer item : items) {
                if (body.size() + item.remaining() > max) {
                    subscription.cancel();
                    result.completeExceptionally(new IOException("the provider answer is too large"));
                    return;
                }
                byte[] chunk = new byte[item.remaining()];
                item.get(chunk);
                body.write(chunk, 0, chunk.length);
            }
        }

        @Override
        public void onError(Throwable error) {
            result.completeExceptionally(error);
        }

        @Override
        public void onComplete() {
            result.complete(body.toByteArray());
        }

        @Override
        public CompletionStage<byte[]> getBody() {
            return result;
        }
    }
}
