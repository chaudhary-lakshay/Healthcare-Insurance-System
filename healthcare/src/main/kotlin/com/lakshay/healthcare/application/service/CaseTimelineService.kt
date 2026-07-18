package com.lakshay.healthcare.application.service

import com.lakshay.healthcare.application.dto.CaseTimelineResponse
import com.lakshay.healthcare.application.dto.TimelineEntry
import com.lakshay.healthcare.shared.entity.DcCase
import com.lakshay.healthcare.shared.exception.ResourceNotFoundException
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.security.OwnershipService
import jakarta.persistence.EntityManager
import org.hibernate.envers.AuditReaderFactory
import org.hibernate.envers.DefaultRevisionEntity
import org.hibernate.envers.query.AuditEntity
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant
import java.time.ZoneId

// "Where has my application been": every status transition Envers captured, oldest first.
// Response is status + timestamp only — no planId/appId, minimal disclosure.
@Service
class CaseTimelineService(
    private val entityManager: EntityManager,
    private val dcCaseRepository: DcCaseRepository,
    private val ownershipService: OwnershipService
) {

    // not readOnly: OwnershipService writes a CASE_ACCESS_DENIED audit row on the denial path
    @Transactional
    fun timeline(caseNo: Long): CaseTimelineResponse {
        ownershipService.assertCanAccessCase(caseNo)
        val case = dcCaseRepository.findByCaseNo(caseNo)
            ?: throw ResourceNotFoundException("Case not found: $caseNo")

        val revisions = AuditReaderFactory.get(entityManager)
            .createQuery()
            .forRevisionsOfEntity(DcCase::class.java, false, false)
            .add(AuditEntity.id().eq(caseNo))
            .addOrder(AuditEntity.revisionNumber().asc())
            .resultList

        val entries = revisions.mapNotNull { row ->
            val (entity, revision) = (row as Array<*>).let { it[0] as DcCase to it[1] as DefaultRevisionEntity }
            TimelineEntry(
                status = entity.caseStatus.name,
                at = Instant.ofEpochMilli(revision.timestamp)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDateTime()
                    .toString()
            )
        }.distinctByConsecutiveStatus()

        // cases older than the audit feature have no revisions — show where they stand now
        val timeline = entries.ifEmpty {
            listOf(TimelineEntry(status = case.caseStatus.name, at = ""))
        }
        return CaseTimelineResponse(caseNo = caseNo, timeline = timeline)
    }

    // plan-only edits re-save the same status; collapse those so the timeline reads as transitions
    private fun List<TimelineEntry>.distinctByConsecutiveStatus(): List<TimelineEntry> =
        fold(mutableListOf()) { acc, e ->
            if (acc.lastOrNull()?.status != e.status) acc.add(e)
            acc
        }
}
