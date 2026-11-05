package com.queuebuddy.queuebuddy.service;

import com.queuebuddy.queuebuddy.dto.*;
import com.queuebuddy.queuebuddy.entity.*;
import com.queuebuddy.queuebuddy.exception.ApiException;
import com.queuebuddy.queuebuddy.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;

@Service
@RequiredArgsConstructor
public class QueueService {

    private static final List<QueueStatus> OPEN =
            List.of(QueueStatus.PENDING, QueueStatus.WAITING, QueueStatus.SERVING);
    private static final long DEFAULT_CONSULT_MINUTES = 10;
    private static final Duration LATE_AFTER = Duration.ofMinutes(10);

    private final QueueEntryRepository entries;
    private final NotificationRepository notifications;
    private final ClinicRepository clinics;
    private final AppUserRepository users;
    private final Clock clock;
    private final ApplicationEventPublisher events;

    // =====================================================================
    // Staff (admin + doctor)
    // =====================================================================

    @Transactional(readOnly = true)
    public QueueSnapshot snapshot(Actor actor) {
        return buildSnapshot(actor);
    }

    /** Accept a join request. Admins choose the doctor; a doctor accepting takes the patient themselves. */
    @Transactional
    public QueueSnapshot accept(Actor actor, Long entryId, Long doctorId) {
        clinics.findByIdForUpdate(actor.clinicId());            // serialise ticket numbering
        QueueEntry e = loadEntry(actor, entryId);
        if (e.getStatus() != QueueStatus.PENDING) {
            throw invalidState("Only pending requests can be accepted.");
        }

        AppUser doctor = actor.isDoctor()
                ? loadDoctor(actor.clinicId(), actor.id())
                : loadDoctor(actor.clinicId(), requireDoctorId(doctorId, e));

        e.setTicketNumber(entries.maxTicket(actor.clinicId(), today()) + 1);
        e.setStatus(QueueStatus.WAITING);
        e.setDoctor(doctor);
        e.setDecidedBy(users.getReferenceById(actor.id()));
        e.setDecidedAt(now());
        entries.save(e);

        long ahead = aheadOf(e);
        tell(e, NotificationType.REQUEST_ACCEPTED, String.format(
                "Hi %s, your request at %s was accepted. Your number is #%d and you'll see %s. People ahead of you: %d.",
                firstName(e.getPatientName()), e.getClinic().getName(), e.getTicketNumber(),
                doctorLabel(doctor.getFullName()), ahead));
        return buildSnapshot(actor);
    }

    @Transactional
    public QueueSnapshot reject(Actor actor, Long entryId, String reason) {
        QueueEntry e = loadEntry(actor, entryId);
        if (e.getStatus() != QueueStatus.PENDING) {
            throw invalidState("Only pending requests can be declined.");
        }
        String text = reason == null ? "" : reason.trim();
        if (text.length() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "VALIDATION_FAILED", "Please give a short reason.");
        }
        e.setStatus(QueueStatus.REJECTED);
        e.setRejectionReason(text);
        e.setDecidedBy(users.getReferenceById(actor.id()));
        e.setDecidedAt(now());
        entries.save(e);

