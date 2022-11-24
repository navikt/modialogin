package no.nav.modialogin

import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import redis.clients.jedis.JedisPool

fun Application.redisIntrospection(host: String) {
    val pool = JedisPool(host, 6379)
    routing {
        route("redis") {
            get("keys") {
                val keys = pool.resource.use { redis ->
                    redis.auth("password123")
                    redis.keys("*")
                }
                call.respond(keys)
            }
        }
    }
}