package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.entity.AppUser;
import com.queuebuddy.queuebuddy.repository.AppUserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/doctor/duty")
@RequiredArgsConstructor
public class DutyController {

    private final AppUserRepository users;

    public record DutyRequest(boolean onDuty) {}
    public record DutyResponse(boolean onDuty) {}

    @GetMapping
    public DutyResponse get(@AuthenticationPrincipal Jwt jwt) {
        return new DutyResponse(users.findById(Long.valueOf(jwt.getSubject()))
                .map(AppUser::isOnDuty).orElse(false));
    }

    @PutMapping
    @Transactional
    public DutyResponse set(@AuthenticationPrincipal Jwt jwt, @RequestBody DutyRequest body) {
        AppUser doctor = users.findById(Long.valueOf(jwt.getSubject())).orElseThrow();
        doctor.setOnDuty(body.onDuty());
        users.save(doctor);
        return new DutyResponse(doctor.isOnDuty());
    }
}