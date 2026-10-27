package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.ModelExchange;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckModelUnavailableException;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import com.anthropic.errors.AnthropicServiceException;
import com.openai.errors.OpenAIServiceException;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;

/**
 * TODO §106 (lot A, C1, H1) — {@link PrecheckModelPort} sur le {@link ChatModel} de Spring AI : un message système, un
 * message utilisateur, <b>aucun outil déclaré</b>. Les options (modèle, plafond, format de réponse) sont propres au
 * fournisseur. Aucune erreur du fournisseur n'est relayée (son corps peut citer la requête, donc des données du dossier,
 * ou la clé) : seul un message générique remonte, la cause reste en {@code cause} pour le journal technique.
 */
final class SpringAiPrecheckModel implements PrecheckModelPort {

    private static final Logger LOG = LoggerFactory.getLogger(SpringAiPrecheckModel.class);

    private final ChatModel chatModel;
    private final String provider;
    private final String model;
    private final Function<ModelExchange.Request, ChatOptions> options;
    private final Function<ModelExchange.Request, String> system;

    SpringAiPrecheckModel(ChatModel chatModel, String provider, String model,
            Function<ModelExchange.Request, ChatOptions> options, Function<ModelExchange.Request, String> system) {
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.provider = Objects.requireNonNull(provider, "provider");
        this.model = Objects.requireNonNull(model, "model");
        this.options = Objects.requireNonNull(options, "options");
        this.system = Objects.requireNonNull(system, "system");
    }

    @Override
    public ModelExchange.Reply complete(ModelExchange.Request request) {
        Prompt prompt = new Prompt(
                List.of(new SystemMessage(system.apply(request)), new UserMessage(request.user())),
                options.apply(request));
        ChatResponse response;
        try {
            response = chatModel.call(prompt);
        } catch (RuntimeException ex) {
            // F8 (revue adversariale de #211) : de quoi distinguer une clé refusée d'une panne, sans le message du
            // fournisseur (il peut recopier la requête, donc des données du dossier).
            LOG.warn("AI pre-check provider call failed: {}", describe(ex));
            throw new PrecheckModelUnavailableException(
                    "the language model provider is unavailable or refused the request", ex);
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new PrecheckModelUnavailableException("the language model provider returned no answer");
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        String text = response.getResult().getOutput().getText();
        String finishReason = response.getResult().getMetadata() == null
                ? null : response.getResult().getMetadata().getFinishReason();
        if ("length".equalsIgnoreCase(finishReason) || "max_tokens".equalsIgnoreCase(finishReason)) {
            // Symptôme typique d'un modèle qui raisonne avant de répondre : la réflexion a consommé le plafond de
            // sortie et la réponse JSON est absente ou tronquée (le moteur la refusera). Aucun contenu journalisé.
            LOG.warn("AI pre-check answer cut at the output token limit ({} tokens, finish reason {}): a reasoning "
                    + "model may spend this budget thinking; set app.precheck.reasoning-effort=none or raise "
                    + "app.precheck.max-output-tokens", request.maxOutputTokens(), finishReason);
        }
        long tokensIn = usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
        long tokensOut = usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        if (tokensIn <= 0) {
            // F9 (revue adversariale de #211) : sans jetons déclarés, le plafond du jour ne protégerait plus rien ;
            // estimation prudente, un jeton pour quatre caractères.
            tokensIn = estimate(system.apply(request)) + estimate(request.user());
            tokensOut = Math.max(tokensOut, estimate(text));
        }
        return new ModelExchange.Reply(text, tokensIn, tokensOut, provider, model);
    }

    static long estimate(String text) {
        return text == null ? 0 : (text.length() + 3) / 4;
    }

    /** Types d'erreur de la chaîne des causes et statut HTTP du fournisseur ; jamais un message. */
    static String describe(Throwable error) {
        StringBuilder out = new StringBuilder();
        Throwable current = error;
        for (int depth = 0; current != null && depth < 6; depth++, current = current.getCause()) {
            out.append(depth == 0 ? "" : " <- ").append(current.getClass().getSimpleName());
            if (current instanceof OpenAIServiceException service) {
                out.append(" (HTTP ").append(service.statusCode()).append(')');
            } else if (current instanceof AnthropicServiceException service) {
                out.append(" (HTTP ").append(service.statusCode()).append(')');
            }
        }
        return out.toString();
    }
}
