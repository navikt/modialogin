package no.nav.modialogin.persistence

import com.github.dockerjava.api.model.ExposedPort
import com.github.dockerjava.api.model.HostConfig
import com.github.dockerjava.api.model.PortBinding
import com.github.dockerjava.api.model.Ports
import org.testcontainers.containers.Container
import org.testcontainers.containers.GenericContainer
import redis.clients.jedis.HostAndPort

abstract class RestartableTestContainer(val originalPort: Int) {
    protected lateinit var container: GenericContainer<*>
    lateinit var hostAndPort: HostAndPort
    var afterStartupStrategy: (() -> Unit)? = null
        set(value) {
            field = value
            value?.invoke()
        }

    init {
        initWithRandomPorts()
    }

    protected abstract fun createBaseContainer(): GenericContainer<*>

    private fun initWithRandomPorts() {
        container = createBaseContainer()
        container.start()
        hostAndPort = HostAndPort(container.host, container.getMappedPort(originalPort))
    }

    private fun setSamePorts() {
        container = createBaseContainer().withExposedPorts(originalPort)
            .withCreateContainerCmdModifier {
                it.withHostConfig(
                    HostConfig().withPortBindings(PortBinding(Ports.Binding.bindPort(hostAndPort.port), ExposedPort(originalPort)))
                )
            }
    }

    protected abstract fun checkHealth(): Result<Container.ExecResult>

    suspend fun stop() {
        container.stop()
        waitForFailure {
            checkHealth()
        }
    }

    fun unsafeStop() {
        container.stop()
    }

    private suspend fun start() {
        container.start()
        waitForSuccess {
            checkHealth()
        }
    }

    suspend fun restart() {
        println("Restarting container")
        stop()
        setSamePorts()
        start()
        afterStartupStrategy?.invoke()
        println("Container restarted")
    }

    private suspend fun waitForSuccess(ping: suspend () -> Result<*>) {
        var result = ping()
        while (result.isFailure) {
            result = ping()
        }
    }

    private suspend fun waitForFailure(ping: suspend () -> Result<*>) {
        var result = ping()
        while (result.isSuccess) {
            result = ping()
        }
    }
}
