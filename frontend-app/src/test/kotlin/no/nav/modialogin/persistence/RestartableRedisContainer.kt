package no.nav.modialogin.persistence

import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import java.io.IOException
import java.lang.Exception

class RestartableRedisContainer : RestartableTestContainer(6379) {
    override fun createBaseContainer(): GenericContainer<*> {
        class RedisContainer : GenericContainer<RedisContainer>("redis:6-alpine") {
            init {
                withCommand("redis-server --requirepass password")
                withExposedPorts(6379)
            }
        }
        return RedisContainer()
    }

    override fun checkHealth(): Result<Container.ExecResult> {
        return try {
            container.execInContainer("redis-cli", "-a", "password", "ping").let {
                if (it.exitCode != 0 || it.stdout != "PONG\n") {
                    throw IOException("Redis is not ready")
                }
                Result.success(it)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
