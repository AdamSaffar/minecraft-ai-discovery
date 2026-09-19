package io.github.minecraftaidiscovery.semantic.terrain;

public final class TerrainObservationFrame {
    private static final byte[] EMPTY_BYTES = new byte[0];
    private static final short[] EMPTY_SHORTS = new short[0];

    private final long observationSequence;
    private final FrameStatus frameStatus;
    private final TerrainObservationContract contract;
    private final byte[] occlusionState;
    private final byte[] baseState;
    private final short[] baseIdentity;
    private final short[] foregroundIdentity;
    private final byte[] foregroundOverflow;

    private TerrainObservationFrame(
        long observationSequence,
        FrameStatus frameStatus,
        TerrainObservationContract contract,
        byte[] occlusionState,
        byte[] baseState,
        short[] baseIdentity,
        short[] foregroundIdentity,
        byte[] foregroundOverflow
    ) {
        this.observationSequence = observationSequence;
        this.frameStatus = frameStatus;
        this.contract = contract;
        this.occlusionState = occlusionState.clone();
        this.baseState = baseState.clone();
        this.baseIdentity = baseIdentity.clone();
        this.foregroundIdentity = foregroundIdentity.clone();
        this.foregroundOverflow = foregroundOverflow.clone();
        validatePayload();
    }

    public static TerrainObservationFrame invalid(long observationSequence) {
        return new TerrainObservationFrame(
            observationSequence,
            FrameStatus.INVALID_FRAME,
            null,
            EMPTY_BYTES,
            EMPTY_BYTES,
            EMPTY_SHORTS,
            EMPTY_SHORTS,
            EMPTY_BYTES
        );
    }

    public static TerrainObservationFrame valid(
        TerrainObservationContract contract,
        long observationSequence,
        byte[] occlusionState,
        byte[] baseState,
        short[] baseIdentity,
        short[] foregroundIdentity,
        byte[] foregroundOverflow
    ) {
        if (contract == null) {
            throw new IllegalArgumentException("Terrain observation contract is missing");
        }
        requireArray("occlusion state", occlusionState);
        requireArray("base state", baseState);
        requireArray("base identity", baseIdentity);
        requireArray("foreground identity", foregroundIdentity);
        requireArray("foreground overflow", foregroundOverflow);
        return new TerrainObservationFrame(
            observationSequence,
            FrameStatus.VALID,
            contract,
            occlusionState,
            baseState,
            baseIdentity,
            foregroundIdentity,
            foregroundOverflow
        );
    }

    public long getObservationSequence() {
        return observationSequence;
    }

    public FrameStatus getFrameStatus() {
        return frameStatus;
    }

    public boolean isValid() {
        return frameStatus == FrameStatus.VALID;
    }

    public byte[] copyOcclusionState() {
        return occlusionState.clone();
    }

    public byte[] copyBaseState() {
        return baseState.clone();
    }

    public short[] copyBaseIdentity() {
        return baseIdentity.clone();
    }

    public short[] copyForegroundIdentity() {
        return foregroundIdentity.clone();
    }

    public byte[] copyForegroundOverflow() {
        return foregroundOverflow.clone();
    }

    private void validatePayload() {
        if (frameStatus == FrameStatus.INVALID_FRAME) {
            if (occlusionState.length != 0
                || baseState.length != 0
                || baseIdentity.length != 0
                || foregroundIdentity.length != 0
                || foregroundOverflow.length != 0) {
                throw new IllegalArgumentException(
                    "An invalid terrain observation must not contain a payload"
                );
            }
            return;
        }

        requireLength("occlusion state", occlusionState.length, TerrainObservationContract.PIXEL_COUNT);
        requireLength("base state", baseState.length, TerrainObservationContract.PIXEL_COUNT);
        requireLength("base identity", baseIdentity.length, TerrainObservationContract.PIXEL_COUNT);
        requireLength(
            "foreground identity",
            foregroundIdentity.length,
            TerrainObservationContract.expectedForegroundLength()
        );
        requireLength(
            "foreground overflow",
            foregroundOverflow.length,
            TerrainObservationContract.PIXEL_COUNT
        );

        for (int pixel = 0; pixel < TerrainObservationContract.PIXEL_COUNT; pixel++) {
            validatePixel(pixel);
        }
    }

