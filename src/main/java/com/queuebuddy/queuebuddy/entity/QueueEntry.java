package com.queuebuddy.queuebuddy.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "queue_entries",
        uniqueConstraints = @UniqueConstraint(columnNames = {"clinic_id", "queue_date", "ticket_number"}))
@Getter @Setter @NoArgsConstructor
public class QueueEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "clinic_id", nullable = false)
    private Clinic clinic;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "patient_id", nullable = false)
    private AppUser patient;

    @Column(nullable = false)
    private String patientName;

    private String patientPhone;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "doctor_id")
    private AppUser doctor;               // doctor the patient is assigned to

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "preferred_doctor_id")
    private AppUser preferredDoctor;      // doctor the patient asked for

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QueueStatus status;

    @Column(nullable = false)
    private LocalDate queueDate;
    
    @Column(name = "ticket_number")
    private Integer ticketNumber;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "served_by_id")
    private AppUser servedBy;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by_id")
    private AppUser decidedBy;

    @Column(length = 255)
    private String rejectionReason;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant decidedAt;      // accepted or rejected
    private Instant notifiedAt;     // desk pressed "Notify"
    private Instant calledAt;
    private Instant completedAt;
}