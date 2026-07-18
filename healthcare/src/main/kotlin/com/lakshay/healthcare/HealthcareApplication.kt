package com.lakshay.healthcare

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class HealthcareApplication

// SpreadOperator: runApplication requires the vararg spread — it's the Spring Boot idiom,
// and this runs once at startup so the array copy is irrelevant.
@Suppress("SpreadOperator")
fun main(args: Array<String>) {
	runApplication<HealthcareApplication>(*args)
}
