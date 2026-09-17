package io.github.minecraftaidiscovery.semantic.visibility;

final class BaseQualificationTargetMask {
    private static final int TRIAL_CROP_SIZE = 32;

    private final int width;
    private final int height;
    private final int classId;
    private final boolean[] pixels;
    private final int centerX;
    private final int centerY;
    private final int pixelCount;

    BaseQualificationTargetMask(
        int width,
        int height,
        int classId,
        boolean[] pixels
    ) {
        if (width <= 0 || height <= 0 || classId <= 0
            || pixels == null || pixels.length != width * height) {
            throw new IllegalArgumentException("Calibration target mask is invalid");
        }
        long sumX = 0;
        long sumY = 0;
        int count = 0;
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            if (pixels[pixel]) {
                sumX += pixel % width;
                sumY += pixel / width;
                count++;
            }
        }
        if (count == 0) {
            throw new IllegalArgumentException("Calibration target mask is empty");
        }
        this.width = width;
        this.height = height;
        this.classId = classId;
        this.pixels = pixels.clone();
        int centroidX = (int)((sumX + count / 2L) / count);
        int centroidY = (int)((sumY + count / 2L) / count);
        int[] cropCenter = bestCropCenter(
            width,
            height,
            pixels,
            centroidX,
            centroidY
        );
        this.centerX = cropCenter[0];
        this.centerY = cropCenter[1];
        this.pixelCount = count;
    }

    int getClassId() {
        return classId;
    }

    int getCenterX() {
        return centerX;
    }

    int getCenterY() {
        return centerY;
    }

    int getPixelCount() {
        return pixelCount;
    }

    boolean contains(int pixel) {
        return pixels[pixel];
    }

    boolean[] copyPixels() {
        return pixels.clone();
    }

    int getWidth() {
        return width;
    }

    int getHeight() {
        return height;
    }

    /*
     * Re-centering every target removes its absolute screen location. For large regions, choosing
     * the densest fixed-size crop also keeps swapped islands out of the swatch when the projected
     * footprint provides enough room, preventing mosaic layout from becoming an identity cue.
     */
    private static int[] bestCropCenter(
        int width,
        int height,
        boolean[] pixels,
        int centroidX,
        int centroidY
    ) {
        int stride = width + 1;
        int[] integral = new int[Math.multiplyExact(stride, height + 1)];
        for (int y = 0; y < height; y++) {
            int rowSum = 0;
            for (int x = 0; x < width; x++) {
                if (pixels[y * width + x]) {
                    rowSum++;
                }
                integral[(y + 1) * stride + x + 1] = integral[y * stride + x + 1]
                    + rowSum;
            }
        }
        int half = TRIAL_CROP_SIZE / 2;
        int selectedX = centroidX;
        int selectedY = centroidY;
        int selectedCoverage = -1;
        long selectedDistance = Long.MAX_VALUE;
        for (int pixel = 0; pixel < pixels.length; pixel++) {
            if (!pixels[pixel]) {
                continue;
            }
            int x = pixel % width;
            int y = pixel / width;
            int minX = Math.max(0, x - half);
            int minY = Math.max(0, y - half);
            int maxX = Math.min(width, minX + TRIAL_CROP_SIZE);
            int maxY = Math.min(height, minY + TRIAL_CROP_SIZE);
            minX = Math.max(0, maxX - TRIAL_CROP_SIZE);
            minY = Math.max(0, maxY - TRIAL_CROP_SIZE);
            int coverage = integral[maxY * stride + maxX]
                - integral[minY * stride + maxX]
                - integral[maxY * stride + minX]
                + integral[minY * stride + minX];
            long deltaX = x - (long)centroidX;
            long deltaY = y - (long)centroidY;
            long distance = deltaX * deltaX + deltaY * deltaY;
            if (coverage > selectedCoverage
                || coverage == selectedCoverage && distance < selectedDistance) {
                selectedX = x;
                selectedY = y;
                selectedCoverage = coverage;
                selectedDistance = distance;
            }
        }
        return new int[] {selectedX, selectedY};
    }
}
