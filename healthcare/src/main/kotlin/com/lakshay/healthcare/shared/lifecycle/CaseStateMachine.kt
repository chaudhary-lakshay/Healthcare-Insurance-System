package com.lakshay.healthcare.shared.lifecycle

import com.lakshay.healthcare.shared.entity.DcCase
import org.springframework.stereotype.Component

// Only place that changes a case's status. Keeps callers from scattering caseStatus = X around.
// Re-running determination on an already-DETERMINED case is allowed (idempotent).
@Component
class CaseStateMachine {

    private val allowed: Map<CaseStatus, Set<CaseStatus>> = mapOf(
        CaseStatus.SUBMITTED to setOf(CaseStatus.DETERMINED),
        CaseStatus.DETERMINED to setOf(CaseStatus.DETERMINED)
    )

    fun canTransition(from: CaseStatus, to: CaseStatus): Boolean =
        to in (allowed[from] ?: emptySet())

    // Returns a copy of [case] in state [to], or throws if the move isn't allowed.
    fun transition(case: DcCase, to: CaseStatus): DcCase {
        require(canTransition(case.caseStatus, to)) {
            "Illegal case transition ${case.caseStatus} -> $to (case ${case.caseNo})"
        }
        return case.copy(caseStatus = to)
    }
}
