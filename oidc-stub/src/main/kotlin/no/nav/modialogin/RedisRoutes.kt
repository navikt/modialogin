package no.nav.modialogin

import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import redis.clients.jedis.JedisPool
import kotlin.time.Duration.Companion.seconds

fun Application.redis(host: String) {
    val pool = JedisPool(host, 6379)
    val password = "password123"
    routing {
        route("redis") {
            get("keys") {
                val keys = pool.resource.use { redis ->
                    redis.auth("password123")
                    redis.keys("*")
                }
                call.respond(keys)
            }

            post("session/{sessionId}") {
                val sessionID = requireNotNull(call.parameters["sessionId"])
                val body = call.receiveText()
                pool.resource.use {redis ->
                    redis.auth(password)
                    redis.setex(sessionID, 60.seconds.inWholeSeconds, body)
                }
                call.respondText(sessionID, status = HttpStatusCode.OK)
            }

            delete {
                pool.resource.use {redis ->
                    redis.auth(password)
                    redis.flushAll()
                }
                call.respondText("", status = HttpStatusCode.OK)
            }
        }
    }
}