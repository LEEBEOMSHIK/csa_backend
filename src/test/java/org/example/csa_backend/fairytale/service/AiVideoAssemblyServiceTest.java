package org.example.csa_backend.fairytale.service;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Exercises the real system {@code ffmpeg}/{@code ffprobe} binaries (confirmed
 * present on this machine, ffmpeg 8.1.1 via winget) rather than mocking the
 * process. No provider API keys are needed since this service never calls an
 * external AI provider -- it only composes locally-supplied image/audio bytes.
 */
class AiVideoAssemblyServiceTest {

    private final AiVideoAssemblyService service = new AiVideoAssemblyService();

    /**
     * These tests shell out to the real {@code ffmpeg}/{@code ffprobe} binaries. On a machine
     * (CI runner, another developer's PC) where they are not installed/on PATH, skip the whole
     * class instead of failing the build -- {@link ProcessBuilder#start()} throwing here means
     * "not found", which we treat as an assumption failure (skip), not a test failure.
     */
    @BeforeAll
    static void ffmpegMustBeAvailable() {
        Assumptions.assumeTrue(isExecutableAvailable("ffmpeg"),
                "ffmpeg not found on PATH -- skipping AiVideoAssemblyServiceTest");
        Assumptions.assumeTrue(isExecutableAvailable("ffprobe"),
                "ffprobe not found on PATH -- skipping AiVideoAssemblyServiceTest");
    }

    private static boolean isExecutableAvailable(String executable) {
        try {
            Process process = new ProcessBuilder(executable, "-version")
                    .redirectErrorStream(true)
                    .start();
            process.getInputStream().readAllBytes();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return false;
        }
    }

    @Test
    void assembleVideoProducesPlayableMp4FromSilentAndAudioPages() throws Exception {
        byte[] page1Image = generateTestPng(Color.RED);
        byte[] page2Image = generateTestPng(Color.GREEN);
        byte[] page3Image = generateTestPng(Color.BLUE);
        byte[] page2Audio = generateTestAudioMp3(1.0);

        List<AiVideoAssemblyService.PageMedia> pages = List.of(
                new AiVideoAssemblyService.PageMedia(1, page1Image, null),
                new AiVideoAssemblyService.PageMedia(2, page2Image, page2Audio),
                new AiVideoAssemblyService.PageMedia(3, page3Image, null)
        );

        long tempDirsBefore = countAiVideoTempDirs(999L);
        byte[] result = service.assembleVideo(999L, pages);
        long tempDirsAfter = countAiVideoTempDirs(999L);

        assertThat(result).isNotEmpty();
        assertThat(tempDirsAfter).as("temp work dir must be cleaned up after assembly").isEqualTo(tempDirsBefore);

        Path outputFile = Files.createTempFile("assembled-video-", ".mp4");
        try {
            Files.write(outputFile, result);
            double durationSeconds = probeDurationSeconds(outputFile);
            // page1 (silent, 5s) + page2 (~1s audio) + page3 (silent, 5s) ~= 11s
            assertThat(durationSeconds).isGreaterThan(9.0).isLessThan(13.0);
            assertThat(probeHasVideoStream(outputFile)).isTrue();
        } finally {
            Files.deleteIfExists(outputFile);
        }
    }

    @Test
    void assembleVideoRejectsPagesWithoutAnyUsableImage() {
        List<AiVideoAssemblyService.PageMedia> pages = List.of(
                new AiVideoAssemblyService.PageMedia(1, null, null),
                new AiVideoAssemblyService.PageMedia(2, new byte[0], null)
        );

        assertThatThrownBy(() -> service.assembleVideo(1000L, pages))
                .isInstanceOf(VideoAssemblyException.class);
    }

    @Test
    void assembleVideoWrapsFfmpegFailureAndStillCleansUpTempFiles() throws Exception {
        byte[] garbageNotAnImage = "this is not a real image".getBytes(StandardCharsets.UTF_8);
        List<AiVideoAssemblyService.PageMedia> pages = List.of(
                new AiVideoAssemblyService.PageMedia(1, garbageNotAnImage, null)
        );

        long tempDirsBefore = countAiVideoTempDirs(1001L);

        assertThatThrownBy(() -> service.assembleVideo(1001L, pages))
                .isInstanceOf(VideoAssemblyException.class);

        long tempDirsAfter = countAiVideoTempDirs(1001L);
        assertThat(tempDirsAfter).as("temp work dir must be cleaned up even on ffmpeg failure").isEqualTo(tempDirsBefore);
    }

    private byte[] generateTestPng(Color color) throws IOException {
        BufferedImage image = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        g.setColor(color);
        g.fillRect(0, 0, 320, 240);
        g.dispose();

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private byte[] generateTestAudioMp3(double durationSeconds) throws Exception {
        Path audioFile = Files.createTempFile("ai-video-test-audio-", ".mp3");
        try {
            List<String> command = List.of(
                    "ffmpeg", "-y",
                    "-f", "lavfi", "-i", "sine=frequency=440:duration=" + durationSeconds,
                    "-c:a", "libmp3lame",
                    audioFile.toString()
            );
            runProcess(command);
            return Files.readAllBytes(audioFile);
        } finally {
            Files.deleteIfExists(audioFile);
        }
    }

    private double probeDurationSeconds(Path mp4File) throws Exception {
        List<String> command = List.of(
                "ffprobe", "-v", "error",
                "-show_entries", "format=duration",
                "-of", "default=noprint_wrappers=1:nokey=1",
                mp4File.toString()
        );
        String output = runProcess(command).strip();
        return Double.parseDouble(output);
    }

    private boolean probeHasVideoStream(Path mp4File) throws Exception {
        List<String> command = List.of(
                "ffprobe", "-v", "error",
                "-select_streams", "v:0",
                "-show_entries", "stream=codec_type",
                "-of", "default=noprint_wrappers=1:nokey=1",
                mp4File.toString()
        );
        String output = runProcess(command).strip();
        return "video".equals(output);
    }

    private String runProcess(List<String> command) throws Exception {
        ProcessBuilder builder = new ProcessBuilder(command);
        builder.redirectErrorStream(true);
        Process process = builder.start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        boolean finished = process.waitFor(60, TimeUnit.SECONDS);
        if (!finished) {
            process.destroyForcibly();
            throw new IllegalStateException("Test helper process timed out: " + command);
        }
        if (process.exitValue() != 0) {
            throw new IllegalStateException("Test helper process failed (" + command + "): " + output);
        }
        return output;
    }

    private long countAiVideoTempDirs(Long fairytaleId) throws IOException {
        Path tempRoot = Path.of(System.getProperty("java.io.tmpdir"));
        String prefix = "ai-video-" + fairytaleId + "-";
        long count = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(tempRoot, prefix + "*")) {
            for (Path ignored : stream) {
                count++;
            }
        }
        return count;
    }
}
