package no.nav.modialogin.persistence

import org.testcontainers.containers.Container
import org.testcontainers.containers.PostgreSQLContainer
import java.io.IOException

class RestartablePostgresContainer() : RestartableTestContainer(5432) {
    override fun createBaseContainer(): PostgreSQLContainer<*> {
        return PostgreSQLContainer("postgres:12-alpine")
            .withDatabaseName("postgres")
            .withUsername("username")
            .withPassword("password")
    }

    override fun checkHealth(): Result<Container.ExecResult> {
        return try {
            container.execInContainer("pg_isready", "-U", "postgres", "-d", "postgres").let {
                if (it.exitCode != 0) {
                    throw IOException("Postgres is not ready")
                }
                Result.success(it)
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
