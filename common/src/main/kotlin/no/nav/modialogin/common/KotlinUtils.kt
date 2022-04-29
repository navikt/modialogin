package no.nav.modialogin.common

object KotlinUtils {
    fun String.cutoff(n: Int): String {
        return if (this.length <= n) {
            this
        } else {
            this.substring(0, n - 3) + "..."
        }
    }

    fun getEnvProperty(name: String): String {
        return System.getProperty(name, System.getenv(name)) ?: "'$name' not defined"
    }
}
