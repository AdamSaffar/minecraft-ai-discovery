package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.SemanticBufferBuilderExtension;
import io.github.minecraftaidiscovery.semantic.SemanticVertexData;
import io.github.minecraftaidiscovery.semantic.SemanticVertexEncoding;
import java.util.List;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin implements SemanticBufferBuilderExtension {
    @Shadow
    private List<BufferBuilder.DrawArrayParameters> parameters;

    @Shadow
    private int lastParameterIndex;

    @Unique
    private final SemanticVertexData semanticObservation$vertexData = new SemanticVertexData();

    @Inject(method = "begin", at = @At("HEAD"))
    private void semanticObservation$discardAbandonedData(
        int drawMode,
        VertexFormat format,
        CallbackInfo callback
    ) {
        if (lastParameterIndex == parameters.size()) {
            semanticObservation$vertexData.clear();
        }
    }

    @Inject(method = "next", at = @At("RETURN"))
    private void semanticObservation$appendClassId(CallbackInfo callback) {
        semanticObservation$vertexData.appendClassId(SemanticVertexEncoding.getActiveClassId());
    }

    @Override
    public byte[] semanticObservation$takeVertexData(int vertexCount) {
        return semanticObservation$vertexData.takeVertices(vertexCount);
    }
}
