package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.AuditEvent
import org.springframework.data.jpa.repository.JpaRepository

interface AuditEventRepository : JpaRepository<AuditEvent, Long>
