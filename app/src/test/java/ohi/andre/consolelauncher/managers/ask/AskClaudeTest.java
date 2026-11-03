package ohi.andre.consolelauncher.managers.ask;

import com.anthropic.models.beta.messages.BetaOutputConfig;
import com.anthropic.models.beta.messages.MessageCreateParams;

import org.junit.Test;

import java.util.ArrayList;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class AskClaudeTest {

    @Test
    public void buildsTheRequest() {
        MessageCreateParams p = AskClaude.params("claude-opus-5-5", "low", new ArrayList<>(), new ArrayList<>(), "hi");
        System.out.println(p);
        assertEquals("claude-opus-5-5", p.model().asString());
        assertEquals(16000L, p.maxTokens());
        assertTrue(p.fallbacks().isPresent());
        assertTrue(p.fallbacks().get().isDefault());
        assertTrue(p.betas().isPresent());
        assertEquals("server-side-fallback-2026-07-01", p.betas().get().get(0).asString());
        assertEquals(BetaOutputConfig.Effort.LOW, p.outputConfig().get().effort().get());
        assertEquals(1, p.messages().size());
        assertTrue(p.system().isPresent());
    }

    @Test
    public void noFallbacksForModelsWithout() {
//        Haiku has no server-side fallback: sending it would do nothing (or 400 in the list form)
        MessageCreateParams p = AskClaude.params("claude-haiku-5-5", "medium", new ArrayList<>(), new ArrayList<>(), "hi");
        assertFalse(p.fallbacks().isPresent());
        assertFalse(p.betas().isPresent());
        assertEquals(BetaOutputConfig.Effort.MEDIUM, p.outputConfig().get().effort().get());
    }

    @Test
    public void effortNames() {
        assertEquals(BetaOutputConfig.Effort.LOW, AskClaude.effort("nonsense"));
        assertEquals(BetaOutputConfig.Effort.XHIGH, AskClaude.effort(" XHigh "));
    }
}
