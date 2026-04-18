package com.lakshay.healthcare.correspondence.dto

data class TriggerResponse(
    val triggerId: Long,
    val caseNo: Long,
    val triggerStatus: String?
)
