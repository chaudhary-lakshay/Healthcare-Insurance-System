package com.lakshay.healthcare.shared.security

import com.github.benmanes.caffeine.cache.Caffeine
import com.github.benmanes.caffeine.cache.LoadingCache
import io.github.bucket4j.Bandwidth
import io.github.bucket4j.Bucket
import io.github.bucket4j.Refill
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.filter.OncePerRequestFilter
import java.time.Duration

// Per-IP throttle on the public auth endpoints. Deliberately NOT a @Component:
// SecurityConfig news it up into the security chain only — a Filter bean would
// get auto-registered a second time in the servlet chain and eat 2 tokens per hit.
class RateLimitFilter(
    private val limit: Long,
    private val window: Duration
) : OncePerRequestFilter() {

    companion object {
        val AUTH_PATHS = setOf(
            "/api/auth/login",
            "/api/auth/refresh",
            "/api/auth/logout",
            "/user-api/login",
            "/user-api/activate",
            "/worker-api/login",
            "/worker-api/activate"
        )
        private const val CACHE_MAX_SIZE = 100_000L
    }

    private val buckets: LoadingCache<String, Bucket> = Caffeine.newBuilder()
        .expireAfterAccess(window.multipliedBy(2))
        .maximumSize(CACHE_MAX_SIZE) // spoofed-IP flood shouldn't OOM us
        .build { _ ->
            Bucket.builder()
                .addLimit(Bandwidth.classic(limit, Refill.intervally(limit, window)))
                .build()
        }

    override fun shouldNotFilter(request: HttpServletRequest): Boolean =
        request.method != "POST" || request.requestURI.trimEnd('/') !in AUTH_PATHS

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        // remoteAddr on purpose — X-Forwarded-For is client-spoofable, no trusted proxy here
        val probe = buckets.get(request.remoteAddr).tryConsumeAndReturnRemaining(1)
        if (probe.isConsumed) {
            filterChain.doFilter(request, response)
            return
        }
        val retryAfter = Duration.ofNanos(probe.nanosToWaitForRefill).seconds + 1
        response.status = HttpStatus.TOO_MANY_REQUESTS.value()
        response.setHeader("Retry-After", retryAfter.toString())
        response.contentType = MediaType.APPLICATION_JSON_VALUE
        response.writer.write(
            """{"status":429,"error":"Too Many Requests","message":"Too many attempts. Try again later."}"""
        )
    }
}
