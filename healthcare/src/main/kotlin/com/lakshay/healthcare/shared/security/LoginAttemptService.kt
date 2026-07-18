package com.lakshay.healthcare.shared.security

import com.github.benmanes.caffeine.cache.Caffeine
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger

// Failed-login counter per email with temp lockout. In-memory on purpose:
// single-node app, counters are throwaway defense, not domain data.
@Service
class LoginAttemptService(
    @Value("\${app.auth-throttle.max-failures:5}") private val maxFailures: Int,
    @Value("\${app.auth-throttle.lockout-minutes:15}") private val lockoutMinutes: Long
) {
    private val log = LoggerFactory.getLogger(LoginAttemptService::class.java)

    private class Attempts {
        val failures = AtomicInteger()
        @Volatile
        var lockedUntil: Instant? = null
    }

    private val attempts = Caffeine.newBuilder()
        .expireAfterWrite(Duration.ofMinutes(lockoutMinutes))
        .maximumSize(100_000)
        .build<String, Attempts>()

    // static value, not remaining time — don't leak lock age per account
    fun lockoutSeconds(): Long = lockoutMinutes * 60

    fun isLocked(email: String): Boolean {
        val until = attempts.getIfPresent(key(email))?.lockedUntil ?: return false
        return Instant.now().isBefore(until)
    }

    fun recordFailure(email: String) {
        val k = key(email)
        val a = attempts.get(k) { Attempts() }
        // re-read volatile right before increment — narrows the lock/increment race
        if (a.lockedUntil?.let { Instant.now().isBefore(it) } == true) return
        if (a.failures.incrementAndGet() >= maxFailures) {
            a.lockedUntil = Instant.now().plus(Duration.ofMinutes(lockoutMinutes))
            a.failures.set(0)
            log.warn("Account lockout: {} locked for {} min after {} failed logins", k, lockoutMinutes, maxFailures)
        }
        attempts.put(k, a) // refresh write timestamp so the window slides
    }

    fun recordSuccess(email: String) {
        attempts.invalidate(key(email))
    }

    private fun key(email: String) = email.trim().lowercase()
}
