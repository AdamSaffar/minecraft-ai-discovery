package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.SemanticBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.SemanticVertexData;
import io.github.minecraftaidiscovery.semantic.SemanticVertexEncoding;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionVertexData;
import io.github.minecraftaidiscovery.semantic.lighting.LightingRetentionVertexEncoding;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeBufferStateExtension;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeConfiguration;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeVertexData;
import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeVertexEncoding;
import it.unimi.dsi.fastutil.ints.IntArrays;
import it.unimi.dsi.fastutil.ints.IntComparator;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements
    SemanticBufferBuilderExtension,
    TranslucentProbeBufferBuilderExtension,
    LightingRetentionBufferBuilderExtension {
    @Shadow
    private List<BufferBuilder.DrawArrayParameters> parameters;

    @Shadow
    private int lastParameterIndex;

    @Shadow
    private int vertexCount;

    @Unique
    private final SemanticVertexData semanticObservation$vertexData = new SemanticVertexData();

    @Unique
    private final TranslucentProbeVertexData semanticObservation$translucentProbeVertexData =
        new TranslucentProbeVertexData();

    @Unique
    private final LightingRetentionVertexData semanticObservation$lightingRetentionVertexData =
        new LightingRetentionVertexData();

    @Inject(method = "begin", at = @At("HEAD"))
    private void semanticObservation$discardAbandonedData(
        int drawMode,
        VertexFormat format,
        CallbackInfo callback
    ) {
        if (lastParameterIndex == parameters.size()) {
            semanticObservation$vertexData.clear();
            semanticObservation$translucentProbeVertexData.clear();
            semanticObservation$lightingRetentionVertexData.clear();
        }
    }

    @Inject(method = "next", at = @At("RETURN"))
    private void semanticObservation$appendClassId(CallbackInfo callback) {
        semanticObservation$vertexData.appendClassId(SemanticVertexEncoding.getActiveClassId());
        if (TranslucentProbeConfiguration.isEnabled()) {
            semanticObservation$translucentProbeVertexData.append(
                TranslucentProbeVertexEncoding.getActive()
            );
            semanticObservation$lightingRetentionVertexData.append(
                LightingRetentionVertexEncoding.consumeVertex()
            );
        }
    }

    @Redirect(
        method = "sortQuads",
        at = @At(
            value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/ints/IntArrays;mergeSort([ILit/unimi/dsi/fastutil/ints/IntComparator;)V",
            remap = false
        )
    )
    private void semanticObservation$sortTranslucentProbeSidecar(
        int[] quadIndices,
        IntComparator comparator
    ) {
        IntArrays.mergeSort(quadIndices, comparator);
        // The saved permutation is reused by both the initial build and camera-driven translucent resort.
        semanticObservation$translucentProbeVertexData.reorderLastQuads(quadIndices);
    }

    @Inject(method = "popState", at = @At("RETURN"))
    private void semanticObservation$saveTranslucentProbeState(
        CallbackInfoReturnable<BufferBuilder.State> callback
    ) {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            return;
        }
        byte[] snapshot = semanticObservation$translucentProbeVertexData
            .snapshotLastVertices(vertexCount);
        ((TranslucentProbeBufferStateExtension)(Object)callback.getReturnValue())
            .semanticObservation$setTranslucentProbeVertexData(snapshot);
    }

    @Inject(method = "restoreState", at = @At("TAIL"))
    private void semanticObservation$restoreTranslucentProbeState(
        BufferBuilder.State state,
        CallbackInfo callback
    ) {
        if (!TranslucentProbeConfiguration.isEnabled()) {
            return;
        }
        byte[] snapshot = ((TranslucentProbeBufferStateExtension)(Object)state)
            .semanticObservation$getTranslucentProbeVertexData();
        if (snapshot == null) {
            throw new IllegalStateException("Translucent buffer state has no diagnostic sidecar");
        }
        semanticObservation$translucentProbeVertexData.restoreSnapshot(snapshot);
    }

    @Override
    public byte[] semanticObservation$takeVertexData(int vertexCount) {
        return semanticObservation$vertexData.takeVertices(vertexCount);
    }

    @Override
    public byte[] semanticObservation$takeTranslucentProbeVertexData(int vertexCount) {
        return semanticObservation$translucentProbeVertexData.takeVertices(vertexCount);
    }

    @Override
    public byte[] semanticObservation$takeLightingRetentionVertexData(int vertexCount) {
        return semanticObservation$lightingRetentionVertexData.takeVertices(vertexCount);
    }
}
