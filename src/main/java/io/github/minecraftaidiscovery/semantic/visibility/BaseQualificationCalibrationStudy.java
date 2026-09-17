package io.github.minecraftaidiscovery.semantic.visibility;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.Map;

public final class BaseQualificationCalibrationStudy {
    private BaseQualificationCalibrationStudy() {
    }

    public static void main(String[] arguments) throws IOException {
        if (arguments.length < 4) {
            throw new IllegalArgumentException(
                "Expected a calibration mode, study manifest, probe root, and study directory"
            );
        }
        String mode = arguments[0];
        Path manifestPath = Paths.get(arguments[1]).toAbsolutePath().normalize();
        Path probeRoot = Paths.get(arguments[2]).toAbsolutePath().normalize();
        Path studyDirectory = Paths.get(arguments[3]).toAbsolutePath().normalize();
        BaseQualificationStudyManifest manifest = BaseQualificationStudyManifest.read(manifestPath);
        Map<String, BaseQualificationCapture> captures = new LinkedHashMap<>();
        for (BaseQualificationStudyManifest.Capture entry : manifest.getCaptures()) {
            BaseQualificationCapture capture = BaseQualificationCaptureReader.read(
                probeRoot.resolve(entry.getCaptureName()),
                manifest.getManifestSha256()
            );
            captures.put(entry.getCaptureName(), capture);
        }
        BaseQualificationTrialPlan plan = BaseQualificationTrialPlan.create(manifest, captures);
        if ("generate".equals(mode) && arguments.length == 4) {
            BaseQualificationTrialWriter.write(
                studyDirectory,
                manifestPath,
                manifest,
                plan
            );
            System.out.println(
                "Generated " + plan.getTargets().size() + " targets and "
                    + plan.getPresentations().size() + " blinded presentations at "
                    + studyDirectory
            );
            return;
        }
        if (arguments.length != 5) {
            throw new IllegalArgumentException(
                "Scoring modes also require a blinded response TSV"
            );
        }
        Path responses = Paths.get(arguments[4]).toAbsolutePath().normalize();
        if ("freeze-development".equals(mode)) {
            BaseQualificationAnalysisFiles.freezeDevelopment(
                manifestPath,
                studyDirectory,
                responses,
                manifest,
                plan
            );
            System.out.println("Frozen the development analysis at " + studyDirectory);
            return;
        }
        if ("evaluate-held-out".equals(mode)) {
            BaseQualificationAnalysisFiles.evaluateHeldOut(
                manifestPath,
                studyDirectory,
                responses,
                manifest,
                plan
            );
            System.out.println("Completed the held-out analysis at " + studyDirectory);
            return;
        }
        throw new IllegalArgumentException("Unknown calibration mode: " + mode);
    }
}
