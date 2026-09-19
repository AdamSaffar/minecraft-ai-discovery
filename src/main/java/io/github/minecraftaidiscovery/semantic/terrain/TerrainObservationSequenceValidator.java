package io.github.minecraftaidiscovery.semantic.terrain;

public final class TerrainObservationSequenceValidator {
    private boolean initialized;
    private long previousSequence;

    public void accept(TerrainObservationFrame frame) {
        if (frame == null) {
            throw new IllegalArgumentException("Terrain observation frame is missing");
        }
        long sequence = frame.getObservationSequence();
        // Java stores the uint64 sequence as raw long bits; ordering remains unsigned.
        if (initialized && Long.compareUnsigned(sequence, previousSequence) <= 0) {
            throw new IllegalArgumentException(
                "Terrain observation sequence must increase strictly"
            );
        }
        previousSequence = sequence;
        initialized = true;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public long getPreviousSequence() {
        if (!initialized) {
            throw new IllegalStateException("No terrain observation sequence was accepted");
        }
        return previousSequence;
    }
}
