package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.SemanticVertexEncoding;
import java.nio.ByteBuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.VertexFormatElement;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BufferBuilder.class)
abstract class BufferBuilderMixin {
    @Shadow
    private ByteBuffer buffer;

    @Shadow
    private int elementOffset;

    @Shadow
    private VertexFormatElement currentElement;

    @Inject(method = "putByte", at = @At("HEAD"), cancellable = true)
    private void semanticObservation$writeMarkerAlpha(int index, byte value, CallbackInfo callback) {
        int markerAlpha = SemanticVertexEncoding.getActiveMarkerAlpha();
        if (markerAlpha == SemanticVertexEncoding.INACTIVE) {
            return;
        }

        boolean packedBlockAlpha = index == 15;
        boolean elementBlockAlpha = index == 3
            && currentElement != null
            && currentElement.getType() == VertexFormatElement.Type.COLOR;
        if (packedBlockAlpha || elementBlockAlpha) {
            buffer.put(elementOffset + index, (byte)markerAlpha);
            callback.cancel();
        }
    }
}
