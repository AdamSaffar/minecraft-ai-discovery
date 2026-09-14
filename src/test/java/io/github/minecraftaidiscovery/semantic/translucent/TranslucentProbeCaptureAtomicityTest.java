package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class TranslucentProbeCaptureAtomicityTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void publishesCompletedDirectoryWithOneAtomicMove() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("probe-complete");
        try (TranslucentProbeCapture.AtomicCaptureDirectory capture =
            TranslucentProbeCapture.openAtomicDirectory(target)) {
            Files.write(
                capture.getWorkingDirectory().resolve("metadata.json"),
                "{}".getBytes(StandardCharsets.UTF_8)
            );
            capture.commit();
        }

        assertTrue(Files.isRegularFile(target.resolve("metadata.json")));
        assertFalse(Files.exists(target.resolveSibling("probe-complete.partial")));
    }

    @Test
    public void removesIncompleteDirectoryWhenSerializationFails() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("probe-failed");
        Path working = target.resolveSibling("probe-failed.partial");
        boolean failed = false;
        try {
            try (TranslucentProbeCapture.AtomicCaptureDirectory capture =
                TranslucentProbeCapture.openAtomicDirectory(target)) {
                Files.write(capture.getWorkingDirectory().resolve("partial.bin"), new byte[1]);
                throw new IOException("serialization failed");
            }
        } catch (IOException expected) {
            failed = true;
        }
        assertTrue(failed);
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(working));
    }
}
