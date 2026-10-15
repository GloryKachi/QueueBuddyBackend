package com.queuebuddy.queuebuddy.repository;

import com.queuebuddy.queuebuddy.entity.Clinic;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface ClinicRepository extends JpaRepository<Clinic, Long> {
    boolean existsByJoinCode(String joinCode);
    boolean existsByWhatsappNumber(String whatsappNumber);
    Optional<Clinic> findByJoinCode(String joinCode);
}
