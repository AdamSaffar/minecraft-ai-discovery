package io.github.minecraftaidiscovery.semantic.visibility;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public final class BaseInfluenceProbeStudy {
    private BaseInfluenceProbeStudy() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length != 1) {
            throw new IllegalArgumentException("Expected one probe-root path");
        }
        Path probeRoot = Paths.get(arguments[0]);
        List<BaseInfluenceProbeCapture> captures =
            BaseInfluenceProbeDirectoryReader.readAllVersionFive(probeRoot);
        if (captures.isEmpty()) {
            throw new IllegalArgumentException("No version-5 probe directories were found");
        }

        System.out.println(
            "capture\tbackground\tsupported\tunsupportedLighting\toverflow\t"
                + "firstPersonUnsupported\tminR\tminG\tminB\tmaxR\tmaxG\tmaxB\t"
                + "meanR\tmeanG\tmeanB"
        );
        for (BaseInfluenceProbeCapture capture : captures) {
            System.out.println(BaseInfluenceProbeSummary.from(capture).toTsv());
        }
    }
}
