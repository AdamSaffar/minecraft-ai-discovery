package io.github.minecraftaidiscovery.semantic.visibility;

import java.util.Locale;

public final class BaseInfluenceProbeSummary {
    private final String captureName;
    private final long backgroundPixels;
    private final long supportedPixels;
    private final long unsupportedLightingPixels;
    private final long overflowPixels;
    private final long firstPersonUnsupportedPixels;
    private final int[] minimumRgb;
    private final int[] maximumRgb;
    private final double[] meanRgb;

    private BaseInfluenceProbeSummary(
        String captureName,
        long backgroundPixels,
        long supportedPixels,
        long unsupportedLightingPixels,
        long overflowPixels,
        long firstPersonUnsupportedPixels,
        int[] minimumRgb,
        int[] maximumRgb,
        double[] meanRgb
    ) {
        this.captureName = captureName;
        this.backgroundPixels = backgroundPixels;
        this.supportedPixels = supportedPixels;
        this.unsupportedLightingPixels = unsupportedLightingPixels;
        this.overflowPixels = overflowPixels;
        this.firstPersonUnsupportedPixels = firstPersonUnsupportedPixels;
        this.minimumRgb = minimumRgb;
        this.maximumRgb = maximumRgb;
        this.meanRgb = meanRgb;
    }

    public static BaseInfluenceProbeSummary from(BaseInfluenceProbeCapture capture) {
        if (capture == null) {
            throw new IllegalArgumentException("Probe capture is missing");
        }
        byte[] reasons = capture.getComposition().copyReasons();
        byte[] rgb = capture.getComposition().copyRgb();
        long background = 0;
        long supported = 0;
        long lighting = 0;
        long overflow = 0;
        long firstPerson = 0;
        long[] sums = new long[3];
        int[] minimum = {255, 255, 255};
        int[] maximum = {0, 0, 0};

        for (int pixel = 0; pixel < reasons.length; pixel++) {
            int reason = Byte.toUnsignedInt(reasons[pixel]);
            if (reason == BaseInfluenceReasonEncoding.BACKGROUND) {
                background++;
                continue;
            }
            if (reason == BaseInfluenceReasonEncoding.SUPPORTED) {
                supported++;
                int rgbOffset = pixel * 3;
                for (int channel = 0; channel < 3; channel++) {
                    int value = Byte.toUnsignedInt(rgb[rgbOffset + channel]);
                    sums[channel] += value;
                    minimum[channel] = Math.min(minimum[channel], value);
                    maximum[channel] = Math.max(maximum[channel], value);
                }
                continue;
            }
            if ((reason & BaseInfluenceReasonEncoding.UNSUPPORTED_LIGHTING) != 0) {
                lighting++;
            }
            if ((reason & BaseInfluenceReasonEncoding.TRANSLUCENT_OVERFLOW) != 0) {
                overflow++;
            }
            if ((reason & BaseInfluenceReasonEncoding.FIRST_PERSON_RETENTION_UNSUPPORTED) != 0) {
                firstPerson++;
            }
        }

        double[] means = new double[3];
        if (supported == 0) {
            minimum = new int[] {-1, -1, -1};
            maximum = new int[] {-1, -1, -1};
            means = new double[] {Double.NaN, Double.NaN, Double.NaN};
        } else {
            for (int channel = 0; channel < 3; channel++) {
                means[channel] = sums[channel] / (double)supported;
            }
        }
        return new BaseInfluenceProbeSummary(
            capture.getCaptureName(),
            background,
            supported,
            lighting,
            overflow,
            firstPerson,
            minimum,
            maximum,
            means
        );
    }

    public String getCaptureName() {
        return captureName;
    }

    public long getBackgroundPixels() {
        return backgroundPixels;
    }

    public long getSupportedPixels() {
        return supportedPixels;
    }

    public long getUnsupportedLightingPixels() {
        return unsupportedLightingPixels;
    }

    public long getOverflowPixels() {
        return overflowPixels;
    }

    public long getFirstPersonUnsupportedPixels() {
        return firstPersonUnsupportedPixels;
    }

    public int getMinimum(int channel) {
        return minimumRgb[requireChannel(channel)];
    }

    public int getMaximum(int channel) {
        return maximumRgb[requireChannel(channel)];
    }

    public double getMean(int channel) {
        return meanRgb[requireChannel(channel)];
    }

    public String toTsv() {
        return String.format(
            Locale.ROOT,
            "%s\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%d\t%.6f\t%.6f\t%.6f",
            captureName,
            backgroundPixels,
            supportedPixels,
            unsupportedLightingPixels,
            overflowPixels,
            firstPersonUnsupportedPixels,
            minimumRgb[0],
            minimumRgb[1],
            minimumRgb[2],
            maximumRgb[0],
            maximumRgb[1],
            maximumRgb[2],
            meanRgb[0],
            meanRgb[1],
            meanRgb[2]
        );
    }

    private static int requireChannel(int channel) {
        if (channel < 0 || channel >= 3) {
            throw new IllegalArgumentException("RGB channel must be 0, 1, or 2");
        }
        return channel;
    }
}
