package com.edubase.course.grpc;

import com.edubase.contracts.course.v1.CourseByIdRequest;
import com.edubase.contracts.course.v1.CourseByIdResponse;
import com.edubase.contracts.course.v1.CourseQueryServiceGrpc;
import com.edubase.course.entity.Course;
import com.edubase.course.entity.CourseStatus;
import com.edubase.course.repository.CourseRepository;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class CourseQueryGrpcService extends CourseQueryServiceGrpc.CourseQueryServiceImplBase {

    private final CourseRepository courseRepository;

    @Override
    public void getCourseById(CourseByIdRequest request, StreamObserver<CourseByIdResponse> responseObserver) {
        String courseId = request.getCourseId() == null ? "" : request.getCourseId().trim();
        Optional<Course> course = courseId.isBlank()
                ? Optional.empty()
                : courseRepository.findByIdAndDeletedAtIsNull(courseId);

        CourseByIdResponse response = CourseByIdResponse.newBuilder()
                .setCourseId(courseId)
                .setExists(course.isPresent())
                .setPublished(course.map(Course::getStatus).filter(CourseStatus.PUBLISHED::equals).isPresent())
                .setInstructorId(course.map(Course::getInstructorId).orElse(""))
                .setTitle(course.map(Course::getTitle).orElse(""))
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }
}
