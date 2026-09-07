package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class TranslucentProbeGpuTransportTest {
    @Test
    public void keepsSemanticClassBytesSeparateFromTextureRgb() {
        String shader = TranslucentProbeGpuTransport.fragmentShaderSource();

        assertTrue(shader.contains("vec4(ProbeCode.r, ProbeCode.g, ProbeCode.b, atlas.a)"));
        assertTrue(shader.contains("if (atlas.a <= 0.0) discard"));
        assertFalse(shader.contains("atlas.r"));
        assertFalse(shader.contains("atlas.g"));
        assertFalse(shader.contains("atlas.b"));
    }

    @Test
    public void depthPeelUsesThePreviousDiagnosticDepth() {
        String shader = TranslucentProbeGpuTransport.fragmentShaderSource();

        assertTrue(shader.contains("texture2D(PreviousDepth"));
        assertTrue(shader.contains("gl_FragCoord.z <= previous"));
        assertTrue(TranslucentProbePassMode.DEPTH_PEEL.usesPreviousDepth());
        assertFalse(TranslucentProbePassMode.FRONTMOST.usesPreviousDepth());
    }

    @Test
    public void vertexShaderUsesMinecraftPositionAndAtlasCoordinates() {
        String shader = TranslucentProbeGpuTransport.vertexShaderSource();

        assertTrue(shader.contains("gl_ModelViewProjectionMatrix * gl_Vertex"));
        assertTrue(shader.contains("gl_TexCoord[0] = gl_MultiTexCoord0"));
        assertTrue(shader.contains("ProbeCode = gl_Color"));
    }
}
