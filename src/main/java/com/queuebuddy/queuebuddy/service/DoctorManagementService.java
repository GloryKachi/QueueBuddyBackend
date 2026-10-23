package com.queuebuddy.queuebuddy.service;

import com.queuebuddy.queuebuddy.dto.DoctorSummary;
import com.queuebuddy.queuebuddy.entity.AccountStatus;
import com.queuebuddy.queuebuddy.entity.AppUser;
import com.queuebuddy.queuebuddy.entity.Role;
import com.queuebuddy.queuebuddy.exception.ApiException;
import com.queuebuddy.queuebuddy.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class DoctorManagementService {

    private final AppUserRepository users;

    @Transactional(readOnly = true)
    public List<DoctorSummary> list(Long clinicId) {
        return users.findByClinicIdAndRoleOrderByCreatedAtDesc(clinicId, Role.DOCTOR)
                .stream().map(DoctorSummary::from).toList();
    }

    // PENDING_APPROVAL or SUSPENDED -> ACTIVE
    @Transactional
    public DoctorSummary approve(Long clinicId, Long doctorId) {
        AppUser doctor = find(clinicId, doctorId);
        if (doctor.getStatus() == AccountStatus.PENDING_VERIFICATION) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCTOR_NOT_VERIFIED",
                    "This doctor hasn't verified their email yet.");
        }
        doctor.setStatus(AccountStatus.ACTIVE);
        return DoctorSummary.from(users.save(doctor));
    }

    // any status -> SUSPENDED (this is also what "Decline" does)
    @Transactional
    public DoctorSummary suspend(Long clinicId, Long doctorId) {
        AppUser doctor = find(clinicId, doctorId);
        doctor.setStatus(AccountStatus.SUSPENDED);
        return DoctorSummary.from(users.save(doctor));
    }

    // Looks up by clinic too, so an admin can never touch another clinic's doctors
    private AppUser find(Long clinicId, Long doctorId) {
        return users.findByIdAndClinicIdAndRole(doctorId, clinicId, Role.DOCTOR)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND,
                        "DOCTOR_NOT_FOUND", "We couldn't find that doctor."));
    }
}