package io.github.minecraftaidiscovery.semantic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

public class SemanticRenderPassTest {
    @Test
    public void exposesEachSupportedLayerOnlyDuringItsReplay() {
        List<SemanticRenderLayer> observed = new ArrayList<>();

        for (SemanticRenderLayer layer : SemanticRenderLayer.supportedInOrder()) {
            SemanticRenderPass.run(layer, () -> {
                assertTrue(SemanticRenderPass.isActive());
                assertEquals(layer, SemanticRenderPass.requireLayer());
                observed.add(layer);
            });
            assertFalse(SemanticRenderPass.isActive());
        }

        assertEquals(SemanticRenderLayer.supportedInOrder(), observed);
    }

    @Test
    public void rejectsNestedOrMissingRenderPassStateAndCleansUpAfterFailure() {
        expectIllegalState(SemanticRenderPass::requireLayer, "No semantic render layer");
        expectIllegalArgument(
            () -> SemanticRenderPass.run(null, () -> {}),
            "layer is missing"
        );
        expectIllegalState(
            () -> SemanticRenderPass.run(
                SemanticRenderLayer.SOLID,
                () -> SemanticRenderPass.run(SemanticRenderLayer.CUTOUT, () -> {})
            ),
            "cannot be nested"
        );
        assertFalse(SemanticRenderPass.isActive());
    }

    private static void expectIllegalArgument(ThrowingRunnable runnable, String expectedMessage) {
        try {
            runnable.run();
            fail("Expected IllegalArgumentException");
        } catch (IllegalArgumentException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    private static void expectIllegalState(ThrowingRunnable runnable, String expectedMessage) {
        try {
            runnable.run();
            fail("Expected IllegalStateException");
        } catch (IllegalStateException exception) {
            assertTrue(exception.getMessage().contains(expectedMessage));
        }
    }

    private interface ThrowingRunnable {
        void run();
    }
}
