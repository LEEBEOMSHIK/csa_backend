package org.example.csa_backend.fairytale.service;

/**
 * Raised when server-side ffmpeg slide+audio -> mp4 assembly fails (ffmpeg
 * missing from PATH, non-zero exit code, timeout, or I/O failure while
 * preparing/reading temp files). Callers should catch this separately from
 * text/image/TTS generation failures: the fairytale's already-generated
 * pages are still valid and should not be discarded just because the video
 * step failed.
 */
public class VideoAssemblyException extends RuntimeException {

    public VideoAssemblyException(String message) {
        super(message);
    }

    public VideoAssemblyException(String message, Throwable cause) {
        super(message, cause);
    }
}
