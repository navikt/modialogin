package no.nav.modialogin.features.authfeature

import com.github.benmanes.caffeine.cache.Expiry
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object AccessTokenExpirationStrategy : Expiry<SessionId, TokenPrincipal> {
    override fun expireAfterCreate(key: SessionId, value: TokenPrincipal, currentTime: Long): Long {
        return value.durationToExpiry().inWholeNanoseconds
    }

    override fun expireAfterUpdate(
        key: SessionId,
        value: TokenPrincipal,
        currentTime: Long,
        currentDuration: Long
    ): Long {
        return value.durationToExpiry().inWholeNanoseconds
    }

    override fun expireAfterRead(
        key: SessionId?,
        value: TokenPrincipal?,
        currentTime: Long,
        currentDuration: Long
    ): Long {
        return currentDuration
    }

    private fun TokenPrincipal.durationToExpiry(): Duration {
        return (accessToken.expiresAt.time - System.currentTimeMillis()).milliseconds
    }

}