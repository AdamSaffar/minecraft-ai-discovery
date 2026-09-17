package io.github.minecraftaidiscovery.semantic.visibility;

public enum BaseQualificationTargetRegion {
    LARGE_A(0, true, true),
    LARGE_B(1, false, true),
    SMALL_A(2, true, false),
    SMALL_B(3, false, false);

    private final int scheduleIndex;
    private final boolean identityA;
    private final boolean large;

    BaseQualificationTargetRegion(int scheduleIndex, boolean identityA, boolean large) {
        this.scheduleIndex = scheduleIndex;
        this.identityA = identityA;
        this.large = large;
    }

    public int getScheduleIndex() {
        return scheduleIndex;
    }

    public boolean isIdentityA() {
        return identityA;
    }

    public boolean isLarge() {
        return large;
    }

    public static BaseQualificationTargetRegion fromScheduleIndex(int index) {
        int normalized = Math.floorMod(index, 4);
        for (BaseQualificationTargetRegion region : values()) {
            if (region.scheduleIndex == normalized) {
                return region;
            }
        }
        throw new IllegalStateException("Missing calibration target region");
    }
}
