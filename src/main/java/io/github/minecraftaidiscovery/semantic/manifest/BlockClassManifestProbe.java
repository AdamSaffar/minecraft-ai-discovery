package io.github.minecraftaidiscovery.semantic.manifest;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.SharedConstants;
import net.minecraft.block.Block;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

public final class BlockClassManifestProbe {
    public static final String EXPECTED_MINECRAFT_VERSION = "1.16.1";
    public static final String YARN_MAPPINGS = "1.16.1+build.21";
    public static final int EXPECTED_BLOCK_ENTRY_COUNT = 763;
    public static final String CANDIDATE_FILE_NAME = "block-class-manifest-candidate.json";

    private BlockClassManifestProbe() {
    }

    public static BlockClassManifest generate() {
        String runtimeMinecraftVersion = SharedConstants.getGameVersion().getName();
        if (!EXPECTED_MINECRAFT_VERSION.equals(runtimeMinecraftVersion)) {
            throw new IllegalStateException(
                "Expected Minecraft " + EXPECTED_MINECRAFT_VERSION + " but found " + runtimeMinecraftVersion
            );
        }

        List<BlockClassManifest.SourceEntry> sourceEntries = new ArrayList<>();
        for (Block block : Registry.BLOCK) {
            Identifier identifier = Registry.BLOCK.getId(block);
            if (identifier == null) {
                throw new IllegalStateException("Block registry returned a null identifier");
            }

            int rawId = Registry.BLOCK.getRawId(block);
            if (Registry.BLOCK.get(identifier) != block) {
                throw new IllegalStateException("Block registry identifier did not round trip: " + identifier);
            }
            if (Registry.BLOCK.get(rawId) != block) {
                throw new IllegalStateException(
                    "Block registry raw ID did not round trip: " + rawId + " for " + identifier
                );
            }
            sourceEntries.add(new BlockClassManifest.SourceEntry(identifier.toString(), rawId));
        }

        BlockClassManifest manifest = BlockClassManifest.generate(
            runtimeMinecraftVersion,
            YARN_MAPPINGS,
            Registry.BLOCK_KEY.getValue().toString(),
            sourceEntries
        );
        manifest.requireEntryCount(EXPECTED_BLOCK_ENTRY_COUNT);
        return manifest;
    }

    public static Path writeCandidate(BlockClassManifest manifest, Path gameDirectory) throws IOException {
        if (gameDirectory == null) {
            throw new IllegalArgumentException("Game directory is missing");
        }
        Path outputPath = gameDirectory
            .resolve("semantic-observation")
            .resolve(CANDIDATE_FILE_NAME);
        return BlockClassManifestJson.write(manifest, outputPath);
    }
}
