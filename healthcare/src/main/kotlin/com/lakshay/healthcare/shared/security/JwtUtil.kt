package com.lakshay.healthcare.shared.security

import io.jsonwebtoken.Claims
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component
import java.nio.charset.StandardCharsets
import java.util.Date
import javax.crypto.SecretKey

@Component
class JwtUtil(
    @Value("\${jwt.secret}") private val secret: String,
    @Value("\${jwt.expiration}") private val expirationMs: Long,
    @Value("\${jwt.issuer}") private val issuer: String,
    @Value("\${jwt.audience}") private val audience: String
) {

    private fun getSigningKey(): SecretKey =
        Keys.hmacShaKeyFor(secret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(email: String, role: String): String {
        val now = Date()
        val expiry = Date(now.time + expirationMs)

        return Jwts.builder()
            .subject(email)
            .claim("role", role)
            .issuer(issuer)
            .audience().add(audience).and()
            .issuedAt(now)
            .expiration(expiry)
            .signWith(getSigningKey())
            .compact()
    }

    fun extractEmail(token: String): String =
        extractAllClaims(token).subject

    fun extractRole(token: String): String =
        extractAllClaims(token)["role"] as String

    fun extractExpiration(token: String): Date =
        extractAllClaims(token).expiration

    fun isTokenExpired(token: String): Boolean =
        extractExpiration(token).before(Date())

    fun validateToken(token: String, email: String): Boolean {
        val claims = extractAllClaims(token)
        // audience is a Set in jjwt 0.12+; pre-upgrade tokens carried a plain string
        // and the parser folds those into a singleton set, so contains() covers both
        return claims.subject == email
                && claims.issuer == issuer
                && claims.audience?.contains(audience) == true
                && !isTokenExpired(token)
    }

    private fun extractAllClaims(token: String): Claims =
        Jwts.parser()
            .verifyWith(getSigningKey())
            .build()
            .parseSignedClaims(token)
            .payload
}