        tell(e, NotificationType.REQUEST_REJECTED, String.format(
                "Hi %s, your request at %s wasn't accepted: %s",
                firstName(e.getPatientName()), e.getClinic().getName(), text));
        return buildSnapshot(actor);
    }

    /** Admin moves a waiting patient to another doctor. */
    @Transactional
    public QueueSnapshot assign(Actor actor, Long entryId, Long doctorId) {
        if (!actor.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, "FORBIDDEN", "Only the admin can reassign patients.");
        }
        QueueEntry e = loadEntry(actor, entryId);
        if (e.getStatus() != QueueStatus.WAITING) {
            throw invalidState("Only waiting patients can be reassigned.");
        }
        AppUser previous = e.getDoctor();
        AppUser doctor = loadDoctor(actor.clinicId(), doctorId);
        if (previous != null && previous.getId().equals(doctor.getId())) {
            return buildSnapshot(actor);
        }
        e.setDoctor(doctor);
        entries.save(e);

        tell(e, NotificationType.REQUEST_ACCEPTED, String.format(
                "Hi %s, you've been moved to %s at %s. Your number is still #%d.",
                firstName(e.getPatientName()), doctorLabel(doctor.getFullName()),
                e.getClinic().getName(), e.getTicketNumber()));
        notifyNextInLine(actor.clinicId(), previous);
        notifyNextInLine(actor.clinicId(), doctor);
        return buildSnapshot(actor);
    }

    /** Finish the doctor's current patient and call their next one. */
    @Transactional
    public QueueSnapshot callNext(Actor actor, Long doctorIdParam) {
        Long doctorId = actor.isDoctor() ? actor.id() : doctorIdParam;
        if (doctorId == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCTOR_REQUIRED",
                    "Choose which doctor's queue to advance.");
        }
        AppUser doctor = loadDoctor(actor.clinicId(), doctorId);
        List<QueueEntry> board = loadBoard(actor.clinicId());

        QueueEntry next = board.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING && sameDoctor(e, doctorId))
                .min(Comparator.comparing(QueueEntry::getTicketNumber))
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "QUEUE_EMPTY", "Nobody is waiting."));

        Instant now = now();
        board.stream()
                .filter(e -> e.getStatus() == QueueStatus.SERVING && sameDoctor(e, doctorId))
                .forEach(e -> {
                    e.setStatus(QueueStatus.DONE);
                    e.setCompletedAt(now);
                    entries.save(e);
                });

        next.setStatus(QueueStatus.SERVING);
        next.setCalledAt(now);
        next.setServedBy(doctor);
        entries.save(next);

        tell(next, NotificationType.YOUR_TURN, String.format(
                "It's your turn, %s! Please go to %s now.",
                firstName(next.getPatientName()), doctorLabel(doctor.getFullName())));
        notifyNextInLine(actor.clinicId(), doctor);
        return buildSnapshot(actor);
    }

    @Transactional
    public QueueSnapshot complete(Actor actor, Long entryId) {
        QueueEntry e = loadEntry(actor, entryId);
        if (e.getStatus() != QueueStatus.SERVING) {
            throw invalidState("Only the patient being served can be marked done.");
        }
        e.setStatus(QueueStatus.DONE);
        e.setCompletedAt(now());
        entries.save(e);
        notifyNextInLine(actor.clinicId(), e.getDoctor());
        return buildSnapshot(actor);
    }

    @Transactional
    public QueueSnapshot skip(Actor actor, Long entryId) {
        QueueEntry e = loadEntry(actor, entryId);
        if (e.getStatus() != QueueStatus.WAITING && e.getStatus() != QueueStatus.SERVING) {
            throw invalidState("That patient isn't in the line.");
        }
        e.setStatus(QueueStatus.SKIPPED);
        e.setCompletedAt(now());
        entries.save(e);
        notifyNextInLine(actor.clinicId(), e.getDoctor());
        return buildSnapshot(actor);
    }

    @Transactional
    public QueueSnapshot cancel(Actor actor, Long entryId) {
        QueueEntry e = loadEntry(actor, entryId);
        if (!OPEN.contains(e.getStatus())) {
            throw invalidState("That entry is already closed.");
        }
        e.setStatus(QueueStatus.CANCELLED);
        e.setCompletedAt(now());
        entries.save(e);
        notifyNextInLine(actor.clinicId(), e.getDoctor());
        return buildSnapshot(actor);
    }

    /** Front desk nudge: "please come to the desk". Turns LATE after 10 minutes. */
    @Transactional
    public QueueSnapshot notifyDesk(Actor actor, Long entryId) {
        QueueEntry e = loadEntry(actor, entryId);
        if (e.getStatus() != QueueStatus.WAITING) {
            throw invalidState("Only waiting patients can be notified.");
        }
        e.setNotifiedAt(now());
        entries.save(e);
        tell(e, NotificationType.DESK_REQUEST, String.format(
                "Hi %s, the front desk at %s is asking for you. Please come to the desk.",
                firstName(e.getPatientName()), e.getClinic().getName()));
        return buildSnapshot(actor);
    }

    // =====================================================================
    // Patient
    // =====================================================================

    @Transactional(readOnly = true)
    public MyQueueResponse myQueue(Actor actor) {
        return buildMine(actor);
    }

    @Transactional
    public MyQueueResponse requestToJoin(Actor actor, Long doctorId) {
        clinics.findByIdForUpdate(actor.clinicId());
        if (entries.existsByClinicIdAndQueueDateAndPatientIdAndStatusIn(
                actor.clinicId(), today(), actor.id(), OPEN)) {
            throw new ApiException(HttpStatus.CONFLICT, "ALREADY_IN_QUEUE",
                    "You already have an active request or place today.");
        }
        AppUser patient = users.findById(actor.id())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND", "Account not found."));

        AppUser preferred = null;
        if (doctorId != null) {
            preferred = loadDoctor(actor.clinicId(), doctorId);
            if (!preferred.isOnDuty()) {
                throw new ApiException(HttpStatus.CONFLICT, "DOCTOR_UNAVAILABLE",
                        "That doctor isn't available right now.");
            }
        }

        QueueEntry e = new QueueEntry();
        e.setClinic(patient.getClinic());
        e.setPatient(patient);
        e.setPatientName(patient.getFullName());
        e.setPatientPhone(patient.getPhone());
        e.setStatus(QueueStatus.PENDING);
        e.setQueueDate(today());
        e.setJoinedAt(now());
        e.setPreferredDoctor(preferred);
        entries.save(e);
        return buildMine(actor);
    }

    @Transactional
    public MyQueueResponse cancelMine(Actor actor) {
        QueueEntry e = entries
                .findByClinicIdAndQueueDateAndPatientIdOrderByIdDesc(actor.clinicId(), today(), actor.id())
                .stream()
                .filter(x -> OPEN.contains(x.getStatus()))
                .findFirst()
                .orElseThrow(() -> new ApiException(HttpStatus.CONFLICT, "NOTHING_TO_CANCEL",
                        "You're not in the queue."));
        e.setStatus(QueueStatus.CANCELLED);
        e.setCompletedAt(now());
        entries.save(e);
        notifyNextInLine(actor.clinicId(), e.getDoctor());
        return buildMine(actor);
    }

    // =====================================================================
    // Notifications
    // =====================================================================

    /** Stores a message for the patient and queues it for WhatsApp after the transaction commits. */
    private void tell(QueueEntry e, NotificationType type, String message) {
        Notification n = new Notification();
        n.setQueueEntry(e);
        n.setUser(e.getPatient());
        n.setPhone(e.getPatientPhone());
        n.setType(type);
        n.setMessage(message.length() > 500 ? message.substring(0, 500) : message);
        n.setCreatedAt(now());
        notifications.save(n);
        events.publishEvent(new PatientMessageEvent(e.getPatientPhone(), message));
    }

    /** Tells the first waiting patient of this doctor that they're next (once). */
    private void notifyNextInLine(Long clinicId, AppUser doctor) {
        if (doctor == null) return;
        Long doctorId = doctor.getId();
        entries.findByClinicIdAndQueueDateAndStatusInOrderByTicketNumberAsc(
                        clinicId, today(), List.of(QueueStatus.WAITING))
                .stream()
                .filter(e -> sameDoctor(e, doctorId))
                .min(Comparator.comparing(QueueEntry::getTicketNumber))
                .ifPresent(next -> {
                    if (!notifications.existsByQueueEntryIdAndType(next.getId(), NotificationType.YOU_ARE_NEXT)) {
                        tell(next, NotificationType.YOU_ARE_NEXT, String.format(
                                "Hi %s, you're next at %s. Please be ready — %s will call you shortly.",
                                firstName(next.getPatientName()), next.getClinic().getName(),
                                doctorLabel(doctor.getFullName())));
                    }
                });
    }

    // =====================================================================
    // Builders
    // =====================================================================

    private QueueSnapshot buildSnapshot(Actor actor) {
        Long clinicId = actor.clinicId();
        List<QueueEntry> board = loadBoard(clinicId);
        List<QueueEntry> done = entries.findByClinicIdAndQueueDateAndStatusInOrderByTicketNumberAsc(
                clinicId, today(), List.of(QueueStatus.DONE));
        long avg = avgConsultMinutes(done);
        Instant now = now();

        List<QueueEntry> visible = board.stream().filter(e -> canSee(actor, e)).toList();

        List<QueueItem> requests = visible.stream()
                .filter(e -> e.getStatus() == QueueStatus.PENDING)
                .sorted(Comparator.comparing(QueueEntry::getJoinedAt))
                .map(e -> toItem(e, board, avg, now)).toList();
        List<QueueItem> waiting = visible.stream()
                .filter(e -> e.getStatus() == QueueStatus.WAITING)
                .sorted(Comparator.comparing(QueueEntry::getTicketNumber))
                .map(e -> toItem(e, board, avg, now)).toList();
        List<QueueItem> serving = visible.stream()
                .filter(e -> e.getStatus() == QueueStatus.SERVING)
                .sorted(Comparator.comparing(QueueEntry::getCalledAt,
                        Comparator.nullsLast(Comparator.naturalOrder())))
                .map(e -> toItem(e, board, avg, now)).toList();

        long longest = waiting.stream().mapToLong(QueueItem::waitingMinutes).max().orElse(0);
        long servedToday = done.stream().filter(e -> canSee(actor, e)).count();
        QueueStats stats = new QueueStats(waiting.size(), requests.size(), avg, longest, servedToday);

        Boolean onDuty = actor.isDoctor()
                ? users.findById(actor.id()).map(AppUser::isOnDuty).orElse(false)
                : null;
        String clinicName = clinics.findById(clinicId).map(Clinic::getName).orElse("");

        return new QueueSnapshot(today(), clinicName, actor.isDoctor() ? "DOCTOR" : "ADMIN", onDuty,
                requests, waiting, serving, stats, doctorOptions(clinicId, board));
    }

    private MyQueueResponse buildMine(Actor actor) {
        Long clinicId = actor.clinicId();
        List<QueueEntry> board = loadBoard(clinicId);
        List<QueueEntry> done = entries.findByClinicIdAndQueueDateAndStatusInOrderByTicketNumberAsc(
                clinicId, today(), List.of(QueueStatus.DONE));
        long avg = avgConsultMinutes(done);
        Instant now = now();

        QueueEntry latest = entries
                .findByClinicIdAndQueueDateAndPatientIdOrderByIdDesc(clinicId, today(), actor.id())
                .stream().findFirst().orElse(null);
        QueueItem item = latest == null ? null : toItem(latest, board, avg, now);

        boolean active = latest != null && OPEN.contains(latest.getStatus());
        Long doctorId = latest != null && latest.getDoctor() != null ? latest.getDoctor().getId() : null;

        Integer nowServing = null;
        long waitingCount;
        if (active && doctorId != null) {
            nowServing = board.stream()
                    .filter(e -> e.getStatus() == QueueStatus.SERVING && sameDoctor(e, doctorId))
                    .map(QueueEntry::getTicketNumber).findFirst().orElse(null);
            waitingCount = board.stream()
                    .filter(e -> e.getStatus() == QueueStatus.WAITING && sameDoctor(e, doctorId)).count();
        } else {
            waitingCount = board.stream().filter(e -> e.getStatus() == QueueStatus.WAITING).count();
        }

        List<NotificationItem> updates = notifications.findTop10ByUserIdOrderByCreatedAtDesc(actor.id())
                .stream()
                .map(n -> new NotificationItem(n.getId(), n.getType().name(), n.getMessage(), n.getCreatedAt()))
                .toList();

        String patientName = users.findById(actor.id()).map(AppUser::getFullName).orElse("");
        String clinicName = clinics.findById(clinicId).map(Clinic::getName).orElse("");

        return new MyQueueResponse(patientName, clinicName, item, nowServing, waitingCount, avg,
                updates, doctorOptions(clinicId, board));
    }

    private List<DoctorOption> doctorOptions(Long clinicId, List<QueueEntry> board) {
        return users.findByClinicIdAndRoleOrderByCreatedAtDesc(clinicId, Role.DOCTOR).stream()
                .filter(u -> u.getStatus() == AccountStatus.ACTIVE)
                .map(u -> new DoctorOption(u.getId(), u.getFullName(), u.getSpecialization(), u.isOnDuty(),
                        board.stream()
                                .filter(e -> e.getStatus() == QueueStatus.WAITING && sameDoctor(e, u.getId()))
                                .count()))
                .sorted((a, b) -> Boolean.compare(b.onDuty(), a.onDuty()))
                .toList();
    }

    private QueueItem toItem(QueueEntry e, List<QueueEntry> board, long avg, Instant now) {
        int position = 0;
        long estimate = 0;
        if (e.getStatus() == QueueStatus.WAITING && e.getDoctor() != null) {
            Long docId = e.getDoctor().getId();
            long ahead = board.stream()
                    .filter(o -> o.getStatus() == QueueStatus.WAITING && sameDoctor(o, docId)
                            && o.getTicketNumber() < e.getTicketNumber())
                    .count();
            boolean busy = board.stream()
                    .anyMatch(o -> o.getStatus() == QueueStatus.SERVING && sameDoctor(o, docId));
            position = (int) ahead + 1;
            estimate = (ahead + (busy ? 1 : 0)) * avg;
        }

        Instant since = switch (e.getStatus()) {
            case PENDING -> e.getJoinedAt();
            case WAITING -> e.getDecidedAt() != null ? e.getDecidedAt() : e.getJoinedAt();
            case SERVING -> e.getCalledAt();
            default -> null;
        };
        long waited = since == null ? 0 : Math.max(0, Duration.between(since, now).toMinutes());

        AppUser doctor = e.getDoctor();
        AppUser preferred = e.getPreferredDoctor();
        return new QueueItem(
                e.getId(), e.getTicketNumber(), e.getPatientName(), mask(e.getPatientPhone()),
                e.getStatus(), displayStatus(e, position, now),
                waited, estimate, position,
                e.getJoinedAt(), e.getCalledAt(),
                e.getServedBy() == null ? null : e.getServedBy().getFullName(),
                e.getRejectionReason(),
                doctor == null ? null : doctor.getId(), doctor == null ? null : doctor.getFullName(),
                preferred == null ? null : preferred.getId(), preferred == null ? null : preferred.getFullName());
    }

    private String displayStatus(QueueEntry e, int position, Instant now) {
        if (e.getStatus() != QueueStatus.WAITING) return e.getStatus().name();
        if (e.getNotifiedAt() != null) {
            return Duration.between(e.getNotifiedAt(), now).compareTo(LATE_AFTER) >= 0 ? "LATE" : "NOTIFIED";
        }
        return position == 1 ? "NEXT" : "WAITING";
    }

    // =====================================================================
    // Helpers
    // =====================================================================

    private List<QueueEntry> loadBoard(Long clinicId) {
        return entries.findByClinicIdAndQueueDateAndStatusInOrderByTicketNumberAsc(clinicId, today(), OPEN);
    }

    private QueueEntry loadEntry(Actor actor, Long id) {
        QueueEntry e = entries.findByIdAndClinicId(id, actor.clinicId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "ENTRY_NOT_FOUND",
                        "That queue entry was not found."));
        if (actor.isDoctor() && e.getDoctor() != null && !e.getDoctor().getId().equals(actor.id())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "NOT_YOUR_PATIENT",
                    "This patient is assigned to another doctor.");
        }
        return e;
    }

    private AppUser loadDoctor(Long clinicId, Long doctorId) {
        AppUser doctor = users.findByIdAndClinicIdAndRole(doctorId, clinicId, Role.DOCTOR)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "DOCTOR_NOT_FOUND", "Doctor not found."));
        if (doctor.getStatus() != AccountStatus.ACTIVE) {
            throw new ApiException(HttpStatus.CONFLICT, "DOCTOR_NOT_VERIFIED", "That doctor isn't active.");
        }
        return doctor;
    }

    private Long requireDoctorId(Long doctorId, QueueEntry e) {
        Long id = doctorId != null ? doctorId
                : (e.getPreferredDoctor() != null ? e.getPreferredDoctor().getId() : null);
        if (id == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "DOCTOR_REQUIRED", "Choose a doctor for this patient.");
        }
        return id;
    }

    private boolean canSee(Actor actor, QueueEntry e) {
        if (!actor.isDoctor()) return true;
        if (e.getStatus() == QueueStatus.PENDING) {
            return e.getPreferredDoctor() == null || e.getPreferredDoctor().getId().equals(actor.id());
        }
        return sameDoctor(e, actor.id());
    }

    private static boolean sameDoctor(QueueEntry e, Long doctorId) {
        return e.getDoctor() != null && e.getDoctor().getId().equals(doctorId);
    }

    private long aheadOf(QueueEntry e) {
        Long docId = e.getDoctor().getId();
        List<QueueEntry> board = loadBoard(e.getClinic().getId());
        long waitingAhead = board.stream()
                .filter(o -> o.getStatus() == QueueStatus.WAITING && sameDoctor(o, docId)
                        && o.getTicketNumber() < e.getTicketNumber())
                .count();
        boolean busy = board.stream()
                .anyMatch(o -> o.getStatus() == QueueStatus.SERVING && sameDoctor(o, docId));
        return waitingAhead + (busy ? 1 : 0);
    }

    private long avgConsultMinutes(List<QueueEntry> done) {
        double avg = done.stream()
                .filter(e -> e.getCalledAt() != null && e.getCompletedAt() != null)
                .mapToLong(e -> Math.max(0, Duration.between(e.getCalledAt(), e.getCompletedAt()).toMinutes()))
                .average().orElse(DEFAULT_CONSULT_MINUTES);
        return Math.max(1, Math.round(avg));
    }

    private ApiException invalidState(String message) {
        return new ApiException(HttpStatus.CONFLICT, "INVALID_STATE", message);
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() <= 7) return "***";
        return phone.substring(0, 4) + "****" + phone.substring(phone.length() - 3);
    }

    private static String firstName(String full) {
        return full == null || full.isBlank() ? "there" : full.trim().split("\\s+")[0];
    }

    private static String doctorLabel(String name) {
        if (name == null || name.isBlank()) return "your doctor";
        return name.matches("(?i)^dr\\.?\\s.*") ? name : "Dr " + name;
    }

    private LocalDate today() { return LocalDate.now(clock); }
    private Instant now() { return Instant.now(clock); }
}