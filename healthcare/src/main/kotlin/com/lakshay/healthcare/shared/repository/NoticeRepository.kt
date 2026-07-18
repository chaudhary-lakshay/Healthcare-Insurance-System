package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.Notice
import org.springframework.data.jpa.repository.JpaRepository

interface NoticeRepository : JpaRepository<Notice, Long> {
    fun findByRecipientOrderByCreatedAtDesc(recipient: String): List<Notice>
    fun findByCaseNo(caseNo: Long): List<Notice>
    fun existsByCaseNoAndNoticeTypeAndStatus(caseNo: Long, noticeType: String, status: String): Boolean
}
