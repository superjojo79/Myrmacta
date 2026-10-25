package be.myrmacta.infrastructure.assistant;

import be.myrmacta.domain.assistant.model.AssistantExceptions.ModelUnavailableException;
import be.myrmacta.domain.assistant.model.ModelExchange;
import be.myrmacta.domain.assistant.model.ToolSpec;
import be.myrmacta.domain.assistant.port.out.LanguageModelPort;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import tools.jackson.databind.ObjectMapper;

/**
 * TODO §86 (LC3, D-ASSIST-PROVIDER — revue du 2026-10-02) — adaptateur de {@link LanguageModelPort} sur l'abstraction
 * {@link ChatModel} de Spring AI (starter Anthropic ; changer de fournisseur = changer de {@code ChatModel}, pas ce code).
 *
 * <p>La boucle d'agent reste celle du domaine (propose/confirm, plafonds de tours et de jetons) : le {@code ChatModel} de
 * Spring AI 2 n'exécute pas les outils (cela relève de ChatClient), les outils ne sont déclarés que par leur définition. Aucune erreur du fournisseur n'est relayée
 * (son corps peut citer la requête ou la clé) : seul un message générique remonte, la cause technique reste en {@code cause}.
 */
public final class SpringAiLanguageModel implements LanguageModelPort {

    private final ChatModel chatModel;
    private final ObjectMapper mapper;
    private final String model;

    public SpringAiLanguageModel(ChatModel chatModel, String model, ObjectMapper mapper) {
        this.model = Objects.requireNonNull(model, "model");
        this.chatModel = Objects.requireNonNull(chatModel, "chatModel");
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    @Override
    public ModelExchange.Reply complete(ModelExchange.Request request) {
        ChatResponse response;
        try {
            response = chatModel.call(toPrompt(request));
        } catch (ModelUnavailableException ex) {
            throw ex;
        } catch (RuntimeException ex) {
            // Message générique : ni le corps d'erreur du fournisseur ni la clé ne doivent atteindre l'utilisateur.
            throw new ModelUnavailableException("the language model provider is unavailable or refused the request", ex);
        }
        return toReply(response, request.maxOutputTokens());
    }

    Prompt toPrompt(ModelExchange.Request request) {
        List<Message> messages = new ArrayList<>();
        if (request.system() != null && !request.system().isBlank()) {
            messages.add(new SystemMessage(request.system()));
        }
        Map<String, String> toolNames = new HashMap<>();
        for (ModelExchange.Message m : request.messages()) {
            if (m.role() == ModelExchange.Message.Role.USER) {
                if (!m.toolResults().isEmpty()) {
                    List<ToolResponseMessage.ToolResponse> responses = new ArrayList<>();
                    for (ModelExchange.ToolResult r : m.toolResults()) {
                        // Spring AI ne porte pas d'indicateur d'erreur : on le met dans le contenu, lisible par le modèle.
                        String content = r.error() ? "ERROR: " + r.content() : r.content();
                        responses.add(new ToolResponseMessage.ToolResponse(r.toolCallId(), toolNames.getOrDefault(r.toolCallId(), "tool"), content));
                    }
                    messages.add(ToolResponseMessage.builder().responses(responses).build());
                }
                if (!m.text().isBlank() || m.toolResults().isEmpty()) {
                    messages.add(new UserMessage(m.text().isBlank() ? "(vide)" : m.text()));
                }
            } else {
                List<AssistantMessage.ToolCall> calls = new ArrayList<>();
                for (ModelExchange.ToolCall c : m.toolCalls()) {
                    toolNames.put(c.id(), c.name());
                    calls.add(new AssistantMessage.ToolCall(c.id(), "function", c.name(), c.argumentsJson().isBlank() ? "{}" : c.argumentsJson()));
                }
                // L'API refuse un bloc texte fait de blancs : sans appel d'outil on met un repère, avec appel d'outil on n'envoie aucun texte.
                String text = m.text().isBlank() ? (calls.isEmpty() ? "(vide)" : "") : m.text();
                messages.add(AssistantMessage.builder().content(text).toolCalls(calls).build());
            }
        }
        List<ToolCallback> callbacks = new ArrayList<>();
        for (ToolSpec spec : request.tools()) {
            callbacks.add(new DeclaredOnlyTool(spec, mapper));
        }
        // Le modèle Anthropic ignore toute autre implémentation d'options (modèle, plafond et outils compris) : options natives obligatoires.
        AnthropicChatOptions options = AnthropicChatOptions.builder()
                .model(model)
                .maxTokens(request.maxOutputTokens())
                .toolCallbacks(callbacks)
                .build();
        return new Prompt(messages, options);
    }

    ModelExchange.Reply toReply(ChatResponse response, long maxOutputTokens) {
        if (response == null) {
            throw new ModelUnavailableException("the language model provider returned no answer");
        }
        StringBuilder text = new StringBuilder();
        List<ModelExchange.ToolCall> calls = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Generation g : response.getResults()) {
            AssistantMessage out = g.getOutput();
            if (out.getText() != null) {
                text.append(out.getText());
            }
            for (AssistantMessage.ToolCall c : out.getToolCalls()) {
                // Un identifiant vide ou répété ferait refuser la requête suivante : on en garantit un, unique, par appel.
                String id = c.id() == null || c.id().isBlank() || !seen.add(c.id()) ? "toolu_local_" + calls.size() : c.id();
                seen.add(id);
                String args = c.arguments() == null || c.arguments().isBlank() ? "{}" : c.arguments();
                calls.add(new ModelExchange.ToolCall(id, c.name() == null ? "" : c.name(), args));
            }
        }
        // Sans mesure du fournisseur (Spring AI renvoie une réponse sans métadonnées quand le contenu est vide), on compte le pire cas
        // plutôt que zéro : les plafonds de jetons (D-ASSIST-COST) ne doivent pas pouvoir être contournés par une réponse vide.
        long in = 0;
        long outTokens = maxOutputTokens;
        if (response.getMetadata() != null && response.getMetadata().getUsage() != null
                && response.getMetadata().getUsage().getCompletionTokens() != null
                && response.getMetadata().getUsage().getCompletionTokens() > 0) {
            var usage = response.getMetadata().getUsage();
            in = usage.getPromptTokens() == null ? 0 : usage.getPromptTokens();
            outTokens = usage.getCompletionTokens() == null ? 0 : usage.getCompletionTokens();
        }
        return new ModelExchange.Reply(text.toString(), calls, in, outTokens);
    }

    /** Outil déclaré au modèle sans pouvoir être exécuté par Spring AI : l'exécution appartient à la boucle du domaine. */
    private record DeclaredOnlyTool(ToolSpec spec, ObjectMapper mapper) implements ToolCallback {
        @Override
        public ToolDefinition getToolDefinition() {
            return ToolDefinition.builder()
                    .name(spec.name())
                    .description(spec.description())
                    .inputSchema(mapper.writeValueAsString(spec.inputSchema()))
                    .build();
        }

        @Override
        public String call(String toolInput) {
            throw new UnsupportedOperationException("tools are executed by the assistant agent loop, never by the model client");
        }
    }
}
