package io.github.minecraftaidiscovery.semantic;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.util.math.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;

public final class SemanticGpuTransport {
    private static final int ARRAY_BUFFER = 34962;

    private SemanticGpuTransport() {
    }

    public static void draw(
        VertexFormat normalFormat,
        int semanticBufferId,
        boolean backgroundOnly,
        int vertexCount,
        Matrix4f matrix,
        int mode
    ) {
        if (!backgroundOnly && semanticBufferId < 0) {
            throw new IllegalStateException("Solid-layer vertex buffer has no semantic sidecar");
        }
        if (mode != GL11.GL_QUADS || vertexCount % 4 != 0) {
            throw new IllegalStateException("Semantic solid-layer draw is not composed of complete quads");
        }

        boolean blockTexture = textureEnabled(GL13.GL_TEXTURE0);
        boolean lightmapTexture = textureEnabled(GL13.GL_TEXTURE2);
        boolean lighting = GL11.glIsEnabled(GL11.GL_LIGHTING);
        boolean fog = GL11.glIsEnabled(GL11.GL_FOG);
        boolean blend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean alphaTest = GL11.glIsEnabled(GL11.GL_ALPHA_TEST);
        boolean depthTest = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean dither = GL11.glIsEnabled(GL11.GL_DITHER);
        boolean colorLogic = GL11.glIsEnabled(GL11.GL_COLOR_LOGIC_OP);
        boolean polygonSmooth = GL11.glIsEnabled(GL11.GL_POLYGON_SMOOTH);
        boolean multisample = GL11.glIsEnabled(GL13.GL_MULTISAMPLE);

        normalFormat.endDrawing();
        try {
            setTextureUnit(GL13.GL_TEXTURE0, false);
            setTextureUnit(GL13.GL_TEXTURE2, false);
            RenderSystem.activeTexture(GL13.GL_TEXTURE0);
            RenderSystem.disableLighting();
            RenderSystem.disableFog();
            RenderSystem.disableBlend();
            RenderSystem.disableAlphaTest();
            RenderSystem.enableDepthTest();
            RenderSystem.depthMask(true);
            RenderSystem.colorMask(true, true, true, true);
            GL11.glDisable(GL11.GL_DITHER);
            GL11.glDisable(GL11.GL_COLOR_LOGIC_OP);
            GL11.glDisable(GL11.GL_POLYGON_SMOOTH);
            GL11.glDisable(GL13.GL_MULTISAMPLE);

            GlStateManager.vertexPointer(3, GL11.GL_FLOAT, normalFormat.getVertexSize(), 0L);
            GlStateManager.enableClientState(GL11.GL_VERTEX_ARRAY);
            if (backgroundOnly) {
                GlStateManager.color4f(0.0F, 0.0F, 0.0F, 1.0F);
            } else {
                RenderSystem.glBindBuffer(ARRAY_BUFFER, () -> semanticBufferId);
                GlStateManager.colorPointer(4, GL11.GL_UNSIGNED_BYTE, SemanticVertexData.BYTES_PER_VERTEX, 0L);
                GlStateManager.enableClientState(GL11.GL_COLOR_ARRAY);
            }

            RenderSystem.pushMatrix();
            RenderSystem.loadIdentity();
            RenderSystem.multMatrix(matrix);
            RenderSystem.drawArrays(mode, 0, vertexCount);
            RenderSystem.popMatrix();
        } finally {
            GlStateManager.disableClientState(GL11.GL_COLOR_ARRAY);
            GlStateManager.disableClientState(GL11.GL_VERTEX_ARRAY);
            restoreManagedState(lighting, fog, blend, alphaTest, depthTest);
            setTextureUnit(GL13.GL_TEXTURE2, lightmapTexture);
            setTextureUnit(GL13.GL_TEXTURE0, blockTexture);
            restoreCapability(GL11.GL_DITHER, dither);
            restoreCapability(GL11.GL_COLOR_LOGIC_OP, colorLogic);
            restoreCapability(GL11.GL_POLYGON_SMOOTH, polygonSmooth);
            restoreCapability(GL13.GL_MULTISAMPLE, multisample);
        }
    }

    private static void restoreManagedState(
        boolean lighting,
        boolean fog,
        boolean blend,
        boolean alphaTest,
        boolean depthTest
    ) {
        setLighting(lighting);
        setFog(fog);
        setBlend(blend);
        setAlphaTest(alphaTest);
        setDepthTest(depthTest);
    }

    private static boolean textureEnabled(int textureUnit) {
        RenderSystem.activeTexture(textureUnit);
        boolean enabled = GL11.glIsEnabled(GL11.GL_TEXTURE_2D);
        RenderSystem.activeTexture(GL13.GL_TEXTURE0);
        return enabled;
    }

    private static void setTextureUnit(int textureUnit, boolean enabled) {
        RenderSystem.activeTexture(textureUnit);
        if (enabled) {
            RenderSystem.enableTexture();
        } else {
            RenderSystem.disableTexture();
        }
    }

    private static void setLighting(boolean enabled) {
        if (enabled) {
            RenderSystem.enableLighting();
        } else {
            RenderSystem.disableLighting();
        }
    }

    private static void setFog(boolean enabled) {
        if (enabled) {
            RenderSystem.enableFog();
        } else {
            RenderSystem.disableFog();
        }
    }

    private static void setBlend(boolean enabled) {
        if (enabled) {
            RenderSystem.enableBlend();
        } else {
            RenderSystem.disableBlend();
        }
    }

    private static void setAlphaTest(boolean enabled) {
        if (enabled) {
            RenderSystem.enableAlphaTest();
        } else {
            RenderSystem.disableAlphaTest();
        }
    }

    private static void setDepthTest(boolean enabled) {
        if (enabled) {
            RenderSystem.enableDepthTest();
        } else {
            RenderSystem.disableDepthTest();
        }
    }

    private static void restoreCapability(int capability, boolean enabled) {
        if (enabled) {
            GL11.glEnable(capability);
        } else {
            GL11.glDisable(capability);
        }
    }
}
