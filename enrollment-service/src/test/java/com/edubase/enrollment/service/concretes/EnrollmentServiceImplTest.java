package com.edubase.enrollment.service.concretes;

import com.edubase.enrollment.configuration.mapper.EnrollmentMapper;
import com.edubase.enrollment.dto.request.EnrollmentCreateRequest;
import com.edubase.enrollment.dto.response.CustomPageResponse;
import com.edubase.enrollment.dto.response.EnrollmentResponse;
import com.edubase.enrollment.entity.Enrollment;
import com.edubase.enrollment.entity.EnrollmentStatus;
import com.edubase.enrollment.exception.EnrollmentAlreadyExistsException;
import com.edubase.enrollment.exception.EnrollmentNotFoundException;
import com.edubase.enrollment.grpc.CourseGrpcClient;
import com.edubase.enrollment.grpc.UserGrpcClient;
import com.edubase.enrollment.repository.EnrollmentRepository;
import com.edubase.enrollment.security.AuthContext;
import com.edubase.enrollment.security.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.security.access.AccessDeniedException;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EnrollmentServiceImplTest {

    @Mock
    private EnrollmentRepository enrollmentRepository;

    @Mock
    private EnrollmentMapper enrollmentMapper;

    @Mock
    private UserGrpcClient userGrpcClient;

    @Mock
    private CourseGrpcClient courseGrpcClient;

    @Mock
    private ApplicationEventPublisher applicationEventPublisher;

    @InjectMocks
    private EnrollmentServiceImpl enrollmentService;

    @Test
    void createEnrollment_shouldSetUserIdAndActiveStatus() {
        AuthContext authContext = new AuthContext("10", UserRole.STUDENT);
        EnrollmentCreateRequest request = EnrollmentCreateRequest.builder()
                .courseId(" course-1 ")
                .build();

        Enrollment mapped = new Enrollment();
        EnrollmentResponse response = EnrollmentResponse.builder().build();
        response.setId(1L);

        when(enrollmentRepository.findByUserIdAndCourseId(10L, "course-1"))
                .thenReturn(Optional.empty());
        when(enrollmentMapper.toEntityFromRequest(request)).thenReturn(mapped);
        when(enrollmentRepository.save(any(Enrollment.class))).thenAnswer(inv -> inv.getArgument(0));
        when(enrollmentMapper.toResponseFromEntity(any(Enrollment.class))).thenReturn(response);

        EnrollmentResponse result = enrollmentService.createEnrollment(authContext, request);

        ArgumentCaptor<Enrollment> captor = ArgumentCaptor.forClass(Enrollment.class);
        verify(enrollmentRepository).save(captor.capture());

        Enrollment saved = captor.getValue();
        assertEquals(10L, saved.getUserId());
        assertEquals("course-1", saved.getCourseId());
        assertEquals(EnrollmentStatus.ACTIVE, saved.getStatus());
        assertNotNull(result);
    }

    @Test
    void createEnrollment_shouldReactivateCancelledEnrollment() {
        AuthContext authContext = new AuthContext("5", UserRole.STUDENT);
        EnrollmentCreateRequest request = EnrollmentCreateRequest.builder()
                .courseId("course-2")
                .build();

        Enrollment existing = Enrollment.builder()
                .userId(5L)
                .courseId("course-2")
                .status(EnrollmentStatus.CANCELLED)
                .build();
        EnrollmentResponse response = EnrollmentResponse.builder()
                .status(EnrollmentStatus.ACTIVE)
                .build();
        response.setId(2L);

        when(enrollmentRepository.findByUserIdAndCourseId(5L, "course-2"))
                .thenReturn(Optional.of(existing));
        when(enrollmentRepository.save(existing)).thenReturn(existing);
        when(enrollmentMapper.toResponseFromEntity(existing)).thenReturn(response);

        EnrollmentResponse result = enrollmentService.createEnrollment(authContext, request);

        assertEquals(EnrollmentStatus.ACTIVE, existing.getStatus());
        assertEquals(EnrollmentStatus.ACTIVE, result.getStatus());
    }

    @Test
    void createEnrollment_shouldThrowWhenDuplicateActive() {
        AuthContext authContext = new AuthContext("3", UserRole.STUDENT);
        EnrollmentCreateRequest request = EnrollmentCreateRequest.builder()
                .courseId("course-3")
                .build();

        Enrollment existing = Enrollment.builder()
                .userId(3L)
                .courseId("course-3")
                .status(EnrollmentStatus.ACTIVE)
                .build();

        when(enrollmentRepository.findByUserIdAndCourseId(3L, "course-3"))
                .thenReturn(Optional.of(existing));

        assertThrows(EnrollmentAlreadyExistsException.class,
                () -> enrollmentService.createEnrollment(authContext, request));
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void createEnrollment_shouldRejectDifferentUserWhenNotAdmin() {
        AuthContext authContext = new AuthContext("1", UserRole.STUDENT);
        EnrollmentCreateRequest request = EnrollmentCreateRequest.builder()
                .courseId("course-4")
                .userId(2L)
                .build();

        assertThrows(AccessDeniedException.class,
                () -> enrollmentService.createEnrollment(authContext, request));
        verify(enrollmentRepository, never()).save(any(Enrollment.class));
    }

    @Test
    void createEnrollment_shouldThrowWhenUniqueConstraintFails() {
        AuthContext authContext = new AuthContext("7", UserRole.ADMIN);
        EnrollmentCreateRequest request = EnrollmentCreateRequest.builder()
                .courseId("course-5")
                .userId(7L)
                .build();

        when(enrollmentRepository.findByUserIdAndCourseId(7L, "course-5"))
                .thenReturn(Optional.empty());
        when(enrollmentMapper.toEntityFromRequest(request)).thenReturn(new Enrollment());
        when(enrollmentRepository.save(any(Enrollment.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThrows(EnrollmentAlreadyExistsException.class,
                () -> enrollmentService.createEnrollment(authContext, request));
    }

    @Test
    void getMyEnrollments_shouldUseAuthUserId() {
        AuthContext authContext = new AuthContext("9", UserRole.STUDENT);
        Enrollment enrollment = Enrollment.builder()
                .userId(9L)
                .courseId("course-6")
                .status(EnrollmentStatus.ACTIVE)
                .build();
        Page<Enrollment> page = new PageImpl<>(List.of(enrollment), PageRequest.of(0, 10), 1);

        when(enrollmentRepository.findAllByUserId(eq(9L), any()))
                .thenReturn(page);
        EnrollmentResponse enrollmentResponse = EnrollmentResponse.builder().build();
        enrollmentResponse.setId(6L);
        when(enrollmentMapper.toResponseFromEntity(enrollment))
                .thenReturn(enrollmentResponse);

        CustomPageResponse<EnrollmentResponse> result = enrollmentService.getMyEnrollments(authContext, 0, 10);

        assertEquals(1, result.getContent().size());
        verify(enrollmentRepository).findAllByUserId(eq(9L), any());
    }

    @Test
    void cancelEnrollment_shouldSetCancelled() {
        AuthContext authContext = new AuthContext("4", UserRole.STUDENT);
        Enrollment enrollment = Enrollment.builder()
                .userId(4L)
                .courseId("course-7")
                .status(EnrollmentStatus.ACTIVE)
                .build();
        enrollment.setId(11L);

        when(enrollmentRepository.findById(11L)).thenReturn(Optional.of(enrollment));

        enrollmentService.cancelEnrollment(authContext, 11L);

        assertEquals(EnrollmentStatus.CANCELLED, enrollment.getStatus());
        verify(enrollmentRepository).save(enrollment);
    }

    @Test
    void getEnrollmentById_shouldThrowNotFound() {
        AuthContext authContext = new AuthContext("2", UserRole.ADMIN);
        when(enrollmentRepository.findById(99L)).thenReturn(Optional.empty());

        assertThrows(EnrollmentNotFoundException.class,
                () -> enrollmentService.getEnrollmentById(authContext, 99L));
    }
}
