package com.lakshay.healthcare.ssa.service

import com.lakshay.healthcare.shared.exception.InvalidSsnException
import org.springframework.stereotype.Service

@Service
class SsnValidationService {

    fun validateSsn(ssn: Long): String {
        val ssnStr = ssn.toString()
        if (ssnStr.length != 9) {
            throw InvalidSsnException("Invalid SSN: $ssn - SSN must be exactly 9 digits")
        }

        val stateCode = (ssn % 100).toInt()
        return when (stateCode) {
            1 -> "Washington DC"
            2 -> "Ohio"
            3 -> "Texas"
            4 -> "California"
            5 -> "Florida"
            else -> throw InvalidSsnException("Invalid SSN: $ssn - No state mapped for code $stateCode")
        }
    }
}
