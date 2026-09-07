package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TranslucentProbeConfigurationTest {
    @Test
    public void disabledIsTheDefault() {
        assertFalse(TranslucentProbeConfiguration.parseEnabled(null));
        assertFalse(TranslucentProbeConfiguration.parseEnabled("false"));
    }

    @Test
    public void explicitTrueEnablesTheProbe() {
        assertTrue(TranslucentProbeConfiguration.parseEnabled("true"));
        assertTrue(TranslucentProbeConfiguration.parseEnabled("TRUE"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsAmbiguousConfiguration() {
        TranslucentProbeConfiguration.parseEnabled("yes");
    }
}
