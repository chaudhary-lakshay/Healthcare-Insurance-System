package com.lakshay.healthcare.shared.entity

import jakarta.persistence.*
import java.time.LocalDateTime

// Append-only audit row. All fields are val (no setters) so an event can't be mutated in code.
@Entity
@Table(name = "AUDIT_LOG")
data class AuditEvent(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "audit_id")
    val auditId: Long = 0,
    @Column(name = "occurred_at")
    val occurredAt: LocalDateTime = LocalDateTime.now(),
    @Column(name = "actor")
    val actor: String,
    @Column(name = "actor_role")
    val actorRole: String? = null,
    @Column(name = "action")
    val action: String,
    @Column(name = "entity_type")
    val entityType: String? = null,
    @Column(name = "entity_id")
    val entityId: String? = null,
    @Column(name = "detail")
    val detail: String? = null
)
