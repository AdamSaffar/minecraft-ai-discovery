package io.github.minecraftaidiscovery.semantic.lighting;

import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeConfiguration;

public final class LightingRetentionVertexEncoding {
    private static final ThreadLocal<Quad> ACTIVE = new ThreadLocal<>();

    private LightingRetentionVertexEncoding() {
    }

    public static void runQuad(float[] multipliers, Runnable renderer) {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            renderer.run();
            return;
        }
        runEnabledQuad(multipliers, renderer);
    }

    static void runEnabledQuad(float[] multipliers, Runnable renderer) {
        if (multipliers == null || multipliers.length != 4) {
            throw new IllegalArgumentException("Lighting-retention quad must have four multipliers");
        }

        Quad previous = ACTIVE.get();
        Quad current = new Quad(multipliers);
        ACTIVE.set(current);
        try {
            renderer.run();
            current.requireComplete();
        } finally {
            if (previous == null) {
                ACTIVE.remove();
            } else {
                ACTIVE.set(previous);
            }
        }
    }

    public static Sample consumeVertex() {
        Quad quad = ACTIVE.get();
        return quad == null ? Sample.invalid() : quad.consume();
    }

    static boolean isActive() {
        return ACTIVE.get() != null;
    }

    public static final class Sample {
        private static final Sample INVALID = new Sample(0, false);

        private final int multiplier;
        private final boolean valid;

        private Sample(int multiplier, boolean valid) {
            this.multiplier = multiplier;
            this.valid = valid;
        }

        static Sample valid(float multiplier) {
            return new Sample(quantizeMultiplier(multiplier), true);
        }

        static Sample invalid() {
            return INVALID;
        }

        public int getMultiplier() {
            return multiplier;
        }

        public boolean isValid() {
            return valid;
        }
    }

    private static final class Quad {
        private final float[] multipliers;
        private int nextVertex;

        private Quad(float[] multipliers) {
            this.multipliers = multipliers.clone();
            for (float multiplier : this.multipliers) {
                requireMultiplier(multiplier);
            }
        }

        private Sample consume() {
            if (nextVertex >= multipliers.length) {
                throw new IllegalStateException("Lighting-retention quad emitted more than four vertices");
            }
            return Sample.valid(multipliers[nextVertex++]);
        }

        private void requireComplete() {
            if (nextVertex != multipliers.length) {
                throw new IllegalStateException(
                    "Lighting-retention quad emitted " + nextVertex + " vertices instead of four"
                );
            }
        }
    }

    public static int quantizeMultiplier(float multiplier) {
        requireMultiplier(multiplier);
        // VertexConsumer uses the same truncating float-to-ubyte conversion for normal block colors.
        return (int)(multiplier * 255.0F);
    }

    private static void requireMultiplier(float multiplier) {
        if (!Float.isFinite(multiplier) || multiplier < 0.0F || multiplier > 1.0F) {
            throw new IllegalArgumentException(
                "Lighting-retention multiplier is outside [0,1]: " + multiplier
            );
        }
    }
}
