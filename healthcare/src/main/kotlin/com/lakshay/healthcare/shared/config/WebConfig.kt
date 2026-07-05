package com.lakshay.healthcare.shared.config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.servlet.config.annotation.CorsRegistry
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer

@Configuration
class WebConfig(
    @Value("\${app.cors.allowed-origins:*}") private val allowedOrigins: String
) {

    private val log = LoggerFactory.getLogger(WebConfig::class.java)

    @Bean
    fun corsConfigurer(): WebMvcConfigurer {
        val origins = allowedOrigins.split(",")
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .ifEmpty { listOf("*") }

        if (origins.contains("*")) {
            log.warn("CORS allowed-origins is '*' (all origins allowed). Set app.cors.allowed-origins to a specific list in production.")
        }

        return object : WebMvcConfigurer {
            override fun addCorsMappings(registry: CorsRegistry) {
                registry.addMapping("/**")
                    .allowedOrigins(*origins.toTypedArray())
                    .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
                    .allowedHeaders("*")
            }
        }
    }
}
