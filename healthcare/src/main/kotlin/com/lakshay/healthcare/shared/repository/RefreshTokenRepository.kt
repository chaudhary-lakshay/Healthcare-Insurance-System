package com.lakshay.healthcare.shared.repository

import com.lakshay.healthcare.shared.entity.RefreshToken
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import java.time.LocalDateTime

interface RefreshTokenRepository : JpaRepository<RefreshToken, Long> {

    fun findByTokenHash(tokenHash: String): RefreshToken?

    // atomic claim — rowcount 0 means the token was already spent/revoked/missing,
    // so two concurrent refreshes can't both win
    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.usedSw = 'Y' where t.tokenHash = :hash and t.usedSw = 'N' and t.revokedSw = 'N'")
    fun claim(@Param("hash") hash: String): Int

    @Modifying(clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedSw = 'Y' where t.familyId = :familyId")
    fun revokeFamily(@Param("familyId") familyId: String): Int

    @Modifying(clearAutomatically = true)
    fun deleteByExpiresAtBefore(cutoff: LocalDateTime): Int
}
