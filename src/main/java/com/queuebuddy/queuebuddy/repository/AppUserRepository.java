package com.queuebuddy.queuebuddy.repository;

import com.queuebuddy.queuebuddy.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByLicenseNumber(String licenseNumber);
    Optional<AppUser> findByEmail(String email);
}
