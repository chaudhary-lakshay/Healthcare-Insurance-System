package com.lakshay.healthcare.shared.security

import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

/**
 * Fills in the SecurityContext from a Bearer token, nothing more.
 *
 * No token, bad token, expired token -> leave the request unauthenticated and let it through.
 * SecurityConfig decides what's public; AuthEntryPoint sends the 401. We used to keep a second
 * copy of the public-path list here and it drifted (token-less /actuator/info got wrongly 401'd),
 * so the allowlist lives only in SecurityConfig now.
 */
@Component
class JwtAuthFilter(
    private val jwtUtil: JwtUtil
) : OncePerRequestFilter() {

    companion object {
        private const val BEARER_PREFIX_LENGTH = 7
    }

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain
    ) {
        val authHeader = request.getHeader("Authorization")
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            authenticate(authHeader.substring(BEARER_PREFIX_LENGTH))
        }
        filterChain.doFilter(request, response)
    }

    // Valid token -> set auth. Anything thrown (malformed/expired) is swallowed so the request
    // falls through unauthenticated and the authz layer 401s it. Never a 500 — JJWT errors aren't
    // AuthenticationExceptions.
    // Broad catch on purpose: any bad/expired/malformed token just leaves the request
    // unauthenticated for the authz layer to 401. JJWT errors aren't AuthenticationExceptions.
    @Suppress("TooGenericExceptionCaught", "SwallowedException")
    private fun authenticate(token: String) {
        try {
            val email = jwtUtil.extractEmail(token)
            if (jwtUtil.validateToken(token, email)) {
                val authorities = listOf(SimpleGrantedAuthority(jwtUtil.extractRole(token)))
                SecurityContextHolder.getContext().authentication =
                    UsernamePasswordAuthenticationToken(email, null, authorities)
            }
        } catch (e: Exception) {
            // bad token -> stay unauthenticated
        }
    }
}
