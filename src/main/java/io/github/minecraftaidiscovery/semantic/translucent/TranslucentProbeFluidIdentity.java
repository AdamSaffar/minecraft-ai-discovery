package io.github.minecraftaidiscovery.semantic.translucent;

import net.minecraft.fluid.Fluid;
import net.minecraft.fluid.FluidState;
import net.minecraft.fluid.Fluids;

public final class TranslucentProbeFluidIdentity {
    public static final String WATER = "minecraft:water";
    public static final String LAVA = "minecraft:lava";

    private TranslucentProbeFluidIdentity() {
    }

    public static String requireCanonicalIdentifier(FluidState state) {
        if (state == null || state.isEmpty()) {
            throw new IllegalArgumentException("Rendered fluid state is missing");
        }
        Fluid fluid = state.getFluid();
        if (fluid.matchesType(Fluids.WATER)) {
            return WATER;
        }
        if (fluid.matchesType(Fluids.LAVA)) {
            return LAVA;
        }
        throw new IllegalStateException("Unsupported rendered fluid type in the pinned client");
    }

    static String classifyIdentity(
        Object fluid,
        Object water,
        Object flowingWater,
        Object lava,
        Object flowingLava
    ) {
        if (fluid == water || fluid == flowingWater) {
            return WATER;
        }
        if (fluid == lava || fluid == flowingLava) {
            return LAVA;
        }
        throw new IllegalArgumentException("Unknown fluid identity");
    }
}
