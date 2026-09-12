package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionVertexEncoding;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.block.BlockModelRenderer;
import net.minecraft.client.render.model.BakedQuad;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(BlockModelRenderer.class)
abstract class BlockModelRendererMixin {
    @Redirect(
        method = "renderQuad(Lnet/minecraft/world/BlockRenderView;Lnet/minecraft/block/BlockState;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/client/render/VertexConsumer;Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;FFFFIIIII)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/VertexConsumer;quad(Lnet/minecraft/client/util/math/MatrixStack$Entry;Lnet/minecraft/client/render/model/BakedQuad;[FFFF[IIZ)V"
        )
    )
    private void semanticObservation$captureRendererLighting(
        VertexConsumer vertices,
        MatrixStack.Entry matrixEntry,
        BakedQuad quad,
        float[] multipliers,
        float red,
        float green,
        float blue,
        int[] lightCoordinates,
        int overlay,
        boolean useQuadColorData
    ) {
        // Capture the renderer's multiplier before VertexConsumer folds biome or model tint into RGB.
        LightingRetentionVertexEncoding.runQuad(
            multipliers,
            () -> vertices.quad(
                matrixEntry,
                quad,
                multipliers,
                red,
                green,
                blue,
                lightCoordinates,
                overlay,
                useQuadColorData
            )
        );
    }
}
