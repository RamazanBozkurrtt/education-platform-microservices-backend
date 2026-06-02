package com.edubase.course.service.concretes;

import com.edubase.course.service.abstracts.VideoDurationService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.OptionalInt;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FfprobeVideoDurationService implements VideoDurationService {

    private static final long PROCESS_TIMEOUT_SECONDS = 15L;

    @Value("${media.ffprobe-path:${FFMPEG_FFPROBE_PATH:ffprobe}}")
    private String ffprobePath;

    @Override
    public OptionalInt extractDurationSeconds(Path videoPath) {
        if (videoPath == null || !Files.exists(videoPath)) {
            return OptionalInt.empty();
        }

        Process process = null;
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(
                    ffprobePath,
                    "-v", "error",
                    "-show_entries", "format=duration",
                    "-of", "default=nokey=1:noprint_wrappers=1",
                    videoPath.toAbsolutePath().toString()
            );
            processBuilder.redirectErrorStream(true);
            process = processBuilder.start();

            String output = readOutput(process);
            boolean finished = process.waitFor(PROCESS_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                log.warn("VIDEO_DURATION_FFPROBE_TIMEOUT | file={}", videoPath.getFileName());
                return OptionalInt.empty();
            }

            if (process.exitValue() != 0) {
                log.warn("VIDEO_DURATION_FFPROBE_NON_ZERO_EXIT | exitCode={} file={} output={}",
                        process.exitValue(), videoPath.getFileName(), sanitize(output));
                return OptionalInt.empty();
            }

            Integer seconds = parseDurationSeconds(output);
            if (seconds == null || seconds <= 0) {
                return OptionalInt.empty();
            }
            return OptionalInt.of(seconds);
        } catch (Exception ex) {
            log.warn("VIDEO_DURATION_FFPROBE_ERROR | file={} msg={}", videoPath.getFileName(), ex.getMessage());
            return OptionalInt.empty();
        } finally {
            if (process != null) {
                process.destroy();
            }
        }
    }

    Integer parseDurationSeconds(String rawOutput) {
        if (rawOutput == null || rawOutput.isBlank()) {
            return null;
        }

        String normalized = rawOutput.lines()
                .map(String::trim)
                .filter(line -> !line.isBlank())
                .findFirst()
                .orElse(null);
        if (normalized == null) {
            return null;
        }

        try {
            double seconds = Double.parseDouble(normalized);
            if (!Double.isFinite(seconds) || seconds <= 0d || seconds > Integer.MAX_VALUE) {
                return null;
            }
            return (int) Math.ceil(seconds);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String readOutput(Process process) throws IOException {
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (output.length() > 0) {
                    output.append('\n');
                }
                output.append(line);
            }
        }
        return output.toString();
    }

    private String sanitize(String value) {
        if (value == null) {
            return "";
        }
        String singleLine = value.replace('\n', ' ').replace('\r', ' ').trim();
        if (singleLine.length() <= 300) {
            return singleLine;
        }
        return singleLine.substring(0, 300);
    }
}
