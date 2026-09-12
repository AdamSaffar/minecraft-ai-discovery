package io.github.minecraftaidiscovery.semantic.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import io.github.minecraftaidiscovery.semantic.SemanticBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.SemanticGpuTransport;
import io.github.minecraftaidiscovery.semantic.SemanticRenderPass;
import io.github.minecraftaidiscovery.semantic.SemanticVertexData;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifestRuntime;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionGpuTransport;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionRenderPass;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionVertexData;
import io.github.minecraftaidiscovery.semantic.translucent.FogRetentionGpuTransport;
import io.github.minecraftaidiscovery.semantic.translucent.FogRetentionRenderPass;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeConfiguration;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeGpuTransport;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeRenderPass;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeVertexData;
import java.nio.ByteBuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.math.Matrix4f;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(VertexBuffer.class)
abstract class VertexBufferMixin {
    private static final int ARRAY_BUFFER = 34962;
    private static final int STATIC_DRAW = 35044;

    @Shadow
    @Final
    private VertexFormat format;

    @Shadow
    private int vertexCount;

    @Unique
    private int semanticObservation$bufferId = -1;

    @Unique
    private int semanticObservation$vertexCount;

    @Unique
    private byte[] semanticObservation$pendingVertexData;

    @Unique
    private String semanticObservation$manifestFingerprint;

    @Unique
    private boolean semanticObservation$backgroundOnly;

    @Unique
    private int semanticObservation$translucentProbeBufferId = -1;

    @Unique
    private byte[] semanticObservation$pendingTranslucentProbeVertexData;

    @Unique
    private boolean semanticObservation$translucentProbePresent;

    @Unique
    private boolean semanticObservation$translucentProbeBackgroundOnly;

    @Unique
    private String semanticObservation$translucentProbeManifestFingerprint;

    @Unique
    private int semanticObservation$lightingRetentionBufferId = -1;

    @Unique
    private byte[] semanticObservation$pendingLightingRetentionVertexData;

    @Unique
    private boolean semanticObservation$lightingRetentionPresent;

    @Unique
    private boolean semanticObservation$lightingRetentionInvalidOnly;

