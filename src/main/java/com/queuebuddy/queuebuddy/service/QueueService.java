package com.queuebuddy.queuebuddy.service;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.entity.*;
import com.queuebuddy.queuebuddy.exception.ApiException;
import com.queuebuddy.queuebuddy.repository.AppUserRepository;
import com.queuebuddy.queuebuddy.repository.ClinicRepository;
import com.queuebuddy.queuebuddy.repository.NotificationRepository;
import com.queuebuddy.queuebuddy.repository.QueueEntryRepository;
import com.queuebuddy.queuebuddy.util.Phones;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final long LATE_AFTER_MINUTES = 10;
    private static final List<QueueStatus> ACTIVE =
            List.of(QueueStatus.PENDING, QueueStatus.WAITING, QueueStatus.SERVING);

    private final QueueEntryRepository entries;
    private final NotificationRepository notifications;
    private final ClinicRepository clinics;
    private final AppUserRepository users;
    private final Clock clock;

    @Value("${app.queue.default-consult-minutes:10}")
    private int defaultConsultMinutes;

    private record Board(List<QueueEntry> pending, List<QueueEntry> waiting,
                         List<QueueEntry> serving, List<QueueEntry> done, int avg) {}

    // ================= staff =================

    @Transactional(readOnly = true)
    public QueueSnapshot snapshot(Long clinicId) {
        return buildSnapshot(clinicId);
    }

    @Transactional
    public QueueSnapshot accept(Long clinicId, Long entryId, Long deciderId) {
        lockClinic(clinicId);   // so two staff accepting at once never share a ticket number
        QueueEntry e = find(clinicId, entryId);
        requireStatus(e, QueueStatus.PENDING);

        e.setTicketNumber(entries.maxTicket(clinicId, e.getQueueDate()) + 1);
        e.setStatus(QueueStatus.WAITING);
        e.setDecidedAt(Instant.now(clock));
        e.setDecidedBy(users.getReferenceById(deciderId));
        record(e, NotificationType.REQUEST_ACCEPTED,
                "You're in! Your ticket number is #" + e.getTicketNumber() + ".");
        return buildSnapshot(clinicId);
    }

    @Transactional
    public QueueSnapshot reject(Long clinicId, Long entryId, Long deciderId, String reason) {
        QueueEntry e = find(clinicId, entryId);
        requireStatus(e, QueueStatus.PENDING);

        String cleaned = reason.trim();
        e.setStatus(QueueStatus.REJECTED);
        e.setRejectionReason(cleaned);
        e.setDecidedAt(Instant.now(clock));
        e.setDecidedBy(users.getReferenceById(deciderId));
        record(e, NotificationType.REQUEST_REJECTED, "Your request wasn't accepted: " + cleaned);
        return buildSnapshot(clinicId);
    }

    /** Calls the next waiting patient. Finishes whoever this person was already serving. */
    @Transactional
    public QueueSnapshot callNext(Long clinicId, Long callerId) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);

        QueueEntry next = entries
                .findFirstByClinicIdAndQueueDateAndStatusOrderByTicketNumberAsc(clinicId, today, QueueStatus.WAITING)
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT,
                        "QUEUE_EMPTY", "Nobody is waiting right now."));
        AppUser caller = users.findById(callerId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again."));

        for (QueueEntry current : entries.findByClinicIdAndQueueDateAndStatusAndServedById(
                clinicId, today, QueueStatus.SERVING, callerId)) {
            current.setStatus(QueueStatus.DONE);
            current.setCompletedAt(now);
        }

        next.setStatus(QueueStatus.SERVING);
        next.setCalledAt(now);
        next.setServedBy(caller);
        record(next, NotificationType.YOUR_TURN, "It's your turn. Please go in to see the doctor.");

        Optional<QueueEntry> newFirst = entries
                .findFirstByClinicIdAndQueueDateAndStatusOrderByTicketNumberAsc(clinicId, today, QueueStatus.WAITING);
        newFirst.ifPresent(f -> record(f, NotificationType.YOU_ARE_NEXT,
                "You're next in line. Please stay close to the consultation room."));

        return buildSnapshot(clinicId);
    }

    @Transactional
    public QueueSnapshot complete(Long clinicId, Long entryId) {
        QueueEntry e = find(clinicId, entryId);
        requireStatus(e, QueueStatus.SERVING);
        e.setStatus(QueueStatus.DONE);
        e.setCompletedAt(Instant.now(clock));
        return buildSnapshot(clinicId);
    }

    @Transactional
    public QueueSnapshot skip(Long clinicId, Long entryId) {
        QueueEntry e = find(clinicId, entryId);
        requireStatus(e, QueueStatus.WAITING, QueueStatus.SERVING);
        e.setStatus(QueueStatus.SKIPPED);
        e.setCompletedAt(Instant.now(clock));
        return buildSnapshot(clinicId);
    }

    @Transactional
    public QueueSnapshot cancel(Long clinicId, Long entryId) {
        QueueEntry e = find(clinicId, entryId);
        requireStatus(e, QueueStatus.WAITING);
        e.setStatus(QueueStatus.CANCELLED);
        e.setCompletedAt(Instant.now(clock));
        return buildSnapshot(clinicId);
    }

    /** Asks a waiting patient to come forward. Shows "Notified", then "Late" after 10 minutes. */
    @Transactional
    public QueueSnapshot notifyDesk(Long clinicId, Long entryId) {
        QueueEntry e = find(clinicId, entryId);
        requireStatus(e, QueueStatus.WAITING);
        e.setNotifiedAt(Instant.now(clock));
        record(e, NotificationType.DESK_REQUEST, "Please come to the front desk. The clinic is waiting for you.");
        return buildSnapshot(clinicId);
    }

    // ================= patient =================

    @Transactional(readOnly = true)
    public MyQueueResponse myQueue(Long clinicId, Long userId) {
        return buildMine(clinicId, userId);
    }

    @Transactional
    public MyQueueResponse requestToJoin(Long clinicId, Long userId, VisitType visitType) {
        lockClinic(clinicId);
        LocalDate today = LocalDate.now(clock);

        if (entries.existsByClinicIdAndQueueDateAndPatientIdAndStatusIn(clinicId, today, userId, ACTIVE)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_QUEUE",
                    "You already have an active place in the queue.");
        }
        AppUser patient = users.findById(userId).orElseThrow(() ->
                new ApiException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "Please sign in again."));

        QueueEntry e = new QueueEntry();
        e.setClinic(patient.getClinic());
        e.setPatient(patient);
        e.setPatientName(patient.getFullName());
        e.setPatientPhone(patient.getPhone());
        e.setVisitType(visitType);
        e.setStatus(QueueStatus.PENDING);
        e.setQueueDate(today);
        e.setJoinedAt(Instant.now(clock));
        entries.save(e);
        return buildMine(clinicId, userId);
    }

    @Transactional
    public MyQueueResponse cancelMine(Long clinicId, Long userId) {
        LocalDate today = LocalDate.now(clock);
        QueueEntry e = entries.findByClinicIdAndQueueDateAndPatientIdOrderByIdDesc(clinicId, today, userId)
                .stream()
                .filter(x -> x.getStatus() == QueueStatus.PENDING || x.getStatus() == QueueStatus.WAITING)
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_CANCEL",
                        "You don't have a place in the queue to cancel."));
        e.setStatus(QueueStatus.CANCELLED);
        e.setCompletedAt(Instant.now(clock));
        return buildMine(clinicId, userId);
    }

    // ================= helpers =================

    private Clinic lockClinic(Long clinicId) {
        return clinics.findByIdForUpdate(clinicId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CLINIC_NOT_FOUND", "Clinic not found."));
    }

    private QueueEntry find(Long clinicId, Long entryId) {
        return entries.findByIdAndClinicId(entryId, clinicId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND", "We couldn't find that request."));
    }

    private void requireStatus(QueueEntry e, QueueStatus... allowed) {
        for (QueueStatus s : allowed) {
            if (e.getStatus() == s) return;
        }
        throw new ApiException(HttpStatus.CONFLICT, "INVALID_STATE",
                "That patient's status just changed. Refresh and try again.");
    }

    private void record(QueueEntry e, NotificationType type, String message) {
        Notification n = new Notification();
        n.setQueueEntry(e);
        n.setUser(e.getPatient());
        n.setPhone(e.getPatientPhone());
        n.setType(type);
        n.setMessage(message);
        n.setCreatedAt(Instant.now(clock));
        notifications.save(n);
    }

    private Board loadBoard(Long clinicId, LocalDate today) {
        List<QueueEntry> all = entries.findByClinicIdAndQueueDateAndStatusInOrderByTicketNumberAsc(
                clinicId, today,
                List.of(QueueStatus.PENDING, QueueStatus.WAITING, QueueStatus.SERVING, QueueStatus.DONE));

        List<QueueEntry> pending = all.stream()
                .filter(e -> e.getStatus() == QueueStatus.PENDING)
                .sorted(Comparator.comparing(QueueEntry::getJoinedAt)).toList();
        List<QueueEntry> waiting = all.stream().filter(e -> e.getStatus() == QueueStatus.WAITING).toList();
        List<QueueEntry> serving = all.stream().filter(e -> e.getStatus() == QueueStatus.SERVING).toList();
        List<QueueEntry> done = all.stream().filter(e -> e.getStatus() == QueueStatus.DONE).toList();
        return new Board(pending, waiting, serving, done, avgConsultMinutes(done));
    }

    private QueueSnapshot buildSnapshot(Long clinicId) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        Board b = loadBoard(clinicId, today);

        List<QueueItem> requests = b.pending().stream().map(e -> toItem(e, null, b.avg(), now)).toList();
        List<QueueItem> waiting = IntStream.range(0, b.waiting().size())
                .mapToObj(i -> toItem(b.waiting().get(i), i + 1, b.avg(), now)).toList();
        List<QueueItem> serving = b.serving().stream().map(e -> toItem(e, null, b.avg(), now)).toList();

        long longest = waiting.stream().mapToLong(QueueItem::waitingMinutes).max().orElse(0);
        Clinic clinic = clinics.findById(clinicId).orElseThrow(() ->
                new ApiException(HttpStatus.NOT_FOUND, "CLINIC_NOT_FOUND", "Clinic not found."));

        return new QueueSnapshot(today.toString(), clinic.getName(), requests, waiting, serving,
                new QueueStats(waiting.size(), requests.size(), b.avg(), (int) longest, b.done().size()));
    }

    private MyQueueResponse buildMine(Long clinicId, Long userId) {
        LocalDate today = LocalDate.now(clock);
        Instant now = Instant.now(clock);
        Board b = loadBoard(clinicId, today);

        QueueItem item = null;
        List<QueueEntry> mine = entries.findByClinicIdAndQueueDateAndPatientIdOrderByIdDesc(clinicId, today, userId);
        if (!mine.isEmpty()) {
            QueueEntry e = mine.get(0);
            Integer position = null;
            if (e.getStatus() == QueueStatus.WAITING) {
                for (int i = 0; i < b.waiting().size(); i++) {
                    if (b.waiting().get(i).getId().equals(e.getId())) position = i + 1;
                }
            }
            item = toItem(e, position, b.avg(), now);
        }

        Integer nowServing = b.serving().stream()
                .max(Comparator.comparing(QueueEntry::getCalledAt))
                .map(QueueEntry::getTicketNumber)
                .orElse(null);

        List<NotificationItem> updates = notifications.findTop10ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(n -> new NotificationItem(n.getId(), n.getType(), n.getMessage(), n.getCreatedAt().toString()))
                .toList();

        String clinicName = clinics.findById(clinicId).map(Clinic::getName).orElse("your clinic");
        return new MyQueueResponse(clinicName, item, nowServing, b.waiting().size(), b.avg(), updates);
    }

    private int avgConsultMinutes(List<QueueEntry> done) {
        double seconds = done.stream()
                .filter(e -> e.getCalledAt() != null && e.getCompletedAt() != null)
                .mapToLong(e -> Duration.between(e.getCalledAt(), e.getCompletedAt()).toSeconds())
                .average()
                .orElse(defaultConsultMinutes * 60.0);
        return Math.max(1, (int) Math.round(seconds / 60.0));
    }

    private QueueItem toItem(QueueEntry e, Integer position, int avg, Instant now) {
        Instant from = e.getDecidedAt() != null ? e.getDecidedAt() : e.getJoinedAt();
        Instant until = e.getStatus() == QueueStatus.SERVING && e.getCalledAt() != null ? e.getCalledAt() : now;
        long waited = Math.max(0, Duration.between(from, until).toMinutes());

        return new QueueItem(
                e.getId(),
                e.getTicketNumber(),
                e.getPatientName(),
                Phones.mask(e.getPatientPhone()),
                e.getVisitType(),
                e.getStatus(),
                displayStatus(e, position, now),
                waited,
                position == null ? null : position * avg,
                position,
                e.getJoinedAt().toString(),
                e.getCalledAt() == null ? null : e.getCalledAt().toString(),
                e.getServedBy() == null ? null : e.getServedBy().getFullName(),
                e.getRejectionReason());
    }

    private String displayStatus(QueueEntry e, Integer position, Instant now) {
        return switch (e.getStatus()) {
            case WAITING -> {
                if (e.getNotifiedAt() != null
                        && Duration.between(e.getNotifiedAt(), now).toMinutes() >= LATE_AFTER_MINUTES) yield "LATE";
                if (position != null && position == 1) yield "NEXT";
                if (e.getNotifiedAt() != null) yield "NOTIFIED";
                yield "WAITING";
            }
            default -> e.getStatus().name();   // PENDING, SERVING, DONE, SKIPPED, CANCELLED, REJECTED
        };
    }
}