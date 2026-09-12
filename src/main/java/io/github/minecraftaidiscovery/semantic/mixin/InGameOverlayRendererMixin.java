package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayKind;
import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionCapture;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameOverlayRenderer.class)
abstract class InGameOverlayRendererMixin {
    @Inject(method = "renderOverlays", at = @At("HEAD"))
    private static void semanticObservation$beginCameraOverlayStage(
        MinecraftClient client,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.beginOverlayStage(client);
    }

    @Inject(method = "renderOverlays", at = @At("RETURN"))
    private static void semanticObservation$endCameraOverlayStage(
        MinecraftClient client,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.endOverlayStage();
    }

    @Inject(method = "renderInWallOverlay", at = @At("HEAD"))
    private static void semanticObservation$beginInsideBlockOverlay(
        MinecraftClient client,
        Sprite sprite,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.begin(CameraOverlayKind.INSIDE_BLOCK);
    }

    @Inject(method = "renderInWallOverlay", at = @At("RETURN"))
    private static void semanticObservation$endInsideBlockOverlay(
        MinecraftClient client,
        Sprite sprite,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.end(CameraOverlayKind.INSIDE_BLOCK);
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("HEAD"))
    private static void semanticObservation$beginUnderwaterOverlay(
        MinecraftClient client,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.begin(CameraOverlayKind.UNDERWATER);
    }

    @Inject(method = "renderUnderwaterOverlay", at = @At("RETURN"))
    private static void semanticObservation$endUnderwaterOverlay(
        MinecraftClient client,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.end(CameraOverlayKind.UNDERWATER);
    }

    @Inject(method = "renderFireOverlay", at = @At("HEAD"))
    private static void semanticObservation$beginFireOverlay(
        MinecraftClient client,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.begin(CameraOverlayKind.FIRE);
    }

    @Inject(method = "renderFireOverlay", at = @At("RETURN"))
    private static void semanticObservation$endFireOverlay(
        MinecraftClient client,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        CameraOverlayRetentionCapture.end(CameraOverlayKind.FIRE);
    }
}
