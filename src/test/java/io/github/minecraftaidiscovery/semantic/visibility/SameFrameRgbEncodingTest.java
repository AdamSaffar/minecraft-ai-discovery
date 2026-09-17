package io.github.minecraftaidiscovery.semantic.visibility;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class SameFrameRgbEncodingTest {
    @Test
    public void boxAveragesRgbaIntoTopToBottomRgb() {
        byte[] source = sourceBuffer();
        fillOutputCell(source, 0, 0, 10, 20, 30, 0);
        fillOutputCell(source, SameFrameRgbEncoding.WIDTH - 1, 0, 40, 50, 60, 255);
        fillOutputCell(source, 0, SameFrameRgbEncoding.HEIGHT - 1, 70, 80, 90, 127);

        SameFrameRgbResult result = SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            source
        );
        byte[] rgb = result.copyRgb();

        assertRgb(rgb, 0, 0, 10, 20, 30);
        assertRgb(rgb, SameFrameRgbEncoding.WIDTH - 1, 0, 40, 50, 60);
        assertRgb(rgb, 0, SameFrameRgbEncoding.HEIGHT - 1, 70, 80, 90);
        assertEquals(SameFrameRgbEncoding.SOURCE_WIDTH, result.getSourceWidth());
        assertEquals(SameFrameRgbEncoding.SOURCE_HEIGHT, result.getSourceHeight());
        assertEquals(SameFrameRgbEncoding.WIDTH, result.getWidth());
        assertEquals(SameFrameRgbEncoding.HEIGHT, result.getHeight());
        assertEquals(SameFrameRgbEncoding.expectedRgbLength(), rgb.length);
    }

    @Test
    public void roundsEachByteSpaceChannelToNearest() {
        byte[] source = sourceBuffer();
        int sourceYStart = SameFrameRgbEncoding.SOURCE_HEIGHT
            - SameFrameRgbEncoding.DOWNSAMPLE_FACTOR;
        int sample = 0;
        for (int sourceY = sourceYStart;
            sourceY < SameFrameRgbEncoding.SOURCE_HEIGHT;
            sourceY++) {
            for (int sourceX = 0;
                sourceX < SameFrameRgbEncoding.DOWNSAMPLE_FACTOR;
                sourceX++) {
                setSourcePixel(
                    source,
                    sourceX,
                    sourceY,
                    sample < 4 ? 1 : 0,
                    sample < 5 ? 1 : 0,
                    sample == 0 ? 14 : 0,
                    sample * 17
                );
                sample++;
            }
        }

        byte[] rgb = SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            source
        ).copyRgb();

        assertRgb(rgb, 0, 0, 0, 1, 2);
    }

    @Test
    public void resultDoesNotExposeInputOrOutputBuffers() {
        byte[] source = sourceBuffer();
        fillOutputCell(source, 0, 0, 25, 50, 75, 100);
        SameFrameRgbResult result = SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            source
        );

        fillOutputCell(source, 0, 0, 0, 0, 0, 0);
        byte[] firstCopy = result.copyRgb();
        firstCopy[0] = 0;
        byte[] secondCopy = result.copyRgb();

        assertRgb(secondCopy, 0, 0, 25, 50, 75);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnexpectedSourceWidth() {
        SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH - 1,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            sourceBuffer()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsUnexpectedSourceHeight() {
        SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT + 1,
            sourceBuffer()
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMissingSource() {
        SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            null
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsShortSource() {
        SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            new byte[SameFrameRgbEncoding.expectedSourceLength() - 1]
        );
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsLongSource() {
        SameFrameRgbEncoding.encodeBottomToTopRgba(
            SameFrameRgbEncoding.SOURCE_WIDTH,
            SameFrameRgbEncoding.SOURCE_HEIGHT,
            new byte[SameFrameRgbEncoding.expectedSourceLength() + 1]
        );
    }

    private static byte[] sourceBuffer() {
        return new byte[SameFrameRgbEncoding.expectedSourceLength()];
    }

    private static void fillOutputCell(
        byte[] source,
        int destinationX,
        int destinationY,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        int sourceXStart = destinationX * SameFrameRgbEncoding.DOWNSAMPLE_FACTOR;
        int sourceYStart = SameFrameRgbEncoding.SOURCE_HEIGHT
            - (destinationY + 1) * SameFrameRgbEncoding.DOWNSAMPLE_FACTOR;
        for (int sourceY = sourceYStart;
            sourceY < sourceYStart + SameFrameRgbEncoding.DOWNSAMPLE_FACTOR;
            sourceY++) {
            for (int sourceX = sourceXStart;
                sourceX < sourceXStart + SameFrameRgbEncoding.DOWNSAMPLE_FACTOR;
                sourceX++) {
                setSourcePixel(source, sourceX, sourceY, red, green, blue, alpha);
            }
        }
    }

    private static void setSourcePixel(
        byte[] source,
        int x,
        int y,
        int red,
        int green,
        int blue,
        int alpha
    ) {
        int offset = (y * SameFrameRgbEncoding.SOURCE_WIDTH + x)
            * SameFrameRgbEncoding.SOURCE_BYTES_PER_PIXEL;
        source[offset] = (byte)red;
        source[offset + 1] = (byte)green;
        source[offset + 2] = (byte)blue;
        source[offset + 3] = (byte)alpha;
    }

    private static void assertRgb(
        byte[] rgb,
        int x,
        int y,
        int red,
        int green,
        int blue
    ) {
        int offset = (y * SameFrameRgbEncoding.WIDTH + x)
            * SameFrameRgbEncoding.RGB_BYTES_PER_PIXEL;
        assertEquals(red, Byte.toUnsignedInt(rgb[offset]));
        assertEquals(green, Byte.toUnsignedInt(rgb[offset + 1]));
        assertEquals(blue, Byte.toUnsignedInt(rgb[offset + 2]));
    }
}
