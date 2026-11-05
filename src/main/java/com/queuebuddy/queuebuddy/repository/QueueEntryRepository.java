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

    List<QueueEntry> findByClinicIdAndQueueDateAndPatientIdOrderByIdDesc(
            Long clinicId, LocalDate date, Long patientId);

    boolean existsByClinicIdAndQueueDateAndPatientIdAndStatusIn(
            Long clinicId, LocalDate date, Long patientId, Collection<QueueStatus> statuses);

    Optional<QueueEntry> findByIdAndClinicId(Long id, Long clinicId);

    @Query("select coalesce(max(e.ticketNumber), 0) from QueueEntry e "
            + "where e.clinic.id = :clinicId and e.queueDate = :date")
    int maxTicket(@Param("clinicId") Long clinicId, @Param("date") LocalDate date);
}
