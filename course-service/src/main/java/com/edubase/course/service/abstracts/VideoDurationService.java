package com.edubase.course.service.abstracts;

import java.nio.file.Path;
import java.util.OptionalInt;

public interface VideoDurationService {

    OptionalInt extractDurationSeconds(Path videoPath);
}
