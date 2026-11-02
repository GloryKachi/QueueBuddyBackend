package com.queuebuddy.queuebuddy.repository;

import com.queuebuddy.queuebuddy.entity.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;




public interface ClinicRepository extends JpaRepository<Clinic, Long> {
    boolean existsByJoinCode(String joinCode);

    boolean existsByWhatsappNumber(String whatsappNumber);

    Optional<Clinic> findByJoinCode(String joinCode);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select c from Clinic c where c.id = :id")
    Optional<Clinic> findByIdForUpdate(@Param("id") Long id);
}