package com.lakshay.healthcare.benefit.processor

import com.lakshay.healthcare.shared.entity.EligibilityDetails
import org.slf4j.LoggerFactory
import org.springframework.batch.item.ItemProcessor
import org.springframework.stereotype.Component

@Component
class BenefitItemProcessor : ItemProcessor<EligibilityDetails, EligibilityDetails> {

    companion object {
        private const val ACCOUNT_NUMBER_MODULUS = 100000
    }

    private val logger = LoggerFactory.getLogger(BenefitItemProcessor::class.java)

    override fun process(item: EligibilityDetails): EligibilityDetails {
        logger.info("Processing benefit for case: ${item.caseNo}, plan: ${item.planName}")

        return item.copy(
            bankName = "ISH-Bank",
            accountNumber = generateAccountNumber(item.caseNo)
        )
    }

    private fun generateAccountNumber(caseNo: Long): String {
        return "ISH${caseNo}${System.currentTimeMillis() % ACCOUNT_NUMBER_MODULUS}"
    }
}
