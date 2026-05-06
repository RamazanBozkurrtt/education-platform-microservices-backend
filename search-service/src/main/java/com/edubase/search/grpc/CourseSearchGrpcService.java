package com.edubase.search.grpc;

import com.edubase.contracts.search.v1.CourseSearchHit;
import com.edubase.contracts.search.v1.CourseSearchRequest;
import com.edubase.contracts.search.v1.CourseSearchResponse;
import com.edubase.contracts.search.v1.CourseSearchServiceGrpc;
import com.edubase.search.service.CourseSearchService;
import com.edubase.search.service.model.SearchCourseResult;
import io.grpc.stub.StreamObserver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CourseSearchGrpcService extends CourseSearchServiceGrpc.CourseSearchServiceImplBase {

    private final CourseSearchService courseSearchService;

    @Override
    public void searchCourses(CourseSearchRequest request, StreamObserver<CourseSearchResponse> responseObserver) {
        SearchCourseResult result = courseSearchService.searchCourses(
                normalize(request.getQuery()),
                request.getPageNumber(),
                request.getPageSize(),
                normalize(request.getCategoryId()),
                normalize(request.getInstructorId()),
                parseOptionalPrice(request.getMinPrice()),
                parseOptionalPrice(request.getMaxPrice()),
                parseOptionalRating(request.getMinRating())
        );

        CourseSearchResponse response = CourseSearchResponse.newBuilder()
                .addAllHits(result.hits().stream().map(hit -> CourseSearchHit.newBuilder()
                        .setCourseId(defaultString(hit.courseId()))
                        .setTitle(defaultString(hit.title()))
                        .setDescription(defaultString(hit.description()))
                        .setInstructorId(defaultString(hit.instructorId()))
                        .setCategoryId(defaultString(hit.categoryId()))
                        .setPrice(hit.price() == null ? 0.0d : hit.price())
                        .setStatus(defaultString(hit.status()))
                        .addAllTags(hit.tags())
                        .addAllLearningOutcomes(hit.learningOutcomes())
                        .setAverageRating(hit.averageRating() == null ? 0.0d : hit.averageRating())
                        .setRatingCount(hit.ratingCount() == null ? 0L : hit.ratingCount())
                        .setScore(hit.score())
                        .build()).toList())
                .setTotalElements(result.totalElements())
                .setPageNumber(result.pageNumber())
                .setPageSize(result.pageSize())
                .build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private Double parseOptionalPrice(double value) {
        return value > 0.0d ? value : null;
    }

    private Double parseOptionalRating(double value) {
        return value > 0.0d ? value : null;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }
}
