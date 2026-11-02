package com.queuebuddy.queuebuddy.repository;

import com.queuebuddy.queuebuddy.entity.QueueEntry;
import com.queuebuddy.queuebuddy.entity.QueueStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface QueueEntryRepository extends JpaRepository<QueueEntry, Long> {

    List<QueueEntry> findByClinicIdAndQueueDateAndStatusInOrderByTicketNumberAsc(
            Long clinicId, LocalDate date, Collection<QueueStatus> statuses);

    List<QueueEntry> findByClinicIdAndQueueDateAndStatusAndServedById(
            Long clinicId, LocalDate date, QueueStatus status, Long servedById);

    Optional<QueueEntry> findFirstByClinicIdAndQueueDateAndStatusOrderByTicketNumberAsc(
            Long clinicId, LocalDate date, QueueStatus status);

    Optional<QueueEntry> findByIdAndClinicId(Long id, Long clinicId);

    List<QueueEntry> findByClinicIdAndQueueDateAndPatientIdOrderByIdDesc(
            Long clinicId, LocalDate date, Long patientId);

    boolean existsByClinicIdAndQueueDateAndPatientIdAndStatusIn(
            Long clinicId, LocalDate date, Long patientId, Collection<QueueStatus> statuses);

    @Query("select coalesce(max(q.ticketNumber), 0) from QueueEntry q " +
            "where q.clinic.id = :clinicId and q.queueDate = :date")
    int maxTicket(@Param("clinicId") Long clinicId, @Param("date") LocalDate date);
}