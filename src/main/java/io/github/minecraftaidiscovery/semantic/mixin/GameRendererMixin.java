package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.overlay.CameraOverlayRetentionCapture;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
abstract class GameRendererMixin {
    private static final Logger SEMANTIC_OBSERVATION_LOGGER =
        LogManager.getLogger("SemanticObservationPoC");

    @Inject(method = "renderHand", at = @At("RETURN"))
    private void semanticObservation$completeProbeAfterCameraOverlays(
        MatrixStack matrices,
        Camera camera,
        float tickDelta,
        CallbackInfo callback
    ) {
        semanticObservation$completePendingProbe();
    }

    @Inject(method = "renderWorld", at = @At("RETURN"))
    private void semanticObservation$completeProbeWithoutHandPass(
        float tickDelta,
        long limitTime,
        MatrixStack matrices,
        CallbackInfo callback
    ) {
        // renderHand can be disabled independently; this closes that no-overlay boundary.
        semanticObservation$completePendingProbe();
    }

    private static void semanticObservation$completePendingProbe() {
        MinecraftClient client = MinecraftClient.getInstance();
        try {
            Path metadataPath = CameraOverlayRetentionCapture.completePending();
            if (metadataPath == null) {
                return;
            }
            String relativePath = client.runDirectory.toPath().relativize(metadataPath).toString();
            SEMANTIC_OBSERVATION_LOGGER.info(
                "Wrote researcher-only translucent probe metadata to {}",
                metadataPath.toAbsolutePath()
            );
            client.inGameHud.getChatHud().addMessage(
                new LiteralText("Translucent probe saved: " + relativePath)
            );
        } catch (Exception exception) {
            SEMANTIC_OBSERVATION_LOGGER.error("Researcher translucent probe failed", exception);
            client.inGameHud.getChatHud().addMessage(
                new LiteralText("Translucent probe failed; see latest.log")
            );
        }
    }
}
