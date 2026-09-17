package io.github.minecraftaidiscovery.semantic.visibility;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class BaseQualificationTargetGeometry {
    private static final int MIN_WALL_ROW_PIXELS = 20;
    private static final int MIN_PAIR_ROW_PIXELS = 2;

    private final BaseQualificationCapture reference;
    private final int wallMinX;
    private final int wallMaxX;
    private final Map<String, EnumMap<BaseQualificationTargetRegion, BaseQualificationTargetMask>>
        masksByPair;

    private BaseQualificationTargetGeometry(
        BaseQualificationCapture reference,
        int wallMinX,
        int wallMaxX,
        Map<String, EnumMap<BaseQualificationTargetRegion, BaseQualificationTargetMask>> masksByPair
    ) {
        this.reference = reference;
        this.wallMinX = wallMinX;
        this.wallMaxX = wallMaxX;
        this.masksByPair = masksByPair;
    }

    static BaseQualificationTargetGeometry derive(
        BaseQualificationCapture reference,
        List<BaseQualificationStudyManifest.Pair> pairs
    ) {
        if (reference == null || pairs == null || pairs.isEmpty()) {
            throw new IllegalArgumentException("Calibration reference geometry is missing");
        }
        int width = reference.getWidth();
        int height = reference.getHeight();
        int[] classes = reference.copyBaseClassIds();
        int[] depthBits = reference.copyBaseDepthBits();
        boolean[] targetClasses = new boolean[0x10000];
        for (BaseQualificationStudyManifest.Pair pair : pairs) {
            targetClasses[pair.getClassA()] = true;
            targetClasses[pair.getClassB()] = true;
        }
        /*
         * The mosaic is a single world plane. Its dominant exact depth separates it from shells
         * and foreground blocks that may legitimately share one of the tested class identities.
         */
        int targetDepthBits = dominantTargetDepth(classes, depthBits, targetClasses);
        Bounds wallRows = longestDenseRows(
            classes,
            depthBits,
            targetDepthBits,
            width,
            height,
            0,
            width - 1,
            targetClasses,
            MIN_WALL_ROW_PIXELS
        );
        int minX = width;
        int maxX = -1;
        for (int y = wallRows.minimum; y <= wallRows.maximum; y++) {
            for (int x = 0; x < width; x++) {
                int pixel = y * width + x;
                if (depthBits[pixel] == targetDepthBits
                    && targetClasses[classes[pixel]]) {
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                }
            }
        }
        if (minX >= maxX) {
            throw new IllegalArgumentException("Calibration wall bounds could not be recovered");
        }
        int splitX = (minX + maxX + 1) / 2;
        Map<String, EnumMap<BaseQualificationTargetRegion, BaseQualificationTargetMask>> masks =
            new LinkedHashMap<>();
        for (BaseQualificationStudyManifest.Pair pair : pairs) {
            boolean[] pairClasses = new boolean[0x10000];
            pairClasses[pair.getClassA()] = true;
            pairClasses[pair.getClassB()] = true;
            Bounds row = longestDenseRows(
                classes,
                depthBits,
                targetDepthBits,
                width,
                height,
                minX,
                maxX,
                pairClasses,
                MIN_PAIR_ROW_PIXELS
            );
            int aLeft = count(
                classes, depthBits, targetDepthBits, width, minX, splitX - 1, row, pair.getClassA()
            );
            int aRight = count(
                classes, depthBits, targetDepthBits, width, splitX, maxX, row, pair.getClassA()
            );
            int bLeft = count(
                classes, depthBits, targetDepthBits, width, minX, splitX - 1, row, pair.getClassB()
            );
            int bRight = count(
                classes, depthBits, targetDepthBits, width, splitX, maxX, row, pair.getClassB()
            );
            boolean aLargeOnLeft = aLeft > aRight;
            boolean bLargeOnLeft = bLeft > bRight;
            if (aLargeOnLeft == bLargeOnLeft
                || Math.max(aLeft, aRight) <= Math.min(aLeft, aRight)
                || Math.max(bLeft, bRight) <= Math.min(bLeft, bRight)) {
                throw new IllegalArgumentException(
                    "Calibration pair does not contain opposite large and small regions: "
                        + pair.getCode()
                );
            }
            EnumMap<BaseQualificationTargetRegion, BaseQualificationTargetMask> pairMasks =
                new EnumMap<>(BaseQualificationTargetRegion.class);
            pairMasks.put(
                BaseQualificationTargetRegion.LARGE_A,
                mask(
                    classes, depthBits, targetDepthBits, width, height, minX, maxX, splitX,
                    row, pair.getClassA(), aLargeOnLeft
                )
            );
            pairMasks.put(
                BaseQualificationTargetRegion.SMALL_A,
                mask(
                    classes, depthBits, targetDepthBits, width, height, minX, maxX, splitX,
                    row, pair.getClassA(), !aLargeOnLeft
                )
            );
            pairMasks.put(
                BaseQualificationTargetRegion.LARGE_B,
                mask(
                    classes, depthBits, targetDepthBits, width, height, minX, maxX, splitX,
                    row, pair.getClassB(), bLargeOnLeft
                )
            );
            pairMasks.put(
                BaseQualificationTargetRegion.SMALL_B,
                mask(
                    classes, depthBits, targetDepthBits, width, height, minX, maxX, splitX,
                    row, pair.getClassB(), !bLargeOnLeft
                )
            );
            if (pairMasks.get(BaseQualificationTargetRegion.LARGE_A).getPixelCount()
                    <= pairMasks.get(BaseQualificationTargetRegion.SMALL_A).getPixelCount()
                || pairMasks.get(BaseQualificationTargetRegion.LARGE_B).getPixelCount()
                    <= pairMasks.get(BaseQualificationTargetRegion.SMALL_B).getPixelCount()) {
                throw new IllegalArgumentException(
                    "Calibration large region is not larger than its island: " + pair.getCode()
                );
            }
            masks.put(pair.getCode(), pairMasks);
        }
        return new BaseQualificationTargetGeometry(reference, minX, maxX, masks);
    }

    BaseQualificationTargetMask requireMask(
        String pairCode,
        BaseQualificationTargetRegion region
    ) {
        EnumMap<BaseQualificationTargetRegion, BaseQualificationTargetMask> pair =
            masksByPair.get(pairCode);
        if (pair == null || pair.get(region) == null) {
            throw new IllegalArgumentException("Unknown calibration target mask");
        }
        return pair.get(region);
    }

    int getWallMinX() {
        return wallMinX;
    }

    int getWallMaxX() {
        return wallMaxX;
    }

    private static BaseQualificationTargetMask mask(
        int[] classes,
        int[] depthBits,
        int targetDepthBits,
        int width,
        int height,
        int minX,
        int maxX,
        int splitX,
        Bounds row,
        int classId,
        boolean left
    ) {
        boolean[] selected = new boolean[classes.length];
        int sideMin = left ? minX : splitX;
        int sideMax = left ? splitX - 1 : maxX;
        for (int y = row.minimum; y <= row.maximum; y++) {
            for (int x = sideMin; x <= sideMax; x++) {
                int pixel = y * width + x;
                selected[pixel] = classes[pixel] == classId
                    && depthBits[pixel] == targetDepthBits;
            }
        }
        return new BaseQualificationTargetMask(width, height, classId, selected);
    }

    private static int count(
        int[] classes,
        int[] depthBits,
        int targetDepthBits,
        int width,
        int minX,
        int maxX,
        Bounds rows,
        int classId
    ) {
        int count = 0;
        for (int y = rows.minimum; y <= rows.maximum; y++) {
            for (int x = minX; x <= maxX; x++) {
                int pixel = y * width + x;
                if (classes[pixel] == classId && depthBits[pixel] == targetDepthBits) {
                    count++;
                }
            }
        }
        return count;
    }

    private static Bounds longestDenseRows(
        int[] classes,
        int[] depthBits,
        int targetDepthBits,
        int width,
        int height,
        int minX,
        int maxX,
        boolean[] selectedClasses,
        int minimumPixels
    ) {
        Bounds best = null;
        int bestPixels = -1;
        int start = -1;
        int accumulated = 0;
        for (int y = 0; y <= height; y++) {
            int rowPixels = 0;
            if (y < height) {
                for (int x = minX; x <= maxX; x++) {
                    int classId = classes[y * width + x];
                    if (depthBits[y * width + x] == targetDepthBits
                        && classId >= 0 && classId < selectedClasses.length
                        && selectedClasses[classId]) {
                        rowPixels++;
                    }
                }
            }
            if (y < height && rowPixels >= minimumPixels) {
                if (start < 0) {
                    start = y;
                    accumulated = 0;
                }
                accumulated += rowPixels;
            } else if (start >= 0) {
                Bounds candidate = new Bounds(start, y - 1);
                if (accumulated > bestPixels) {
                    best = candidate;
                    bestPixels = accumulated;
                }
                start = -1;
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("Calibration target row could not be recovered");
        }
        return best;
    }

    private static int dominantTargetDepth(
        int[] classes,
        int[] depthBits,
        boolean[] targetClasses
    ) {
        Map<Integer, Integer> counts = new HashMap<>();
        for (int pixel = 0; pixel < classes.length; pixel++) {
            int classId = classes[pixel];
            if (classId >= 0 && classId < targetClasses.length && targetClasses[classId]) {
                counts.merge(depthBits[pixel], 1, Integer::sum);
            }
        }
        int selectedDepth = 0;
        int selectedCount = 0;
        for (Map.Entry<Integer, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > selectedCount) {
                selectedDepth = entry.getKey();
                selectedCount = entry.getValue();
            }
        }
        if (selectedCount < MIN_WALL_ROW_PIXELS) {
            throw new IllegalArgumentException("Calibration target plane could not be recovered");
        }
        return selectedDepth;
    }

    private static final class Bounds {
        private final int minimum;
        private final int maximum;

        private Bounds(int minimum, int maximum) {
            this.minimum = minimum;
            this.maximum = maximum;
        }
    }
}
