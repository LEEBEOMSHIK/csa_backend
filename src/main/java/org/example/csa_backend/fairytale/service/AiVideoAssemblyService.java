package org.example.csa_backend.fairytale.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Composes the slide images + TTS audio that {@code AiFairytaleService}
 * already generates per page into a single mp4 by shelling out to the
 * system {@code ffmpeg} binary (no external paid video-generation API).
 *
 * Each page becomes a short segment (image held for the audio's duration,
 * or a fixed-length silent segment when a page has no audio), and segments
 * are concatenated with ffmpeg's concat demuxer. All work happens in a
 * per-call temp directory that is always cleaned up.
 */
@Slf4j
@Service
public class AiVideoAssemblyService {

    private static final int SILENT_SEGMENT_SECONDS = 5;
    private static final long FFMPEG_TIMEOUT_SECONDS = 180;
    private static final long PROCESS_KILL_GRACE_SECONDS = 10;
    /** ffmpeg's own error/log output is at most a few KB; cap what we buffer in memory
     *  so a confused/misbehaving ffmpeg process (e.g. fed a non-image as an "image") that
     *  floods stdout/stderr can never exhaust the JVM heap. */
    private static final int MAX_CAPTURED_OUTPUT_BYTES = 262_144;
    private static final String VIDEO_SIZE = "1024:1024";
    private static final int VIDEO_FPS = 24;
    private static final String SCALE_PAD_FILTER =
            "scale=" + VIDEO_SIZE + ":force_original_aspect_ratio=decrease,"
                    + "pad=" + VIDEO_SIZE + ":(ow-iw)/2:(oh-ih)/2,fps=" + VIDEO_FPS;

    public record PageMedia(int pageIndex, byte[] imageData, byte[] audioData) {}

    /**
     * @return the assembled mp4 bytes.
     * @throws VideoAssemblyException if no page has a usable image, or ffmpeg
     *                                 fails/times out/is not installed.
     */
    public byte[] assembleVideo(Long fairytaleId, List<PageMedia> pages) {
        List<PageMedia> usablePages = pages.stream()
                .filter(p -> p.imageData() != null && p.imageData().length > 0)
                .sorted(Comparator.comparingInt(PageMedia::pageIndex))
                .toList();

        if (usablePages.isEmpty()) {
            throw new VideoAssemblyException(
                    "영상 합성에 사용할 이미지가 없습니다: fairytaleId=" + fairytaleId);
        }

        Path workDir;
        try {
            workDir = Files.createTempDirectory("ai-video-" + fairytaleId + "-");
        } catch (IOException e) {
            throw new VideoAssemblyException(
                    "영상 작업 임시 디렉터리 생성 실패: fairytaleId=" + fairytaleId, e);
        }

        try {
            List<Path> segments = new ArrayList<>();
            for (PageMedia page : usablePages) {
                segments.add(buildSegment(workDir, page));
            }
            Path output = concatSegments(workDir, segments, fairytaleId);
            return Files.readAllBytes(output);
        } catch (IOException e) {
            throw new VideoAssemblyException(
                    "완성된 영상 파일 읽기 실패: fairytaleId=" + fairytaleId, e);
        } finally {
            cleanup(workDir);
        }
    }

    private Path buildSegment(Path workDir, PageMedia page) {
        try {
            Path imagePath = workDir.resolve("page_" + page.pageIndex() + ".png");
            Files.write(imagePath, page.imageData());
            Path segmentPath = workDir.resolve("segment_" + page.pageIndex() + ".mp4");

            List<String> command;
            if (page.audioData() != null && page.audioData().length > 0) {
                Path audioPath = workDir.resolve("page_" + page.pageIndex() + ".mp3");
                Files.write(audioPath, page.audioData());
                command = List.of(
                        "ffmpeg", "-y",
                        "-loop", "1", "-i", imagePath.toString(),
                        "-i", audioPath.toString(),
                        "-vf", SCALE_PAD_FILTER,
                        "-c:v", "libx264", "-tune", "stillimage", "-pix_fmt", "yuv420p",
                        "-c:a", "aac", "-b:a", "192k",
                        "-shortest",
                        segmentPath.toString()
                );
            } else {
                command = List.of(
                        "ffmpeg", "-y",
                        "-loop", "1", "-i", imagePath.toString(),
                        "-f", "lavfi", "-i", "anullsrc=channel_layout=stereo:sample_rate=44100",
                        "-vf", SCALE_PAD_FILTER,
                        "-c:v", "libx264", "-tune", "stillimage", "-pix_fmt", "yuv420p",
                        "-c:a", "aac", "-b:a", "192k",
                        "-t", String.valueOf(SILENT_SEGMENT_SECONDS),
                        segmentPath.toString()
                );
            }
            runFfmpeg(command, "페이지 " + page.pageIndex() + " 세그먼트 생성");
            return segmentPath;
        } catch (IOException e) {
            throw new VideoAssemblyException(
                    "페이지 " + page.pageIndex() + " 세그먼트 입력 파일 쓰기 실패", e);
        }
    }

