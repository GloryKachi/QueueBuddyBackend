package com.queuebuddy.queuebuddy.repository;

import com.queuebuddy.queuebuddy.entity.AppUser;
import com.queuebuddy.queuebuddy.entity.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;
import java.util.List;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {
    boolean existsByEmail(String email);
    boolean existsByPhone(String phone);
    boolean existsByLicenseNumber(String licenseNumber);
    Optional<AppUser> findByEmail(String email);
    List<AppUser> findByClinicIdAndRoleOrderByCreatedAtDesc(Long clinicId, Role role);
    Optional<AppUser> findByIdAndClinicIdAndRole(Long id, Long clinicId, Role role);
}
