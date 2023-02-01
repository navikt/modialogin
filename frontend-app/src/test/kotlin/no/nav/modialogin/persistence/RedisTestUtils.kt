package no.nav.modialogin.persistence

import kotlinx.serialization.KSerializer
import no.nav.modialogin.persistence.redis.RedisPersistence
import no.nav.modialogin.persistence.redis.RedisPersistencePubSub
import no.nav.modialogin.utils.AuthJedisPool
import no.nav.modialogin.utils.RedisConfig
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll

object RedisTestUtils {
    open class WithRedis {
        var container: RestartableRedisContainer? = null

        @BeforeAll
        fun setUp() {
            container = RestartableRedisContainer()
        }

        @AfterAll
        fun tearDown() {
            container?.unsafeStop()
        }
    }

    fun <KEY, VALUE>getIntegrationTestUtils(container: RestartableRedisContainer?, scope: String, keySerializer: KSerializer<KEY>, valueSerializer: KSerializer<VALUE>, enablePubSub: Boolean = false): RedisIntegrationTestUtils<KEY, VALUE> {
        requireNotNull(container)

        val redisConfig = RedisConfig(container.hostAndPort.host, container.hostAndPort.port, "password")

        val sendPool = AuthJedisPool(redisConfig)
        val receivePool = AuthJedisPool(redisConfig)

        val sendPubSub = if (enablePubSub) RedisPersistencePubSub("test", redisConfig) else null
        val receivePubSub = if (enablePubSub) RedisPersistencePubSub("test", redisConfig) else null

        val sendRedis = RedisPersistence(scope, keySerializer, valueSerializer, sendPool, sendPubSub)
        val receiveRedis = RedisPersistence(scope, keySerializer, valueSerializer, receivePool, receivePubSub)

        return RedisIntegrationTestUtils(redisConfig, sendRedis, receiveRedis)
    }

    data class RedisIntegrationTestUtils<KEY, VALUE>(
        val redisConfig: RedisConfig,
        val sendRedis: RedisPersistence<KEY, VALUE>,
        val receiveRedis: RedisPersistence<KEY, VALUE>,
    )
}
