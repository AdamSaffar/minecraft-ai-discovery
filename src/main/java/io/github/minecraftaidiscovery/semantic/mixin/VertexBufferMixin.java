package io.github.minecraftaidiscovery.semantic.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.datafixers.util.Pair;
import io.github.minecraftaidiscovery.semantic.SemanticBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.SemanticGpuTransport;
import io.github.minecraftaidiscovery.semantic.SemanticRenderPass;
import io.github.minecraftaidiscovery.semantic.SemanticVertexData;
import io.github.minecraftaidiscovery.semantic.manifest.BlockClassManifestRuntime;
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
        return data;
    }

    @Inject(method = "uploadInternal", at = @At("TAIL"))
    private void semanticObservation$uploadSidecar(BufferBuilder builder, CallbackInfo callback) {
        byte[] vertexData = semanticObservation$pendingVertexData;
        semanticObservation$pendingVertexData = null;
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
    }

    @Inject(method = "draw", at = @At("HEAD"), cancellable = true)
    private void semanticObservation$drawSidecar(Matrix4f matrix, int mode, CallbackInfo callback) {
        if (!SemanticRenderPass.isActive()) {
            return;
        }
        String activeFingerprint = BlockClassManifestRuntime.require().getFingerprintSha256();
        if (!activeFingerprint.equals(semanticObservation$manifestFingerprint)) {
            throw new IllegalStateException("Solid-layer semantic buffer uses a different manifest fingerprint");
        }
        if (semanticObservation$vertexCount != vertexCount) {
            throw new IllegalStateException("Solid-layer semantic buffer has a mismatched vertex count");
        }

        SemanticGpuTransport.draw(
            format,
            semanticObservation$bufferId,
            semanticObservation$backgroundOnly,
            vertexCount,
            matrix,
            mode
        );
        callback.cancel();
    }

    @Inject(method = "close", at = @At("HEAD"))
    private void semanticObservation$deleteSidecar(CallbackInfo callback) {
        if (semanticObservation$bufferId >= 0) {
            RenderSystem.glDeleteBuffers(semanticObservation$bufferId);
            semanticObservation$bufferId = -1;
        }
    }
}
