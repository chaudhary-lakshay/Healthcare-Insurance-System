package com.lakshay.healthcare.ssa.service

import com.lakshay.healthcare.shared.exception.InvalidSsnException
import org.springframework.stereotype.Service

@Service
class SsnValidationService {

    companion object {
        private const val SSN_LENGTH = 9
        private const val STATE_CODE_DIVISOR = 100
        private const val STATE_CODE_TEXAS = 3
        private const val STATE_CODE_CALIFORNIA = 4
        private const val STATE_CODE_FLORIDA = 5
    }

    fun validateSsn(ssn: Long): String {
        val ssnStr = ssn.toString()
        if (ssnStr.length != SSN_LENGTH) {
            throw InvalidSsnException("Invalid SSN: $ssn - SSN must be exactly 9 digits")
        }

        val stateCode = (ssn % STATE_CODE_DIVISOR).toInt()
        return when (stateCode) {
            1 -> "Washington DC"
            2 -> "Ohio"
            STATE_CODE_TEXAS -> "Texas"
            STATE_CODE_CALIFORNIA -> "California"
            STATE_CODE_FLORIDA -> "Florida"
            else -> throw InvalidSsnException("Invalid SSN: $ssn - No state mapped for code $stateCode")
        }
    }
}
