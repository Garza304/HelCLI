package ohi.andre.consolelauncher.managers.ask;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.PermissionDeniedException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.errors.UnauthorizedException;
import com.anthropic.models.beta.messages.BetaContentBlock;
import com.anthropic.models.beta.messages.BetaFallbacksParam;
import com.anthropic.models.beta.messages.BetaMessage;
import com.anthropic.models.beta.messages.BetaMessageParam;
import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.BetaStopReason;
import com.anthropic.models.beta.messages.MessageCreateParams;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * "ask": questions to Claude through Anthropic's Java SDK. Keeps a short conversation, so
 * follow-up questions work, until "ask -new" or 30 minutes without a question.
 *
 * Only loaded on Android 8 and later: the SDK's JSON library needs it.
 */
public class AskClaude {

    public static final String DEFAULT_MODEL = "claude-opus-5-5";

    static final String SYSTEM = "You're answering inside HelCLI, a command-line launcher on an Android phone. "
            + "Your reply is shown as plain text in a narrow terminal, about 44 characters wide, that can't display Markdown: "
            + "no headings, tables, bold or code fences. Use short paragraphs, and \"-\" for lists. "
            + "Keep answers brief unless the person asks for detail.";

    /** Models that accept server-side fallbacks ("default" mode), for when a request is declined. */
    static final String[] FALLBACK_MODELS = {"claude-opus-5-5", "claude-opus-5", "claude-fable-5-1", "claude-sonnet-5-5"};

    private static final int MAX_TURNS = 6;
    private static final long FORGET_AFTER = 30 * 60_000L;

    private static AnthropicClient client;
    private static String clientKey, clientWorkspace;

    private static final List<String> questions = new ArrayList<>();
    private static final List<BetaMessageParam> answers = new ArrayList<>();
    private static long lastAsked;

    private AskClaude() {}

    public static synchronized void forgetConversation() {
        questions.clear();
        answers.clear();
    }

    private static synchronized AnthropicClient client(String key, String workspace) {
        String ws = workspace == null ? "" : workspace;
        if (client == null || !key.equals(clientKey) || !ws.equals(clientWorkspace)) {
            AnthropicOkHttpClient.Builder b = AnthropicOkHttpClient.builder()
                    .apiKey(key)
                    .timeout(Duration.ofMinutes(2))
                    .maxRetries(2);
//            a key that isn't tied to one workspace has to say which one to use
            if (ws.length() > 0) b.putHeader("anthropic-workspace-id", ws);
            client = b.build();
            clientKey = key;
            clientWorkspace = ws;
        }
        return client;
    }

    /** What to tell the user when Claude refuses a key that isn't tied to a workspace. */
    static String workspaceHelp(String message) {
        if (message == null) return null;
        if (message.contains("not scoped to a workspace")) {
            return "Your key isn't tied to one workspace, so HelCLI has to say which workspace to use."
                    + " Copy the ID from platform.claude.com/settings/workspaces (it starts with wrkspc_) and run: ask -workspace wrkspc_..."
                    + " Or make a new key with one workspace selected: ask -link";
        }
        if (message.contains("valid workspace ID") || message.contains("Workspace `")) {
            return "Claude doesn't know that workspace. Check the ID at platform.claude.com/settings/workspaces and run ask -workspace again"
                    + " (ask -workspace off to stop sending one).";
        }
        return null;
    }

    static boolean supportsFallbacks(String model) {
        for (String m : FALLBACK_MODELS) if (m.equals(model)) return true;
        return false;
    }

    static BetaOutputConfig.Effort effort(String name) {
        switch (name == null ? "" : name.trim().toLowerCase(Locale.ROOT)) {
            case "medium": return BetaOutputConfig.Effort.MEDIUM;
            case "high": return BetaOutputConfig.Effort.HIGH;
            case "xhigh": return BetaOutputConfig.Effort.XHIGH;
            case "max": return BetaOutputConfig.Effort.MAX;
            default: return BetaOutputConfig.Effort.LOW;
        }
    }

    /** Builds the request: the conversation so far, then the new question. */
    static MessageCreateParams params(String model, String effort, List<String> pastQuestions,
                                      List<BetaMessageParam> pastAnswers, String question) {
        MessageCreateParams.Builder b = MessageCreateParams.builder()
                .model(model)
                .maxTokens(16000L)
                .system(SYSTEM)
                .outputConfig(BetaOutputConfig.builder().effort(effort(effort)).build());

        if (supportsFallbacks(model)) {
//            if Claude declines, Anthropic retries on the model it recommends for that kind of request
            b.addBeta("server-side-fallback-2026-07-01").fallbacks(BetaFallbacksParam.ofDefault());
        }

        for (int i = 0; i < pastQuestions.size(); i++) {
            b.addUserMessage(pastQuestions.get(i));
            b.addMessage(pastAnswers.get(i));
        }
        b.addUserMessage(question);
        return b.build();
    }

    /** Asks and returns the answer, or a short explanation of what went wrong. Call off the main thread. */
    public static String ask(String key, String workspace, String model, String effort, String question) {
        List<String> pastQ;
        List<BetaMessageParam> pastA;
        synchronized (AskClaude.class) {
            if (System.currentTimeMillis() - lastAsked > FORGET_AFTER) forgetConversation();
            lastAsked = System.currentTimeMillis();
            pastQ = new ArrayList<>(questions);
            pastA = new ArrayList<>(answers);
        }

        try {
            BetaMessage reply = client(key, workspace).beta().messages().create(params(model, effort, pastQ, pastA, question));

            if (reply.stopReason().isPresent() && reply.stopReason().get().equals(BetaStopReason.REFUSAL)) {
                String why = reply.stopDetails().flatMap(d -> d.explanation()).orElse("");
                return "Claude declined to answer that." + (why.length() > 0 ? " " + why : "");
            }

            StringBuilder text = new StringBuilder();
            for (BetaContentBlock block : reply.content()) {
                block.text().ifPresent(t -> text.append(t.text()));
            }
            String answer = text.toString().trim();
            if (reply.stopReason().isPresent() && reply.stopReason().get().equals(BetaStopReason.MAX_TOKENS)) {
                answer += "\n(cut off: the answer was too long)";
            }

            synchronized (AskClaude.class) {
//                keep the reply exactly as it came, so follow-ups replay it unchanged
                questions.add(question);
                answers.add(reply.toParam());
                while (questions.size() > MAX_TURNS) {
                    questions.remove(0);
                    answers.remove(0);
                }
            }
            return answer.length() > 0 ? answer : "(Claude didn't answer)";
        } catch (UnauthorizedException e) {
            return "Your Claude API key was rejected. Make a new one with: ask -link";
        } catch (PermissionDeniedException e) {
            return "Your key can't use " + model + ": " + e.getMessage();
        } catch (NotFoundException e) {
            String ws = workspaceHelp(e.getMessage());
            if (ws != null) return ws;
            return "Claude doesn't know the model \"" + model + "\". Change it with: config -set ask_model " + DEFAULT_MODEL;
        } catch (RateLimitException e) {
            return "Too many questions right now. Try again in a minute.";
        } catch (BadRequestException e) {
            String ws = workspaceHelp(e.getMessage());
            if (ws != null) return ws;
            return "Claude couldn't take that request: " + e.getMessage();
        } catch (AnthropicServiceException e) {
            return "Claude had a problem (" + e.statusCode() + "). Try again in a moment.";
        } catch (AnthropicIoException e) {
            return "Couldn't reach Claude. Check your connection.";
        } catch (Exception e) {
            return "ask: " + e.getMessage();
        }
    }
}