    private Path concatSegments(Path workDir, List<Path> segments, Long fairytaleId) {
        try {
            Path listFile = workDir.resolve("concat.txt");
            String content = segments.stream()
                    .map(this::toConcatEntry)
                    .collect(Collectors.joining("\n"));
            Files.writeString(listFile, content, StandardCharsets.UTF_8);

            Path output = workDir.resolve("output.mp4");
            List<String> command = List.of(
                    "ffmpeg", "-y",
                    "-f", "concat", "-safe", "0",
                    "-i", listFile.toString(),
                    "-c", "copy",
                    output.toString()
            );
            runFfmpeg(command, "세그먼트 병합: fairytaleId=" + fairytaleId);
            return output;
        } catch (IOException e) {
            throw new VideoAssemblyException(
                    "concat 목록 파일 작성 실패: fairytaleId=" + fairytaleId, e);
        }
    }

    private String toConcatEntry(Path segment) {
        String normalized = segment.toAbsolutePath().toString()
                .replace("\\", "/")
                .replace("'", "'\\''");
        return "file '" + normalized + "'";
    }

    private void runFfmpeg(List<String> command, String stepDescription) {
        Process process;
        try {
            ProcessBuilder builder = new ProcessBuilder(command);
            builder.redirectErrorStream(true);
            process = builder.start();
        } catch (IOException e) {
            throw new VideoAssemblyException(
                    "ffmpeg 실행 파일을 찾을 수 없습니다 (" + stepDescription + ")", e);
        }

        // Drain stdout/stderr on a separate thread, bounded to MAX_CAPTURED_OUTPUT_BYTES, so
        // that (a) a misbehaving process cannot exhaust JVM heap by flooding its output, and
        // (b) reading output never blocks waitFor()'s timeout below -- the two run concurrently.
        BoundedOutputReader outputReader = new BoundedOutputReader(process.getInputStream());
        Thread readerThread = new Thread(outputReader, "ffmpeg-output-reader");
        readerThread.setDaemon(true);
        readerThread.start();

        boolean finished;
        try {
            finished = process.waitFor(FFMPEG_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            destroyAndAwait(process, readerThread);
            Thread.currentThread().interrupt();
            throw new VideoAssemblyException("ffmpeg 실행이 중단되었습니다 (" + stepDescription + ")", e);
        }

        if (!finished) {
            destroyAndAwait(process, readerThread);
            throw new VideoAssemblyException("ffmpeg 실행이 시간 초과되었습니다 (" + stepDescription + ")");
        }

        // Process already exited naturally -- its stdout/stderr pipe is now closed, so the
        // reader thread will reach EOF and finish on its own almost immediately.
        joinQuietly(readerThread);

        if (process.exitValue() != 0) {
            log.error("ffmpeg 실행 실패 ({}): exitCode={}, output={}",
                    stepDescription, process.exitValue(), outputReader.captured());
            throw new VideoAssemblyException(
                    "ffmpeg 실행 실패 (" + stepDescription + "): exitCode=" + process.exitValue());
        }
    }

    /**
     * Forcibly kills the process and waits for the OS to actually reclaim it (and release any
     * file handles it held, e.g. the segment mp4 it was writing) before returning. Without this
     * wait, a subsequent cleanup of the temp work dir can race the still-terminating process and
     * fail to delete files it still has open (observed on Windows).
     */
    private void destroyAndAwait(Process process, Thread readerThread) {
        process.destroyForcibly();
        try {
            process.waitFor(PROCESS_KILL_GRACE_SECONDS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        joinQuietly(readerThread);
    }

    private void joinQuietly(Thread thread) {
        try {
            thread.join(TimeUnit.SECONDS.toMillis(PROCESS_KILL_GRACE_SECONDS));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Reads a process's output stream to completion (so the process never blocks on a full
     * pipe buffer), but only retains the first {@link #MAX_CAPTURED_OUTPUT_BYTES} for
     * diagnostics -- everything beyond that is read and discarded.
     */
    private static final class BoundedOutputReader implements Runnable {
        private final InputStream inputStream;
        private final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        BoundedOutputReader(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        @Override
        public void run() {
            byte[] chunk = new byte[8192];
            try {
                int read;
                while ((read = inputStream.read(chunk)) != -1) {
                    synchronized (buffer) {
                        if (buffer.size() < MAX_CAPTURED_OUTPUT_BYTES) {
                            int toCopy = Math.min(read, MAX_CAPTURED_OUTPUT_BYTES - buffer.size());
                            buffer.write(chunk, 0, toCopy);
                        }
                    }
                    // bytes beyond the cap are intentionally discarded, not buffered
                }
            } catch (IOException ignored) {
                // stream closed (process ended/was destroyed) -- nothing more to read
            }
        }

        String captured() {
            synchronized (buffer) {
                return buffer.toString(StandardCharsets.UTF_8);
            }
        }
    }

    private void cleanup(Path workDir) {
        try (Stream<Path> paths = Files.walk(workDir)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    log.warn("영상 작업 임시 파일 삭제 실패: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.warn("영상 작업 임시 디렉터리 정리 실패: {}", workDir, e);
        }
    }
}
