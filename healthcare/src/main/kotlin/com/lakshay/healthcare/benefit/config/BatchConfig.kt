package com.lakshay.healthcare.benefit.config

import com.lakshay.healthcare.benefit.processor.BenefitItemProcessor
import com.lakshay.healthcare.shared.entity.EligibilityDetails
import jakarta.persistence.EntityManagerFactory
import org.springframework.batch.core.Job
import org.springframework.batch.core.job.builder.JobBuilder
import org.springframework.batch.core.repository.JobRepository
import org.springframework.batch.core.step.builder.StepBuilder
import org.springframework.batch.item.database.JpaPagingItemReader
import org.springframework.batch.item.file.FlatFileItemWriter
import org.springframework.batch.item.file.transform.BeanWrapperFieldExtractor
import org.springframework.batch.item.file.transform.DelimitedLineAggregator
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.core.io.FileSystemResource
import org.springframework.transaction.PlatformTransactionManager

@Configuration
class BatchConfig {

    @Value("\${benefit.issuance.output-file:benefit_output.csv}")
    private lateinit var outputFile: String

    @Bean
    fun reader(entityManagerFactory: EntityManagerFactory): JpaPagingItemReader<EligibilityDetails> {
        val reader = JpaPagingItemReader<EligibilityDetails>()
        reader.setName("benefitItemReader")
        reader.setEntityManagerFactory(entityManagerFactory)
        reader.setQueryString("select e from EligibilityDetails e where e.planStatus = 'APPROVED'")
        reader.setPageSize(10)
        reader.afterPropertiesSet()
        return reader
    }

    @Bean
    fun processor(): BenefitItemProcessor = BenefitItemProcessor()

    @Bean
    fun writer(): FlatFileItemWriter<EligibilityDetails> {
        val writer = FlatFileItemWriter<EligibilityDetails>()
        writer.setName("benefitItemWriter")
        writer.setResource(FileSystemResource(outputFile))

        val fieldExtractor = BeanWrapperFieldExtractor<EligibilityDetails>()
        fieldExtractor.setNames(arrayOf("caseNo", "holderName", "holderSSN", "planName", "benefitAmt", "bankName", "accountNumber"))

        val lineAggregator = DelimitedLineAggregator<EligibilityDetails>()
        lineAggregator.setDelimiter(",")
        lineAggregator.setFieldExtractor(fieldExtractor)

        writer.setLineAggregator(lineAggregator)
        writer.setHeaderCallback { it.write("Case No,Holder Name,SSN,Plan Name,Benefit Amount,Bank Name,Account Number") }
        writer.afterPropertiesSet()
        return writer
    }

    @Bean
    fun step1(
        jobRepository: JobRepository,
        transactionManager: PlatformTransactionManager,
        reader: JpaPagingItemReader<EligibilityDetails>,
        processor: BenefitItemProcessor,
        writer: FlatFileItemWriter<EligibilityDetails>
    ): org.springframework.batch.core.Step {
        return StepBuilder("benefitIssuanceStep", jobRepository)
            .chunk<EligibilityDetails, EligibilityDetails>(10, transactionManager)
            .reader(reader)
            .processor(processor)
            .writer(writer)
            .build()
    }

    @Bean
    fun benefitIssuanceJob(
        jobRepository: JobRepository,
        step1: org.springframework.batch.core.Step
    ): Job {
        return JobBuilder("benefitIssuanceJob", jobRepository)
            .start(step1)
            .build()
    }
}
