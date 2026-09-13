package io.github.minecraftaidiscovery.semantic.visibility;

public final class BaseInfluenceReasonEncoding {
    public static final int BACKGROUND = 0;
    public static final int SUPPORTED = 1;
    public static final int UNSUPPORTED_LIGHTING = 1 << 1;
    public static final int TRANSLUCENT_OVERFLOW = 1 << 2;
    public static final int FIRST_PERSON_RETENTION_UNSUPPORTED = 1 << 3;
    public static final int UNSUPPORTED_MASK = UNSUPPORTED_LIGHTING
        | TRANSLUCENT_OVERFLOW
        | FIRST_PERSON_RETENTION_UNSUPPORTED;
    public static final String ENCODING =
        "uint8 status/reason bits; 0 background, 1 supported, "
            + "2 unsupported lighting, 4 translucent overflow, "
            + "8 first-person retention unsupported";

    private BaseInfluenceReasonEncoding() {
    }

    public static boolean isSupported(int code) {
        requireCode(code);
        return code == SUPPORTED;
    }

    public static int requireCode(int code) {
        if (code == BACKGROUND || code == SUPPORTED) {
            return code;
        }
        if ((code & ~UNSUPPORTED_MASK) != 0 || (code & UNSUPPORTED_MASK) == 0) {
            throw new IllegalArgumentException("Unknown base-influence reason code: " + code);
        }
        return code;
    }
}
