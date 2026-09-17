package io.github.minecraftaidiscovery.semantic.visibility;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import javax.imageio.ImageIO;

final class BaseQualificationTrialWriter {
    static final int SOURCE_CROP_SIZE = 32;
    static final int DISPLAY_SCALE = 5;
    static final int PANEL_SIZE = SOURCE_CROP_SIZE * DISPLAY_SCALE;

    private static final int IMAGE_WIDTH = 600;
    private static final int IMAGE_HEIGHT = 240;
    private static final int PANEL_TOP = 52;
    private static final int LEFT_X = 20;
    private static final int TARGET_X = 220;
    private static final int RIGHT_X = 420;
    private static final int MASKED_RGB = 0x606060;
    private static final Gson JSON = new GsonBuilder().setPrettyPrinting().create();

    private BaseQualificationTrialWriter() {
    }

    static void write(
        Path outputDirectory,
        Path studyManifestPath,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan
    ) throws IOException {
        if (outputDirectory == null || studyManifestPath == null || manifest == null || plan == null) {
            throw new IllegalArgumentException("Calibration trial output is incomplete");
        }
        Path output = outputDirectory.toAbsolutePath().normalize();
        if (Files.exists(output, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Calibration output already exists: " + output);
        }
        Path developmentLabeler = output.resolve("labeler-development");
        Path sealed = output.resolve("sealed");
        Path heldOutLabeler = sealed.resolve("labeler-held-out");
        Path developmentImages = developmentLabeler.resolve("presentations");
        Path heldOutImages = heldOutLabeler.resolve("presentations");
        Files.createDirectories(developmentImages);
        Files.createDirectories(heldOutImages);

        // Held-out trial pixels exist only in the sealed tree until development is frozen.

        JsonArray developmentTrials = new JsonArray();
        JsonArray heldOutTrials = new JsonArray();
        JsonArray developmentKey = new JsonArray();
        JsonArray heldOutKey = new JsonArray();
        for (BaseQualificationPresentation presentation : plan.getPresentations()) {
            boolean development = presentation.getTarget().getPair().getPartition()
                == BaseQualificationStudyManifest.Partition.DEVELOPMENT;
            Path imagePath = (development ? developmentImages : heldOutImages).resolve(
                presentation.getTrialId() + ".png"
            );
            BufferedImage image = renderPresentation(
                presentation,
                plan,
                manifest
            );
            if (!ImageIO.write(image, "png", imagePath.toFile())) {
                throw new IOException("PNG writer is unavailable");
            }
            JsonObject labelerEntry = new JsonObject();
            labelerEntry.addProperty("trialId", presentation.getTrialId());
            labelerEntry.addProperty(
                "image",
                "presentations/" + presentation.getTrialId() + ".png"
            );
            (development ? developmentTrials : heldOutTrials).add(labelerEntry);

            JsonObject key = keyEntry(presentation);
            if (development) {
                developmentKey.add(key);
            } else {
                heldOutKey.add(key);
            }
        }
        if (developmentTrials.size() + heldOutTrials.size()
                != BaseQualificationTrialPlan.EXPECTED_PRESENTATIONS
            || developmentKey.size() != developmentTrials.size()
            || heldOutKey.size() != heldOutTrials.size()
            || heldOutKey.size() != developmentKey.size()) {
            throw new IllegalStateException("Calibration key partition is invalid");
        }

        Path developmentKeyPath = sealed.resolve("development-key.json");
        Path heldOutKeyPath = sealed.resolve("held-out-key.json");
        writeJson(developmentKeyPath, keyRoot("development", developmentKey));
        writeJson(heldOutKeyPath, keyRoot("held_out", heldOutKey));
        Files.write(
            developmentLabeler.resolve("index.html"),
            labelerHtml(
                developmentTrials,
                BaseQualificationTrialPlan.opaqueId(manifest.getRandomSeed(), "development-labeler"),
                "development-responses.tsv"
            ).getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            developmentLabeler.resolve("README.txt"),
            labelerInstructions("development").getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            heldOutLabeler.resolve("index.html"),
            labelerHtml(
                heldOutTrials,
                BaseQualificationTrialPlan.opaqueId(manifest.getRandomSeed(), "held-out-labeler"),
                "held-out-responses.tsv"
            ).getBytes(StandardCharsets.UTF_8)
        );
        Files.write(
            heldOutLabeler.resolve("README.txt"),
            labelerInstructions("held-out").getBytes(StandardCharsets.UTF_8)
        );
        writeJson(
            output.resolve("input-lock.json"),
            inputLock(
                studyManifestPath,
                manifest,
                plan,
                developmentKeyPath,
                heldOutKeyPath
            )
        );
    }

    static void releaseHeldOutLabeler(Path studyDirectory) throws IOException {
        Path study = studyDirectory.toAbsolutePath().normalize();
        Path source = study.resolve("sealed/labeler-held-out");
        Path destination = study.resolve("labeler-held-out");
        if (!Files.isDirectory(source, LinkOption.NOFOLLOW_LINKS)
            || Files.exists(destination, LinkOption.NOFOLLOW_LINKS)) {
            throw new IllegalArgumentException("Held-out labeler cannot be released");
        }
        // A same-volume directory move makes the release an all-or-nothing visibility change.
        try {
            Files.move(source, destination, StandardCopyOption.ATOMIC_MOVE);
        } catch (AtomicMoveNotSupportedException exception) {
            Files.move(source, destination);
        }
    }

    private static BufferedImage renderPresentation(
        BaseQualificationPresentation presentation,
        BaseQualificationTrialPlan plan,
        BaseQualificationStudyManifest manifest
    ) {
        BaseQualificationTarget target = presentation.getTarget();
        BaseQualificationStudyManifest.Layout layout = target.getCapture().getLayout();
        String referenceName = layout == BaseQualificationStudyManifest.Layout.FAR
            ? manifest.getReferenceFarCapture()
            : manifest.getReferenceNearCapture();
        BaseQualificationCapture reference = plan.getCaptures().get(referenceName);
        BaseQualificationTargetGeometry referenceGeometry = plan.geometryFor(layout);
        BaseQualificationCapture targetCapture = plan.getCaptures().get(
            target.getCapture().getCaptureName()
        );
        BaseQualificationTargetMask targetMask = target.getMask();
        BaseQualificationTargetMask referenceA = referenceGeometry.requireMask(
            target.getPair().getCode(),
            BaseQualificationTargetRegion.LARGE_A
        );
        BaseQualificationTargetMask referenceB = referenceGeometry.requireMask(
            target.getPair().getCode(),
            BaseQualificationTargetRegion.LARGE_B
        );
        BaseQualificationTargetMask leftMask = presentation.isIdentityAOnLeft()
            ? referenceA
            : referenceB;
        BaseQualificationTargetMask rightMask = presentation.isIdentityAOnLeft()
            ? referenceB
            : referenceA;

        BufferedImage image = new BufferedImage(
            IMAGE_WIDTH,
            IMAGE_HEIGHT,
            BufferedImage.TYPE_INT_RGB
        );
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(new Color(0x202124));
            graphics.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR
            );
            graphics.drawImage(
                maskedCrop(reference, leftMask, presentation.isMirrored()),
                LEFT_X,
                PANEL_TOP,
                PANEL_SIZE,
                PANEL_SIZE,
                null
            );
            graphics.drawImage(
                maskedCrop(targetCapture, targetMask, presentation.isMirrored()),
                TARGET_X,
                PANEL_TOP,
                PANEL_SIZE,
                PANEL_SIZE,
                null
            );
            graphics.drawImage(
                maskedCrop(reference, rightMask, presentation.isMirrored()),
                RIGHT_X,
                PANEL_TOP,
                PANEL_SIZE,
                PANEL_SIZE,
                null
            );
            graphics.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 18));
            graphics.setColor(Color.WHITE);
            drawCentered(graphics, "Reference 1", LEFT_X, PANEL_SIZE);
            drawCentered(graphics, "Sample", TARGET_X, PANEL_SIZE);
            drawCentered(graphics, "Reference 2", RIGHT_X, PANEL_SIZE);
            graphics.setColor(new Color(0x9AA0A6));
            graphics.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 13));
            drawCenteredAt(
                graphics,
                "Choose which reference matches the sample, or choose uncertain.",
                IMAGE_WIDTH / 2,
                IMAGE_HEIGHT - 10
            );
        } finally {
            graphics.dispose();
        }
        return image;
    }

    private static BufferedImage maskedCrop(
        BaseQualificationCapture capture,
        BaseQualificationTargetMask mask,
        boolean mirrored
    ) {
        byte[] rgb = capture.getSameFrameRgb().copyRgb();
        int width = capture.getWidth();
        int height = capture.getHeight();
        int half = SOURCE_CROP_SIZE / 2;
        int startX = mask.getCenterX() - half;
        int startY = mask.getCenterY() - half;
        BufferedImage crop = new BufferedImage(
            SOURCE_CROP_SIZE,
            SOURCE_CROP_SIZE,
            BufferedImage.TYPE_INT_RGB
        );
        for (int outputY = 0; outputY < SOURCE_CROP_SIZE; outputY++) {
            for (int outputX = 0; outputX < SOURCE_CROP_SIZE; outputX++) {
                int sourceOffsetX = mirrored ? SOURCE_CROP_SIZE - 1 - outputX : outputX;
                int sourceX = startX + sourceOffsetX;
                int sourceY = startY + outputY;
                int packed = MASKED_RGB;
                if (sourceX >= 0 && sourceX < width && sourceY >= 0 && sourceY < height) {
                    int pixel = sourceY * width + sourceX;
                    if (mask.contains(pixel)) {
                        int offset = pixel * 3;
                        packed = Byte.toUnsignedInt(rgb[offset]) << 16
                            | Byte.toUnsignedInt(rgb[offset + 1]) << 8
                            | Byte.toUnsignedInt(rgb[offset + 2]);
                    }
                }
                crop.setRGB(outputX, outputY, packed);
            }
        }
        return crop;
    }

    private static JsonObject keyEntry(BaseQualificationPresentation presentation) {
        BaseQualificationTarget target = presentation.getTarget();
        JsonObject key = new JsonObject();
        key.addProperty("trialId", presentation.getTrialId());
        key.addProperty("batch", presentation.getBatch());
        key.addProperty("targetId", target.getTargetId());
        key.addProperty("capture", target.getCapture().getCaptureName());
        key.addProperty("condition", target.getCapture().getCondition());
        key.addProperty("layout", target.getCapture().getLayout().name().toLowerCase(Locale.ROOT));
        key.addProperty("pair", target.getPair().getCode());
        key.addProperty("region", target.getRegion().name().toLowerCase(Locale.ROOT));
        key.addProperty("targetIdentity", target.getRegion().isIdentityA() ? "A" : "B");
        key.addProperty("identityA", target.getPair().getIdentityA());
        key.addProperty("identityB", target.getPair().getIdentityB());
        key.addProperty(
            "correctResponse",
            presentation.getCorrectResponse().name().toLowerCase(Locale.ROOT)
        );
        key.addProperty("mirrored", presentation.isMirrored());
        key.addProperty("control", target.getControl().name().toLowerCase(Locale.ROOT));
        return key;
    }

    private static JsonObject keyRoot(String partition, JsonArray entries) {
        JsonObject root = new JsonObject();
        root.addProperty("format", "base-identity-qualification-key-v1");
        root.addProperty("partition", partition);
        root.addProperty("researcherOnly", true);
        root.addProperty("agentVisible", false);
        root.add("presentations", entries);
        return root;
    }

    private static JsonObject inputLock(
        Path studyManifestPath,
        BaseQualificationStudyManifest manifest,
        BaseQualificationTrialPlan plan,
        Path developmentKey,
        Path heldOutKey
    ) throws IOException {
        JsonObject root = new JsonObject();
        root.addProperty("format", "base-identity-qualification-input-lock-v1");
        root.addProperty("researcherOnly", true);
        root.addProperty("agentVisible", false);
        root.addProperty("candidateFamilyFrozen", true);
        root.addProperty("studyManifestSha256", sha256(studyManifestPath));
        root.addProperty("blockClassManifestSha256", manifest.getManifestSha256());
        root.addProperty("developmentKeySha256", sha256(developmentKey));
        root.addProperty("heldOutKeySha256", sha256(heldOutKey));
        root.addProperty("acceptedCaptureCount", plan.getCaptures().size());
        root.addProperty("uniqueTargetCount", plan.getTargets().size());
        root.addProperty("presentationCount", plan.getPresentations().size());
        JsonArray captures = new JsonArray();
        for (BaseQualificationStudyManifest.Capture entry : manifest.getCaptures()) {
            BaseQualificationCapture capture = plan.getCaptures().get(entry.getCaptureName());
            JsonObject captureJson = new JsonObject();
            captureJson.addProperty("capture", entry.getCaptureName());
            JsonObject hashes = new JsonObject();
            for (Map.Entry<String, String> hash : capture.getArtifactSha256().entrySet()) {
                hashes.addProperty(hash.getKey(), hash.getValue());
            }
            captureJson.add("sha256", hashes);
            captures.add(captureJson);
        }
        root.add("captures", captures);
        return root;
    }

    private static String labelerHtml(
        JsonArray trials,
        String storageId,
        String downloadName
    ) {
        String json = new Gson().toJson(trials);
        return "<!doctype html>\n"
            + "<html lang=\"en\"><head><meta charset=\"utf-8\">\n"
            + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">\n"
            + "<title>Visual comparison session</title>\n"
            + "<style>body{margin:0;background:#111;color:#eee;font:16px system-ui,sans-serif;"
            + "display:flex;min-height:100vh;align-items:center;justify-content:center}"
            + "main{width:min(94vw,760px);text-align:center}img{width:min(94vw,600px);height:auto;"
            + "image-rendering:pixelated;border:1px solid #444}button{font-size:18px;margin:10px 5px;"
            + "padding:12px 18px}#status{color:#aaa;margin:8px}.hidden{display:none}</style></head>\n"
            + "<body><main><h1>Visual comparison</h1><p id=\"status\"></p>"
            + "<img id=\"trial\" alt=\"comparison trial\"><div>"
            + "<button onclick=\"answer('left')\">Reference 1</button>"
            + "<button onclick=\"answer('uncertain')\">Uncertain</button>"
            + "<button onclick=\"answer('right')\">Reference 2</button></div>"
            + "<p>Keyboard: left arrow, down arrow for uncertain, right arrow.</p>"
            + "<button id=\"back\" onclick=\"back()\">Back</button>"
            + "<button id=\"export\" class=\"hidden\" onclick=\"exportResponses()\">"
            + "Export completed responses</button></main><script>\n"
            + "const trials=" + json + ";const key='base-qualification-responses-v1-"
            + storageId + "';"
            + "let responses=JSON.parse(localStorage.getItem(key)||'{}');let index=0;"
            + "while(index<trials.length&&responses[trials[index].trialId])index++;"
            + "function show(){if(index>=trials.length){document.getElementById('status').textContent="
            + "'Complete: '+trials.length+' of '+trials.length;document.getElementById('trial').className="
            + "'hidden';document.getElementById('export').className='';return;}"
            + "document.getElementById('trial').className='';document.getElementById('trial').src="
            + "trials[index].image;document.getElementById('status').textContent='Trial '+(index+1)+"
            + "' of '+trials.length;document.getElementById('back').disabled=index===0;}"
            + "function answer(value){if(index>=trials.length)return;responses[trials[index].trialId]=value;"
            + "localStorage.setItem(key,JSON.stringify(responses));index++;show();}"
            + "function back(){if(index===0)return;index--;delete responses[trials[index].trialId];"
            + "localStorage.setItem(key,JSON.stringify(responses));show();}"
            + "function exportResponses(){let lines=['trial_id\\tresponse'];for(const t of trials){"
            + "lines.push(t.trialId+'\\t'+responses[t.trialId]);}const blob=new Blob([lines.join('\\n')"
            + "+'\\n'],{type:'text/tab-separated-values'});const a=document.createElement('a');"
            + "a.href=URL.createObjectURL(blob);a.download='" + downloadName + "';a.click();"
            + "setTimeout(()=>URL.revokeObjectURL(a.href),1000);}"
            + "document.addEventListener('keydown',e=>{if(e.key==='ArrowLeft')answer('left');"
            + "else if(e.key==='ArrowRight')answer('right');else if(e.key==='ArrowDown')"
            + "answer('uncertain');});show();</script></body></html>\n";
    }

    private static String labelerInstructions(String partition) {
        return "Open index.html in a browser. For each image, choose the reference that matches "
            + "the center sample, or choose Uncertain. Use the arrow keys for faster entry. "
            + "Finish all " + partition + " trials, export the response TSV, and keep it with "
            + "this private researcher-only study. Do not open the sealed key files.\r\n";
    }

    private static void writeJson(Path path, JsonObject json) throws IOException {
        Files.write(path, (JSON.toJson(json) + "\n").getBytes(StandardCharsets.UTF_8));
    }

    private static void drawCentered(Graphics2D graphics, String text, int x, int width) {
        drawCenteredAt(graphics, text, x + width / 2, 30);
    }

    private static void drawCenteredAt(Graphics2D graphics, String text, int centerX, int baseline) {
        int textWidth = graphics.getFontMetrics().stringWidth(text);
        graphics.drawString(text, centerX - textWidth / 2, baseline);
    }

    private static String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            byte[] hash = digest.digest(bytes);
            StringBuilder hex = new StringBuilder(64);
            for (byte value : hash) {
                hex.append(String.format(Locale.ROOT, "%02X", Byte.toUnsignedInt(value)));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
