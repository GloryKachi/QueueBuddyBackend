package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.service.Actor;
import com.queuebuddy.queuebuddy.service.QueueService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/queue")
@RequiredArgsConstructor
public class QueueController {

    private final QueueService queue;

    @GetMapping
    public QueueSnapshot get(@AuthenticationPrincipal Jwt jwt) {
        return queue.snapshot(Actor.from(jwt));
    }

    @PostMapping("/entries/{id}/accept")
    public QueueSnapshot accept(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                @RequestBody(required = false) AcceptRequest body) {
        return queue.accept(Actor.from(jwt), id, body == null ? null : body.doctorId());
    }

    @PostMapping("/entries/{id}/reject")
    public QueueSnapshot reject(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                @Valid @RequestBody RejectRequest body) {
        return queue.reject(Actor.from(jwt), id, body.reason());
    }

    @PostMapping("/entries/{id}/assign")
    public QueueSnapshot assign(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id,
                                @Valid @RequestBody AssignRequest body) {
        return queue.assign(Actor.from(jwt), id, body.doctorId());
    }

    @PostMapping("/entries/{id}/complete")
    public QueueSnapshot complete(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.complete(Actor.from(jwt), id);
    }

    @PostMapping("/entries/{id}/skip")
    public QueueSnapshot skip(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.skip(Actor.from(jwt), id);
    }

    @PostMapping("/entries/{id}/cancel")
    public QueueSnapshot cancel(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.cancel(Actor.from(jwt), id);
    }

    @PostMapping("/entries/{id}/notify")
    public QueueSnapshot notifyPatient(@AuthenticationPrincipal Jwt jwt, @PathVariable Long id) {
        return queue.notifyDesk(Actor.from(jwt), id);
    }

    @PostMapping("/call-next")
    public QueueSnapshot callNext(@AuthenticationPrincipal Jwt jwt,
                                  @RequestParam(required = false) Long doctorId) {
        return queue.callNext(Actor.from(jwt), doctorId);
    }
}