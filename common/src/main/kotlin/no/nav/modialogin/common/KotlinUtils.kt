package no.nav.modialogin.common

object KotlinUtils {
    fun String.cutoff(n: Int): String {
        return if (this.length <= n) {
            this
        } else {
            this.substring(0, n - 3) + "..."
        }
    }

    fun getProperty(name: String): String? = System.getProperty(name, System.getenv(name))
    fun requireProperty(name: String): String = requireNotNull(getProperty(name)) {
        "'$name' was not defined"
    }
}
