package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.translucent.TranslucentProbeBufferStateExtension;
import java.util.Arrays;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(BufferBuilder.State.class)
abstract class BufferBuilderStateMixin implements TranslucentProbeBufferStateExtension {
    @Unique
    private byte[] semanticObservation$translucentProbeVertexData;

    @Override
    public byte[] semanticObservation$getTranslucentProbeVertexData() {
        return semanticObservation$copy(semanticObservation$translucentProbeVertexData);
    }

    @Override
    public void semanticObservation$setTranslucentProbeVertexData(byte[] vertexData) {
        semanticObservation$translucentProbeVertexData = semanticObservation$copy(vertexData);
    }

    @Unique
    private static byte[] semanticObservation$copy(byte[] data) {
        return data == null ? null : Arrays.copyOf(data, data.length);
    }
}
