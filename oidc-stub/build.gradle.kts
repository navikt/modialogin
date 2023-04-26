import org.gradle.jvm.tasks.Jar
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

val ktor_version: String by project
val jedis_version: String by project
val logback_version: String by project
val junit_version: String by project

plugins {
    application
    id("setup.repository")
    kotlin("jvm") version "1.8.21"
    kotlin("plugin.serialization") version "1.8.21"
}

dependencies {
    implementation(kotlin("stdlib"))
    implementation("io.ktor:ktor-server-netty:$ktor_version")
    implementation("io.ktor:ktor-server-content-negotiation:$ktor_version")
    implementation("io.ktor:ktor-serialization-kotlinx-json:$ktor_version")
    implementation("com.nimbusds:nimbus-jose-jwt:9.31")
    implementation("ch.qos.logback:logback-classic:$logback_version")
    implementation("redis.clients:jedis:$jedis_version")

    testImplementation("org.junit.jupiter:junit-jupiter:$junit_version")
}

group = "no.nav"
version = ""

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
tasks.withType<KotlinCompile> {
    kotlinOptions.jvmTarget = "17"
}

tasks.test {
    useJUnitPlatform()
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val fatJar = task("fatJar", type = Jar::class) {
    archiveBaseName.set("app")
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    manifest {
        attributes["Implementation-Title"] = "ModiaLogin"
        attributes["Implementation-Version"] = archiveVersion
        attributes["Main-Class"] = "no.nav.modialogin.OidcStubKt"
    }
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) })
    with(tasks.jar.get() as CopySpec)
}

tasks {
    "build" {
        dependsOn(fatJar)
    }
}
