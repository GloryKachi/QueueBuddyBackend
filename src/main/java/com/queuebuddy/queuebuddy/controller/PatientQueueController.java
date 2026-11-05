package com.queuebuddy.queuebuddy.controller;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.service.Actor;
import com.queuebuddy.queuebuddy.service.QueueService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/me/queue")
@RequiredArgsConstructor
public class PatientQueueController {

    private final QueueService queue;

    @GetMapping
    public MyQueueResponse get(@AuthenticationPrincipal Jwt jwt) {
        return queue.myQueue(Actor.from(jwt));
    }

    @PostMapping("/request")
    public MyQueueResponse request(@AuthenticationPrincipal Jwt jwt,
                                   @RequestBody(required = false) JoinQueueRequest body) {
        return queue.requestToJoin(Actor.from(jwt), body == null ? null : body.doctorId());
    }

    @PostMapping("/cancel")
    public MyQueueResponse cancel(@AuthenticationPrincipal Jwt jwt) {
        return queue.cancelMine(Actor.from(jwt));
    }
}