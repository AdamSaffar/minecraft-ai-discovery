package io.github.minecraftaidiscovery.semantic.translucent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AccessDeniedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    public void retriesTransientAccessDeniedBeforePublishing() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("probe-retried");
        AtomicInteger moveAttempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();

        try (TranslucentProbeCapture.AtomicCaptureDirectory capture =
            TranslucentProbeCapture.openAtomicDirectory(
                target,
                (source, destination) -> {
                    if (moveAttempts.incrementAndGet() < 3) {
                        throw new AccessDeniedException(
                            source.toString(),
                            destination.toString(),
                            "temporarily locked"
                        );
                    }
                    Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
                },
                ignored -> pauses.incrementAndGet()
            )) {
            Files.write(
                capture.getWorkingDirectory().resolve("metadata.json"),
                "{}".getBytes(StandardCharsets.UTF_8)
            );
            capture.commit();
        }

        assertEquals(3, moveAttempts.get());
        assertEquals(2, pauses.get());
        assertTrue(Files.isRegularFile(target.resolve("metadata.json")));
        assertFalse(Files.exists(target.resolveSibling("probe-retried.partial")));
    }

    @Test
    public void exhaustsAccessDeniedRetriesAndRemovesPartialDirectory() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("probe-locked");
        Path working = target.resolveSibling("probe-locked.partial");
        AtomicInteger moveAttempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        boolean failed = false;

        try {
            try (TranslucentProbeCapture.AtomicCaptureDirectory capture =
                TranslucentProbeCapture.openAtomicDirectory(
                    target,
                    (source, destination) -> {
                        moveAttempts.incrementAndGet();
                        throw new AccessDeniedException(
                            source.toString(),
                            destination.toString(),
                            "still locked"
                        );
                    },
                    ignored -> pauses.incrementAndGet()
                )) {
                Files.write(capture.getWorkingDirectory().resolve("partial.bin"), new byte[1]);
                capture.commit();
            }
        } catch (AccessDeniedException expected) {
            failed = true;
        }

        assertTrue(failed);
        assertEquals(TranslucentProbeCapture.ATOMIC_MOVE_MAX_ATTEMPTS, moveAttempts.get());
        assertEquals(TranslucentProbeCapture.ATOMIC_MOVE_MAX_ATTEMPTS - 1, pauses.get());
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(working));
    }

    @Test
    public void doesNotRetryOtherMoveFailures() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("probe-other-failure");
        Path working = target.resolveSibling("probe-other-failure.partial");
        AtomicInteger moveAttempts = new AtomicInteger();
        AtomicInteger pauses = new AtomicInteger();
        boolean failed = false;

        try {
            try (TranslucentProbeCapture.AtomicCaptureDirectory capture =
                TranslucentProbeCapture.openAtomicDirectory(
                    target,
                    (source, destination) -> {
                        moveAttempts.incrementAndGet();
                        throw new IOException("not a transient access denial");
                    },
                    ignored -> pauses.incrementAndGet()
                )) {
                Files.write(capture.getWorkingDirectory().resolve("partial.bin"), new byte[1]);
                capture.commit();
            }
        } catch (IOException expected) {
            failed = true;
        }

        assertTrue(failed);
        assertEquals(1, moveAttempts.get());
        assertEquals(0, pauses.get());
        assertFalse(Files.exists(target));
        assertFalse(Files.exists(working));
    }

    @Test
    public void preservesInterruptionWhileWaitingToRetry() throws IOException {
        Path target = temporaryFolder.getRoot().toPath().resolve("probe-interrupted");
        Path working = target.resolveSibling("probe-interrupted.partial");
        boolean failed = false;

        try {
            try (TranslucentProbeCapture.AtomicCaptureDirectory capture =
                TranslucentProbeCapture.openAtomicDirectory(
                    target,
                    (source, destination) -> {
                        throw new AccessDeniedException(source.toString());
                    },
                    ignored -> {
                        throw new InterruptedException("stop retrying");
                    }
                )) {
                Files.write(capture.getWorkingDirectory().resolve("partial.bin"), new byte[1]);
                capture.commit();
            }
        } catch (IOException expected) {
            failed = true;
        }

        try {
            assertTrue(failed);
            assertTrue(Thread.currentThread().isInterrupted());
            assertFalse(Files.exists(target));
            assertFalse(Files.exists(working));
        } finally {
            Thread.interrupted();
        }
    }
}
