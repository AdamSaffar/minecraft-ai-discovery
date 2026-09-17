package io.github.minecraftaidiscovery.semantic.visibility;

import java.util.Locale;

final class BaseQualificationCandidate {
    enum Reducer {
        MINIMUM("minimum", 0) {
            @Override
            int reduce(int red, int green, int blue) {
                return Math.min(red, Math.min(green, blue));
            }
        },
        LUMINANCE("luminance", 1) {
            @Override
            int reduce(int red, int green, int blue) {
                return (54 * red + 183 * green + 19 * blue + 128) >> 8;
            }
        },
        MAXIMUM("maximum", 2) {
            @Override
            int reduce(int red, int green, int blue) {
                return Math.max(red, Math.max(green, blue));
            }
        };

        private final String metadataName;
        private final int tieBreakOrder;

        Reducer(String metadataName, int tieBreakOrder) {
            this.metadataName = metadataName;
            this.tieBreakOrder = tieBreakOrder;
        }

        abstract int reduce(int red, int green, int blue);

        String getMetadataName() {
            return metadataName;
        }

        int getTieBreakOrder() {
            return tieBreakOrder;
        }

        static Reducer fromMetadataName(String value) {
            for (Reducer reducer : values()) {
                if (reducer.metadataName.equals(value)) {
                    return reducer;
                }
            }
            throw new IllegalArgumentException("Unknown calibration channel reducer: " + value);
        }
    }

    private final Reducer reducer;
    private final int threshold;
    private final int minimumArea;

    BaseQualificationCandidate(Reducer reducer, int threshold, int minimumArea) {
        if (reducer == null || threshold < 0 || threshold > 255 || minimumArea <= 0) {
            throw new IllegalArgumentException("Base-qualification candidate is invalid");
        }
        this.reducer = reducer;
        this.threshold = threshold;
        this.minimumArea = minimumArea;
    }

    Reducer getReducer() {
        return reducer;
    }

    int getThreshold() {
        return threshold;
    }

    int getMinimumArea() {
        return minimumArea;
    }

    String getId() {
        return String.format(
            Locale.ROOT,
            "%s-t%03d-a%03d",
            reducer.getMetadataName(),
            threshold,
            minimumArea
        );
    }
}
