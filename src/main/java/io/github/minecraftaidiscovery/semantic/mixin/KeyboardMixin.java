package io.github.minecraftaidiscovery.semantic.mixin;

import io.github.minecraftaidiscovery.semantic.SemanticCaptureRequests;
import net.minecraft.client.Keyboard;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.LiteralText;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
abstract class KeyboardMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @Inject(method = "onKey", at = @At("HEAD"))
    private void semanticObservation$requestCapture(
        long window,
        int key,
        int scancode,
        int action,
        int modifiers,
        CallbackInfo callback
    ) {
        if (window == client.getWindow().getHandle()
            && key == GLFW.GLFW_KEY_F8
            && action == GLFW.GLFW_PRESS
            && client.world != null
            && client.currentScreen == null) {
            SemanticCaptureRequests.request();
            client.inGameHud.getChatHud().addMessage(new LiteralText("Semantic capture queued"));
        }
    }
}
