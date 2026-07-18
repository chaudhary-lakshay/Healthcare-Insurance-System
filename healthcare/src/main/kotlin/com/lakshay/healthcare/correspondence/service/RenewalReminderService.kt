package com.lakshay.healthcare.correspondence.service

import com.lakshay.healthcare.shared.notification.NotificationService
import com.lakshay.healthcare.shared.repository.CitizenAppRegistrationRepository
import com.lakshay.healthcare.shared.repository.DcCaseRepository
import com.lakshay.healthcare.shared.repository.EligibilityDetailsRepository
import com.lakshay.healthcare.shared.repository.NoticeRepository
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.time.LocalDate

// Nudges citizens to renew at 60/30/7 days before their plan ends. Off in tests
// (the cron never fires in a short-lived JVM anyway; the flag keeps the bean out too).
@Service
@ConditionalOnProperty(name = ["app.renewal-reminders.enabled"], havingValue = "true", matchIfMissing = true)
class RenewalReminderService(
    private val eligibilityRepository: EligibilityDetailsRepository,
    private val dcCaseRepository: DcCaseRepository,
    private val citizenRepository: CitizenAppRegistrationRepository,
    private val noticeRepository: NoticeRepository,
    private val notificationService: NotificationService
) {
    private val log = LoggerFactory.getLogger(RenewalReminderService::class.java)

    companion object {
        val MILESTONES = listOf(60L, 30L, 7L)

        // which renewal milestone a plan-end date hits relative to today, or null. Pure — the test drives this.
        fun dueMilestone(today: LocalDate, planEndDate: LocalDate?): Long? =
            planEndDate?.let { end -> MILESTONES.firstOrNull { today.plusDays(it) == end } }
    }

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "renewalReminders", lockAtLeastFor = "1m", lockAtMostFor = "15m")
    fun sendRenewalReminders() {
        val today = LocalDate.now()
        var sent = 0
        for (days in MILESTONES) {
            val target = today.plusDays(days)
            for (elig in eligibilityRepository.findByPlanStatusAndPlanEndDate("APPROVED", target)) {
                if (remind(elig.caseNo, elig.planName, target, days)) sent++
            }
        }
        log.info("Renewal reminders sent: {}", sent)
    }

    // dedup on a SENT notice, not mere existence — a prior FAILED send must retry.
    // Email carries plan name + end date only; never SSN or bank details.
    private fun remind(caseNo: Long, planName: String?, endDate: LocalDate, days: Long): Boolean {
        val type = "RENEWAL_$days"
        if (noticeRepository.existsByCaseNoAndNoticeTypeAndStatus(caseNo, type, "SENT")) return false
        val email = dcCaseRepository.findByCaseNo(caseNo)
            ?.let { citizenRepository.findByAppId(it.appId)?.email }
        if (email == null) {
            log.warn("No citizen email for case {}, renewal reminder skipped", caseNo)
            return false
        }
        val notice = notificationService.notifyEmail(
            caseNo = caseNo, recipient = email, noticeType = type,
            subject = "Your ${planName ?: "plan"} renews soon - Case #$caseNo",
            body = "Your ${planName ?: "plan"} coverage ends on $endDate ($days days away). " +
                "Log in to your portal to renew before it lapses."
        )
        return notice?.status == "SENT"
    }
}
