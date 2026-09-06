package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.SemanticCapture;
import io.github.minecraftaidiscovery.semantic.SemanticCaptureRequests;
import io.github.minecraftaidiscovery.semantic.SemanticMinecraftRenderLayers;
import java.nio.file.Path;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.Matrix4f;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
abstract class WorldRendererMixin {
    private static final Logger SEMANTIC_OBSERVATION_LOGGER = LogManager.getLogger("SemanticObservationPoC");

    @Shadow
    private void renderLayer(RenderLayer layer, MatrixStack matrices, double cameraX, double cameraY, double cameraZ) {
        throw new AssertionError();
    }

    @Inject(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/WorldRenderer;renderLayer(Lnet/minecraft/client/render/RenderLayer;Lnet/minecraft/client/util/math/MatrixStack;DDD)V",
            ordinal = 2,
            shift = At.Shift.AFTER
        )
    )
    private void semanticObservation$captureAfterSupportedLayers(
        MatrixStack matrices,
        float tickDelta,
        long limitTime,
        boolean renderBlockOutline,
        Camera camera,
        GameRenderer gameRenderer,
        LightmapTextureManager lightmapTextureManager,
        Matrix4f projectionMatrix,
        CallbackInfo callback
    ) {
        if (!SemanticCaptureRequests.consume()) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d cameraPosition = camera.getPos();
        try {
            Path metadataPath = SemanticCapture.capture(
                client,
                layer -> renderLayer(
                    SemanticMinecraftRenderLayers.requireMinecraftLayer(layer),
                    matrices,
                    cameraPosition.x,
                    cameraPosition.y,
                    cameraPosition.z
                )
            );
            String relativePath = client.runDirectory.toPath().relativize(metadataPath).toString();
            SEMANTIC_OBSERVATION_LOGGER.info("Wrote semantic capture metadata to {}", metadataPath.toAbsolutePath());
            client.inGameHud.getChatHud().addMessage(new LiteralText("Semantic capture saved: " + relativePath));
        } catch (Exception exception) {
            SEMANTIC_OBSERVATION_LOGGER.error("Semantic capture failed", exception);
            client.inGameHud.getChatHud().addMessage(new LiteralText("Semantic capture failed; see latest.log"));
        }
    }
}
