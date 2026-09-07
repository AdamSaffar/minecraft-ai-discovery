package io.github.minecraftaidiscovery.semantic.translucent;

public final class TranslucentProbeConfiguration {
    public static final String ENABLED_PROPERTY = "semanticObservation.translucentProbe";

    // Keep this fixed for the process lifetime. Enabling it after chunks exist would mix VBOs with and without sidecars.
    private static final boolean ENABLED = parseEnabled(System.getProperty(ENABLED_PROPERTY));

    private TranslucentProbeConfiguration() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    static boolean parseEnabled(String value) {
        if (value == null || "false".equalsIgnoreCase(value)) {
            return false;
        }
        if ("true".equalsIgnoreCase(value)) {
            return true;
        }
        throw new IllegalArgumentException(
            "System property " + ENABLED_PROPERTY + " must be true or false"
        );
    }
}
