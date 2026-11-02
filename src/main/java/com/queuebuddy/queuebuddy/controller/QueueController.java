package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.QueueSnapshot;
import com.queuebuddy.queuebuddy.dto.RejectRequest;
import com.queuebuddy.queuebuddy.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import static com.queuebuddy.queuebuddy.util.JwtClaims.clinicId;
import static com.queuebuddy.queuebuddy.util.JwtClaims.userId;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queue;

    @GetMapping
    public QueueSnapshot snapshot(@AuthenticationPrincipal Jwt jwt) {
        return queue.snapshot(clinicId(jwt));
    }

    @PostMapping("/entries/{id}/accept")
    public QueueSnapshot accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.accept(clinicId(jwt), id, userId(jwt));
    }

    @PostMapping("/entries/{id}/reject")
    public QueueSnapshot reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                @Valid @RequestBody RejectRequest request) {
        return queue.reject(clinicId(jwt), id, userId(jwt), request.reason());
    }

    @PostMapping("/call-next")
    public QueueSnapshot callNext(@AuthenticationPrincipal Jwt jwt) {
        return queue.callNext(clinicId(jwt), userId(jwt));
    }

    @PostMapping("/entries/{id}/complete")
    public QueueSnapshot complete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.complete(clinicId(jwt), id);
    }

    @PostMapping("/entries/{id}/skip")
    public QueueSnapshot skip(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.skip(clinicId(jwt), id);
    }

    @PostMapping("/entries/{id}/cancel")
    public QueueSnapshot cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.cancel(clinicId(jwt), id);
    }

    @PostMapping("/entries/{id}/notify")
    public QueueSnapshot notifyPatient(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.notifyDesk(clinicId(jwt), id);
    }
}