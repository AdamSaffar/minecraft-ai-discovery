package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.SemanticMinecraftRenderLayers;
import io.github.minecraftaidiscovery.semantic.SemanticRenderLayer;
import io.github.minecraftaidiscovery.semantic.SemanticVertexEncoding;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeConfiguration;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeFluidIdentity;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeMinecraftRenderLayers;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeVertexEncoding;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeVertexEncoding.Contribution;
import java.util.Random;
import net.minecraft.block.BlockState;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.registry.Registry;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(targets = "net.minecraft.client.render.chunk.ChunkBuilder$BuiltChunk$RebuildTask")
abstract class ChunkRebuildTaskMixin {
    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderBlock(Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumer;ZLjava/util/Random;)Z"
        )
    )
    private boolean semanticObservation$encodeRenderedBlock(
        BlockRenderManager renderer,
        BlockState state,
        BlockPos pos,
        BlockRenderView world,
        MatrixStack matrices,
        VertexConsumer vertices,
        boolean cull,
        Random random
    ) {
        RenderLayer minecraftLayer = RenderLayers.getBlockLayer(state);
        SemanticRenderLayer semanticLayer = SemanticMinecraftRenderLayers.classify(minecraftLayer);
        int previousClassId = semanticLayer != null
            ? SemanticVertexEncoding.beginBlock(Registry.BLOCK.getId(state.getBlock()).toString())
            : SemanticVertexEncoding.beginBackground();
        Contribution previousProbeContribution = null;
        if (TranslucentProbeConfiguration.isEnabled()) {
            previousProbeContribution = TranslucentProbeMinecraftRenderLayers.classify(minecraftLayer)
                != null
                ? TranslucentProbeVertexEncoding.beginBlock(
                    Registry.BLOCK.getId(state.getBlock()).toString()
                )
                : TranslucentProbeVertexEncoding.beginBackground();
        }
        try {
            return renderer.renderBlock(state, pos, world, matrices, vertices, cull, random);
        } finally {
            if (previousProbeContribution != null) {
                TranslucentProbeVertexEncoding.restore(previousProbeContribution);
            }
            SemanticVertexEncoding.restore(previousClassId);
        }
    }

    @Redirect(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/block/BlockRenderManager;renderFluid(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/fluid/FluidState;)Z"
        )
    )
    private boolean semanticObservation$encodeRenderedFluid(
        BlockRenderManager renderer,
        BlockPos pos,
        BlockRenderView world,
        VertexConsumer vertices,
        FluidState fluidState
    ) {
        // Fluid identity comes from the renderer call. A waterlogged block's model is encoded by the other wrapper.
        Contribution previousProbeContribution = TranslucentProbeConfiguration.isEnabled()
            ? TranslucentProbeVertexEncoding.beginFluid(
                TranslucentProbeFluidIdentity.requireCanonicalIdentifier(fluidState)
            )
            : null;
        try {
            return renderer.renderFluid(pos, world, vertices, fluidState);
        } finally {
            if (previousProbeContribution != null) {
                TranslucentProbeVertexEncoding.restore(previousProbeContribution);
            }
        }
    }
}