    @Redirect(
        method = "uploadInternal",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/render/BufferBuilder;popData()Lcom/mojang/datafixers/util/Pair;"
        )
    )
    private Pair<BufferBuilder.DrawArrayParameters, ByteBuffer> semanticObservation$captureVertexData(
        BufferBuilder builder
    ) {
        Pair<BufferBuilder.DrawArrayParameters, ByteBuffer> data = builder.popData();
        semanticObservation$pendingVertexData = ((SemanticBufferBuilderExtension)builder)
            .semanticObservation$takeVertexData(data.getFirst().getCount());
        semanticObservation$pendingTranslucentProbeVertexData =
            ((TranslucentProbeBufferBuilderExtension)builder)
                .semanticObservation$takeTranslucentProbeVertexData(data.getFirst().getCount());
        semanticObservation$pendingLightingRetentionVertexData =
            TranslucentProbeConfiguration.isEnabled()
                ? ((LightingRetentionBufferBuilderExtension)builder)
                    .semanticObservation$takeLightingRetentionVertexData(data.getFirst().getCount())
                : null;
        return data;
    }

    @Inject(method = "uploadInternal", at = @At("TAIL"))
    private void semanticObservation$uploadSidecar(BufferBuilder builder, CallbackInfo callback) {
        byte[] vertexData = semanticObservation$pendingVertexData;
        semanticObservation$pendingVertexData = null;
        byte[] translucentProbeVertexData = semanticObservation$pendingTranslucentProbeVertexData;
        semanticObservation$pendingTranslucentProbeVertexData = null;
        byte[] lightingRetentionVertexData = semanticObservation$pendingLightingRetentionVertexData;
        semanticObservation$pendingLightingRetentionVertexData = null;
        if (!format.equals(VertexFormats.POSITION_COLOR_TEXTURE_LIGHT_NORMAL)) {
            return;
        }
        if (vertexData == null || vertexData.length != vertexCount * SemanticVertexData.BYTES_PER_VERTEX) {
            throw new IllegalStateException("Semantic vertex sidecar does not match the uploaded vertex buffer");
        }
        SemanticVertexData.requireConstantQuadClassIds(vertexData);
        semanticObservation$backgroundOnly = SemanticVertexData.isBackgroundOnly(vertexData);

        if (!semanticObservation$backgroundOnly) {
            if (semanticObservation$bufferId < 0) {
                RenderSystem.glGenBuffers(id -> semanticObservation$bufferId = id);
            }
            ByteBuffer directData = ByteBuffer.allocateDirect(vertexData.length);
            directData.put(vertexData).flip();
            RenderSystem.glBindBuffer(ARRAY_BUFFER, () -> semanticObservation$bufferId);
            RenderSystem.glBufferData(ARRAY_BUFFER, directData, STATIC_DRAW);
            VertexBuffer.unbind();
        }

        semanticObservation$vertexCount = vertexCount;
        semanticObservation$manifestFingerprint = BlockClassManifestRuntime.require()
            .getFingerprintSha256();

        if (TranslucentProbeConfiguration.isEnabled()) {
            if (translucentProbeVertexData == null
                || translucentProbeVertexData.length
                    != vertexCount * TranslucentProbeVertexData.BYTES_PER_VERTEX) {
                throw new IllegalStateException(
                    "Translucent probe sidecar does not match the uploaded vertex buffer"
                );
            }
            TranslucentProbeVertexData.requireConstantQuadContributions(translucentProbeVertexData);
            semanticObservation$translucentProbeBackgroundOnly =
                TranslucentProbeVertexData.isBackgroundOnly(translucentProbeVertexData);
            if (!semanticObservation$translucentProbeBackgroundOnly) {
                if (semanticObservation$translucentProbeBufferId < 0) {
                    RenderSystem.glGenBuffers(id -> semanticObservation$translucentProbeBufferId = id);
                }
                ByteBuffer directProbeData = ByteBuffer.allocateDirect(translucentProbeVertexData.length);
                directProbeData.put(translucentProbeVertexData).flip();
                RenderSystem.glBindBuffer(
                    ARRAY_BUFFER,
                    () -> semanticObservation$translucentProbeBufferId
                );
                RenderSystem.glBufferData(ARRAY_BUFFER, directProbeData, STATIC_DRAW);
                VertexBuffer.unbind();
            }
            semanticObservation$translucentProbePresent = true;
            semanticObservation$translucentProbeManifestFingerprint =
                semanticObservation$manifestFingerprint;

            if (lightingRetentionVertexData == null
                || lightingRetentionVertexData.length
                    != vertexCount * LightingRetentionVertexData.BYTES_PER_VERTEX) {
                throw new IllegalStateException(
                    "Lighting-retention sidecar does not match the uploaded vertex buffer"
                );
            }
            LightingRetentionVertexData.requireCompatibleVertexCount(
                lightingRetentionVertexData,
                vertexCount
            );
            semanticObservation$lightingRetentionInvalidOnly =
                LightingRetentionVertexData.isInvalidOnly(lightingRetentionVertexData);
            if (!semanticObservation$lightingRetentionInvalidOnly) {
                if (semanticObservation$lightingRetentionBufferId < 0) {
                    RenderSystem.glGenBuffers(id -> semanticObservation$lightingRetentionBufferId = id);
                }
                ByteBuffer directLightingData = ByteBuffer.allocateDirect(
                    lightingRetentionVertexData.length
                );
                directLightingData.put(lightingRetentionVertexData).flip();
                RenderSystem.glBindBuffer(
                    ARRAY_BUFFER,
                    () -> semanticObservation$lightingRetentionBufferId
                );
                RenderSystem.glBufferData(ARRAY_BUFFER, directLightingData, STATIC_DRAW);
                VertexBuffer.unbind();
            }
            semanticObservation$lightingRetentionPresent = true;
        } else {
            semanticObservation$translucentProbePresent = false;
            semanticObservation$translucentProbeManifestFingerprint = null;
            semanticObservation$lightingRetentionPresent = false;
        }
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void semanticObservation$drawSidecar(Matrix4f matrix, int mode, CallbackInfo callback) {
        if (FogRetentionRenderPass.isActive()) {
            FogRetentionGpuTransport.draw(
                FogRetentionRenderPass.requireContext(),
                vertexCount,
                matrix,
                mode
            );
            callback.cancel();
            return;
        }
        if (LightingRetentionRenderPass.isActive()) {
            semanticObservation$drawLightingRetention(matrix, mode);
            callback.cancel();
            return;
        }
        if (TranslucentProbeRenderPass.isActive()) {
            semanticObservation$drawTranslucentProbe(matrix, mode);
            callback.cancel();
            return;
        }
        if (!SemanticRenderPass.isActive()) {
            return;
        }
        String activeFingerprint = BlockClassManifestRuntime.require().getFingerprintSha256();
        if (!activeFingerprint.equals(semanticObservation$manifestFingerprint)) {
            throw new IllegalStateException("Semantic vertex buffer uses a different manifest fingerprint");
        }
        if (semanticObservation$vertexCount != vertexCount) {
            throw new IllegalStateException("Semantic vertex buffer has a mismatched vertex count");
        }

        SemanticGpuTransport.draw(
            SemanticRenderPass.requireLayer(),
            semanticObservation$bufferId,
            semanticObservation$backgroundOnly,
            vertexCount,
            matrix,
            mode
        );
        callback.cancel();
    }

    @Unique
    private void semanticObservation$drawTranslucentProbe(Matrix4f matrix, int mode) {
        if (!TranslucentProbeConfiguration.isEnabled() || !semanticObservation$translucentProbePresent) {
            throw new IllegalStateException("Vertex buffer has no enabled translucent probe data");
        }
        String activeFingerprint = BlockClassManifestRuntime.require().getFingerprintSha256();
        if (!activeFingerprint.equals(semanticObservation$translucentProbeManifestFingerprint)) {
            throw new IllegalStateException(
                "Translucent probe vertex buffer uses a different manifest fingerprint"
            );
        }
        if (semanticObservation$vertexCount != vertexCount) {
            throw new IllegalStateException("Translucent probe vertex buffer has a mismatched vertex count");
        }
        TranslucentProbeGpuTransport.draw(
            TranslucentProbeRenderPass.requireContext(),
            semanticObservation$translucentProbeBufferId,
            semanticObservation$translucentProbeBackgroundOnly,
            vertexCount,
            matrix,
            mode
        );
    }

    @Unique
    private void semanticObservation$drawLightingRetention(Matrix4f matrix, int mode) {
        if (!TranslucentProbeConfiguration.isEnabled()
            || !semanticObservation$lightingRetentionPresent) {
            throw new IllegalStateException("Vertex buffer has no enabled lighting-retention data");
        }
        if (semanticObservation$vertexCount != vertexCount) {
            throw new IllegalStateException(
                "Lighting-retention vertex buffer has a mismatched vertex count"
            );
        }
        LightingRetentionGpuTransport.draw(
            LightingRetentionRenderPass.requireContext(),
            semanticObservation$lightingRetentionBufferId,
            semanticObservation$lightingRetentionInvalidOnly,
            vertexCount,
            matrix,
            mode
        );
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void semanticObservation$deleteSidecar(CallbackInfo callback) {
        if (semanticObservation$bufferId >= 0) {
            RenderSystem.glDeleteBuffers(semanticObservation$bufferId);
            semanticObservation$bufferId = -1;
        }
        if (semanticObservation$translucentProbeBufferId >= 0) {
            RenderSystem.glDeleteBuffers(semanticObservation$translucentProbeBufferId);
            semanticObservation$translucentProbeBufferId = -1;
        }
        if (semanticObservation$lightingRetentionBufferId >= 0) {
            RenderSystem.glDeleteBuffers(semanticObservation$lightingRetentionBufferId);
            semanticObservation$lightingRetentionBufferId = -1;
        }
    }
}
