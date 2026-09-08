package io.github.minecraftaidiscovery.semantic.translucent;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class TranslucentProbeCandidates {
    // Probe storage bound only. It is not an agent-facing K or a recommended production value.
    public static final int TEMPORARY_STORAGE_CAPACITY = 4;
    public static final int BYTES_PER_SLOT = 8;
    public static final String FRONT_TO_BACK_ENCODING =
        "4 slots/pixel: uint16le class, uint8 source, uint8 sampled alpha, float32le window depth";
    public static final String CONTRIBUTION_RANKED_ENCODING =
        "4 slots/pixel: uint16le class, uint8 source, uint8 sampled alpha, float32le effective alpha";

    private final int width;
    private final int height;
    private final byte[] frontToBack;
    private final byte[] contributionRanked;
    private final byte[] overflow;
    private final long overflowPixelCount;

    private TranslucentProbeCandidates(
        int width,
        int height,
        byte[] frontToBack,
        byte[] contributionRanked,
        byte[] overflow,
        long overflowPixelCount
    ) {
        this.width = width;
        this.height = height;
        this.frontToBack = frontToBack;
        this.contributionRanked = contributionRanked;
        this.overflow = overflow;
        this.overflowPixelCount = overflowPixelCount;
    }

    public static TranslucentProbeCandidates generate(
        List<TranslucentProbeLayer> storedLayers,
        TranslucentProbeLayer overflowLayer
    ) {
        if (storedLayers == null
            || storedLayers.size() != TEMPORARY_STORAGE_CAPACITY
            || overflowLayer == null) {
            throw new IllegalArgumentException("Translucent probe candidate layers are incomplete");
        }
        int width = overflowLayer.getWidth();
        int height = overflowLayer.getHeight();
        int pixels = TranslucentProbeBufferEncoding.requireDimensions(width, height);
        for (TranslucentProbeLayer layer : storedLayers) {
            if (layer == null || layer.getWidth() != width || layer.getHeight() != height) {
                throw new IllegalArgumentException("Translucent probe candidate layer dimensions differ");
            }
        }

        int bytesPerPixel = TEMPORARY_STORAGE_CAPACITY * BYTES_PER_SLOT;
        ByteBuffer frontToBack = ByteBuffer.allocate(Math.multiplyExact(pixels, bytesPerPixel))
            .order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer ranked = ByteBuffer.allocate(Math.multiplyExact(pixels, bytesPerPixel))
            .order(ByteOrder.LITTLE_ENDIAN);
        byte[] overflow = new byte[pixels];
        long overflowPixelCount = 0;

        for (int pixel = 0; pixel < pixels; pixel++) {
            List<Candidate> candidates = new ArrayList<>(TEMPORARY_STORAGE_CAPACITY);
            float remaining = 1.0F;
            float previousDepth = -1.0F;
            boolean emptySeen = false;
            for (int slot = 0; slot < TEMPORARY_STORAGE_CAPACITY; slot++) {
                TranslucentProbeLayer layer = storedLayers.get(slot);
                int classId = layer.getClassId(pixel);
                if (classId == 0) {
                    emptySeen = true;
                    putEmpty(frontToBack);
                    continue;
                }
                if (emptySeen || layer.getDepth(pixel) + 0.000001F < previousDepth) {
                    throw new IllegalArgumentException("Translucent probe peel ordering is inconsistent");
                }
                float sourceAlpha = layer.getSampledAlpha(pixel) / 255.0F;
                // Alpha-derived weight is a compositing coefficient, not a perceptual visibility score.
                float effectiveAlpha = sourceAlpha * remaining;
                remaining *= 1.0F - sourceAlpha;
                Candidate candidate = new Candidate(
                    classId,
                    layer.getSourceCode(pixel),
                    layer.getSampledAlpha(pixel),
                    layer.getDepth(pixel),
                    effectiveAlpha,
                    slot
                );
                candidates.add(candidate);
                putFrontToBack(frontToBack, candidate);
                previousDepth = layer.getDepth(pixel);
            }

            List<Candidate> rankedCandidates = new ArrayList<>(candidates);
            rankedCandidates.sort(
                Comparator.comparingDouble(Candidate::getEffectiveAlpha)
                    .reversed()
                    .thenComparingInt(Candidate::getFrontIndex)
            );
            for (Candidate candidate : rankedCandidates) {
                putRanked(ranked, candidate);
            }
            for (int slot = rankedCandidates.size(); slot < TEMPORARY_STORAGE_CAPACITY; slot++) {
                putEmpty(ranked);
            }

            if (overflowLayer.getClassId(pixel) != 0) {
                if (candidates.size() != TEMPORARY_STORAGE_CAPACITY) {
                    throw new IllegalArgumentException("Overflow appeared after an empty candidate slot");
                }
                overflow[pixel] = 1;
                overflowPixelCount++;
            }
        }

        return new TranslucentProbeCandidates(
            width,
            height,
            frontToBack.array(),
            ranked.array(),
            overflow,
            overflowPixelCount
        );
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    public byte[] getFrontToBack() {
        return frontToBack.clone();
    }

    public byte[] getContributionRanked() {
        return contributionRanked.clone();
    }

    public byte[] getOverflow() {
        return overflow.clone();
    }

    public long getOverflowPixelCount() {
        return overflowPixelCount;
    }

    public static int expectedCandidateBufferLength(int width, int height) {
        int pixels = TranslucentProbeBufferEncoding.requireDimensions(width, height);
        return Math.multiplyExact(
            pixels,
            Math.multiplyExact(TEMPORARY_STORAGE_CAPACITY, BYTES_PER_SLOT)
        );
    }

    private static void putFrontToBack(ByteBuffer target, Candidate candidate) {
        target.putShort((short)candidate.classId);
        target.put((byte)candidate.sourceCode);
        target.put((byte)candidate.sampledAlpha);
        target.putFloat(candidate.depth);
    }

    private static void putRanked(ByteBuffer target, Candidate candidate) {
        target.putShort((short)candidate.classId);
        target.put((byte)candidate.sourceCode);
        target.put((byte)candidate.sampledAlpha);
        target.putFloat(candidate.effectiveAlpha);
    }

    private static void putEmpty(ByteBuffer target) {
        target.putLong(0L);
    }

    private static final class Candidate {
        private final int classId;
        private final int sourceCode;
        private final int sampledAlpha;
        private final float depth;
        private final float effectiveAlpha;
        private final int frontIndex;

        private Candidate(
            int classId,
            int sourceCode,
            int sampledAlpha,
            float depth,
            float effectiveAlpha,
            int frontIndex
        ) {
            this.classId = classId;
            this.sourceCode = sourceCode;
            this.sampledAlpha = sampledAlpha;
            this.depth = depth;
            this.effectiveAlpha = effectiveAlpha;
            this.frontIndex = frontIndex;
        }

        private float getEffectiveAlpha() {
            return effectiveAlpha;
        }

        private int getFrontIndex() {
            return frontIndex;
        }
    }
}
