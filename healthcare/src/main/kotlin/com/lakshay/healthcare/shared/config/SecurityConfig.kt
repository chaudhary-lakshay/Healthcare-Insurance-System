package com.lakshay.healthcare.shared.config

import com.lakshay.healthcare.shared.security.AuthEntryPoint
import com.lakshay.healthcare.shared.security.JwtAuthFilter
import com.lakshay.healthcare.shared.security.RateLimitFilter
import org.springframework.beans.factory.annotation.Value
import java.time.Duration
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.http.HttpMethod
import org.springframework.security.config.annotation.web.builders.HttpSecurity
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity
import org.springframework.security.config.http.SessionCreationPolicy
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.security.web.SecurityFilterChain
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter

@Configuration
@EnableWebSecurity
class SecurityConfig(
    private val jwtAuthFilter: JwtAuthFilter,
    private val authEntryPoint: AuthEntryPoint,
    @Value("\${app.auth-throttle.ip-limit:10}") private val ipLimit: Long,
    @Value("\${app.auth-throttle.ip-window-minutes:15}") private val ipWindowMinutes: Long
) {

    @Bean
    fun securityFilterChain(http: HttpSecurity): SecurityFilterChain {
        http
            .csrf { it.disable() }
            .cors { }
            .exceptionHandling { it.authenticationEntryPoint(authEntryPoint) }
            .sessionManagement { it.sessionCreationPolicy(SessionCreationPolicy.STATELESS) }
            .authorizeHttpRequests { auth ->
                auth
                    .requestMatchers(
                        "/api/auth/login",
                        "/user-api/save",
                        "/user-api/activate",
                        "/user-api/login",
                        "/worker-api/save",
                        "/worker-api/activate",
                        "/worker-api/login",
                        "/citizen-api/register",
                        "/actuator/health",
                        "/actuator/info",
                        "/swagger-ui/**",
                        "/v3/api-docs/**"
                    ).permitAll()
                    .requestMatchers(HttpMethod.GET, "/plan-api/categories").permitAll()
                    .requestMatchers("/plan-api/**").hasRole("ADMIN")
                    .requestMatchers("/admin-api/**").hasRole("ADMIN")
                    .requestMatchers("/admin/worker-api/**").hasRole("ADMIN")
                    // user/worker admin ops (list, find, update, delete, status). The public
                    // save/activate/login routes above already matched permitAll (first match wins),
                    // so these ADMIN rules only hit the management ops.
                    .requestMatchers("/user-api/**").hasRole("ADMIN")
                    .requestMatchers("/worker-api/**").hasRole("ADMIN")
                    .requestMatchers("/report-api/**").hasAnyRole("ADMIN", "WORKER")
                    .requestMatchers("/casework-api/**").hasAnyRole("ADMIN", "WORKER")
                    .anyRequest().authenticated()
            }
            .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter::class.java)
            // brute-force throttle sits in front of everything else on the auth endpoints
            .addFilterBefore(RateLimitFilter(ipLimit, Duration.ofMinutes(ipWindowMinutes)), JwtAuthFilter::class.java)

        return http.build()
    }

    @Bean
    fun passwordEncoder(): PasswordEncoder = BCryptPasswordEncoder()
}
