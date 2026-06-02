package com.edubase.course.service.concretes;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class FfprobeVideoDurationServiceTest {

    private final FfprobeVideoDurationService service = new FfprobeVideoDurationService();

    @Test
    void parseDurationSeconds_roundsUpFractionalSecond() {
        Integer parsed = service.parseDurationSeconds("92.4");
        assertEquals(93, parsed);
    }

    @Test
    void parseDurationSeconds_readsFirstNonEmptyLine() {
        Integer parsed = service.parseDurationSeconds("\n\n191.0\nignored");
        assertEquals(191, parsed);
    }

    @Test
    void parseDurationSeconds_returnsNullForInvalidContent() {
        Integer parsed = service.parseDurationSeconds("N/A");
        assertNull(parsed);
    }
}
