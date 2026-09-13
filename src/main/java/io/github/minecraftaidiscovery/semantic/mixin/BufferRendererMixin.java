package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionCapture;
import java.nio.ByteBuffer;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferRenderer.class)
abstract class BufferRendererMixin {
    @Inject(
        method = "draw(Ljava/nio/ByteBuffer;ILnet/minecraft/client/render/VertexFormat;I)V",
        at = @At("HEAD")
    )
    private static void semanticObservation$mirrorCameraOverlayDraw(
        ByteBuffer buffer,
        int mode,
        VertexFormat vertexFormat,
        int count,
        CallbackInfo callback
    ) {
        // This overload receives the packet already selected and popped by the renderer.
        CameraOverlayRetentionCapture.mirror(buffer, mode, vertexFormat, count);
    }
}
