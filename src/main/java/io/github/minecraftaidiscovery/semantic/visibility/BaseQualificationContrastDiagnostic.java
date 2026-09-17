package io.github.minecraftaidiscovery.semantic.visibility;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class BaseQualificationContrastDiagnostic {
    static final class Result {
        private final double boundaryMedian;
        private final double withinClassMedian;
        private final double ratio;

        private Result(double boundaryMedian, double withinClassMedian, double ratio) {
            this.boundaryMedian = boundaryMedian;
            this.withinClassMedian = withinClassMedian;
            this.ratio = ratio;
        }

        double getBoundaryMedian() {
            return boundaryMedian;
        }

        double getWithinClassMedian() {
            return withinClassMedian;
        }

        double getRatio() {
            return ratio;
        }
    }

    private BaseQualificationContrastDiagnostic() {
    }

    static Result calculate(
        BaseQualificationCapture capture,
        BaseQualificationTargetGeometry geometry,
        String pairCode
    ) {
        BaseQualificationTargetMask largeA = geometry.requireMask(
            pairCode,
            BaseQualificationTargetRegion.LARGE_A
        );
        BaseQualificationTargetMask largeB = geometry.requireMask(
            pairCode,
            BaseQualificationTargetRegion.LARGE_B
        );
        byte[] rgb = capture.getSameFrameRgb().copyRgb();
        List<Double> boundary = new ArrayList<>();
        List<Double> within = new ArrayList<>();
        int width = capture.getWidth();
        int height = capture.getHeight();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                if (x + 1 < width) {
                    addPair(pixel, pixel + 1, largeA, largeB, rgb, boundary, within);
                }
                if (y + 1 < height) {
                    addPair(pixel, pixel + width, largeA, largeB, rgb, boundary, within);
                }
            }
        }
        double boundaryMedian = median(boundary);
        double withinMedian = median(within);
        double ratio = withinMedian == 0.0
            ? (boundaryMedian == 0.0 ? 0.0 : Double.POSITIVE_INFINITY)
            : boundaryMedian / withinMedian;
        return new Result(boundaryMedian, withinMedian, ratio);
    }

    private static void addPair(
        int first,
        int second,
        BaseQualificationTargetMask largeA,
        BaseQualificationTargetMask largeB,
        byte[] rgb,
        List<Double> boundary,
        List<Double> within
    ) {
        boolean firstA = largeA.contains(first);
        boolean secondA = largeA.contains(second);
        boolean firstB = largeB.contains(first);
        boolean secondB = largeB.contains(second);
        if ((firstA && secondB) || (firstB && secondA)) {
            boundary.add(distance(rgb, first, second));
        } else if ((firstA && secondA) || (firstB && secondB)) {
            within.add(distance(rgb, first, second));
        }
    }

    private static double distance(byte[] rgb, int first, int second) {
        int firstOffset = first * 3;
        int secondOffset = second * 3;
        int red = Byte.toUnsignedInt(rgb[firstOffset])
            - Byte.toUnsignedInt(rgb[secondOffset]);
        int green = Byte.toUnsignedInt(rgb[firstOffset + 1])
            - Byte.toUnsignedInt(rgb[secondOffset + 1]);
        int blue = Byte.toUnsignedInt(rgb[firstOffset + 2])
            - Byte.toUnsignedInt(rgb[secondOffset + 2]);
        return Math.sqrt(red * red + green * green + blue * blue);
    }

    private static double median(List<Double> values) {
        if (values.isEmpty()) {
            return 0.0;
        }
        Collections.sort(values);
        int middle = values.size() / 2;
        if ((values.size() & 1) == 1) {
            return values.get(middle);
        }
        return (values.get(middle - 1) + values.get(middle)) / 2.0;
    }
}
