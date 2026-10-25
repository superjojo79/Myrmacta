package be.myrmacta.infrastructure.precheck;

import be.myrmacta.domain.precheck.model.ModelExchange;
import be.myrmacta.domain.precheck.model.PrecheckExceptions.PrecheckModelUnavailableException;
import be.myrmacta.domain.precheck.port.out.PrecheckModelPort;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
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
            throw new PrecheckModelUnavailableException(
                    "the language model provider is unavailable or refused the request", ex);
        }
        if (response == null || response.getResult() == null || response.getResult().getOutput() == null) {
            throw new PrecheckModelUnavailableException("the language model provider returned no answer");
        }
        Usage usage = response.getMetadata() == null ? null : response.getMetadata().getUsage();
        return new ModelExchange.Reply(response.getResult().getOutput().getText(),
                usage == null || usage.getPromptTokens() == null ? 0 : usage.getPromptTokens(),
                usage == null || usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens(),
                provider, model);
    }
}
