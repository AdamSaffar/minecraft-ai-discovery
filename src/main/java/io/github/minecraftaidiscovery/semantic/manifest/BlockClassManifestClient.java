package io.github.minecraftaidiscovery.semantic.manifest;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Path;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public final class BlockClassManifestClient implements ClientModInitializer {
    private static final Logger LOGGER = LogManager.getLogger("BlockClassManifestProbe");

    @Override
    public void onInitializeClient() {
        BlockClassManifest manifest = BlockClassManifestProbe.generate();
        BlockClassVocabulary vocabulary = BlockClassManifestRuntime.initialize(manifest);
        try {
            Path outputPath = BlockClassManifestProbe.writeCandidate(
                manifest,
                FabricLoader.getInstance().getGameDir()
            );
            LOGGER.info(
                "Wrote block class manifest candidate with {} entries and SHA-256 {} to {}",
                vocabulary.getEntryCount(),
                vocabulary.getFingerprintSha256(),
                outputPath.toAbsolutePath()
            );
        } catch (IOException exception) {
            throw new UncheckedIOException("Could not write block class manifest candidate", exception);
        }
    }
}