    private void validatePixel(int pixel) {
        int occlusion = Byte.toUnsignedInt(occlusionState[pixel]);
        int base = Byte.toUnsignedInt(baseState[pixel]);
        int baseId = Short.toUnsignedInt(baseIdentity[pixel]);
        int overflow = Byte.toUnsignedInt(foregroundOverflow[pixel]);
        if (occlusion != TerrainObservationContract.OCCLUSION_CLEAR
            && occlusion != TerrainObservationContract.OCCLUSION_UNKNOWN) {
            throw invalidPixel(pixel, "occlusion state is unknown");
        }
        if (base < TerrainObservationContract.BASE_BACKGROUND
            || base > TerrainObservationContract.BASE_UNKNOWN) {
            throw invalidPixel(pixel, "base state is unknown");
        }
        if (overflow != 0 && overflow != 1) {
            throw invalidPixel(pixel, "foreground overflow is not boolean");
        }

        int populatedSlots = validateForegroundSlots(pixel);
        if (occlusion == TerrainObservationContract.OCCLUSION_UNKNOWN) {
            // Whole-pixel UNKNOWN must not preserve a hidden identity in another plane.
            if (base != TerrainObservationContract.BASE_UNKNOWN
                || baseId != 0
                || populatedSlots != 0
                || overflow != 0) {
                throw invalidPixel(pixel, "occlusion UNKNOWN did not clear terrain data");
            }
            return;
        }

        if (base == TerrainObservationContract.BASE_IDENTITY) {
            if (baseId == 0) {
                throw invalidPixel(pixel, "base identity is missing");
            }
            contract.requireManifestIdentity(baseId);
        } else if (baseId != 0) {
            throw invalidPixel(pixel, "base identity is present without IDENTITY state");
        }

        if (overflow == 1) {
            if (populatedSlots != TerrainObservationContract.FOREGROUND_CAPACITY) {
                throw invalidPixel(pixel, "overflow requires every foreground slot");
            }
            if (base != TerrainObservationContract.BASE_UNKNOWN) {
                // An omitted intervening layer makes the terminal base path incomplete.
                throw invalidPixel(pixel, "overflow requires an unknown terminal base");
            }
        }
    }

    private int validateForegroundSlots(int pixel) {
        int offset = pixel * TerrainObservationContract.FOREGROUND_CAPACITY;
        int populated = 0;
        boolean emptySeen = false;
        for (int slot = 0; slot < TerrainObservationContract.FOREGROUND_CAPACITY; slot++) {
            int identity = Short.toUnsignedInt(foregroundIdentity[offset + slot]);
            if (identity == 0) {
                emptySeen = true;
            } else {
                if (emptySeen) {
                    throw invalidPixel(pixel, "foreground identities contain a gap");
                }
                contract.requireManifestIdentity(identity);
                populated++;
            }
        }
        return populated;
    }

    private static IllegalArgumentException invalidPixel(int pixel, String detail) {
        return new IllegalArgumentException(
            "Terrain observation pixel " + pixel + " is invalid: " + detail
        );
    }

    private static void requireLength(String name, int actual, int expected) {
        if (actual != expected) {
            throw new IllegalArgumentException(
                "Terrain observation " + name + " length must be " + expected
                    + " but was " + actual
            );
        }
    }

    private static void requireArray(String name, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Terrain observation " + name + " is missing");
        }
    }

    public enum FrameStatus {
        VALID(0),
        INVALID_FRAME(1);

        private final int code;

        FrameStatus(int code) {
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }
}
