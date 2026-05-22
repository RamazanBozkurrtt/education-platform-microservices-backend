package com.edubase.payment.repository;

import com.edubase.payment.entity.Payment;
import com.edubase.payment.entity.PaymentStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    Page<Payment> findAllByUserId(Long userId, Pageable pageable);

    Page<Payment> findAllByCourseId(String courseId, Pageable pageable);

    boolean existsByIdAndUserId(Long id, Long userId);

    boolean existsByUserIdAndCourseIdAndStatus(Long userId, String courseId, PaymentStatus status);

    Optional<Payment> findFirstByUserIdAndCourseIdAndStatusOrderByCreatedAtDesc(Long userId, String courseId, PaymentStatus status);

    Optional<Payment> findByUserIdAndCourseIdAndIdempotencyKey(Long userId, String courseId, String idempotencyKey);
}
