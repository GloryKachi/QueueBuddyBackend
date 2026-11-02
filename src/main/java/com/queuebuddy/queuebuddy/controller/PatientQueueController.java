package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.JoinQueueRequest;
import com.queuebuddy.queuebuddy.dto.MyQueueResponse;
import com.queuebuddy.queuebuddy.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import static com.queuebuddy.queuebuddy.util.JwtClaims.clinicId;
import static com.queuebuddy.queuebuddy.util.JwtClaims.userId;

@RestController
@RequestMapping("/api/me/queue")
@RequiredArgsConstructor
public class PatientQueueController {

    private final QueueService queue;

    @GetMapping
    public MyQueueResponse mine(@AuthenticationPrincipal Jwt jwt) {
        return queue.myQueue(clinicId(jwt), userId(jwt));
    }

    @PostMapping("/request")
    public MyQueueResponse request(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody JoinQueueRequest request) {
        return queue.requestToJoin(clinicId(jwt), userId(jwt), request.visitType());
    }

    @PostMapping("/cancel")
    public MyQueueResponse cancel(@AuthenticationPrincipal Jwt jwt) {
        return queue.cancelMine(clinicId(jwt), userId(jwt));
    }
}