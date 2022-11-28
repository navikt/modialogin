package no.nav.modialogin

enum class ApplicationMode(val locally: Boolean, val withinDockercompose: Boolean) {
    LOCALLY_WITHIN_DOCKER(true, true),
    LOCALLY_OUTSIDE_DOCKER(true, false),
    NAIS(false, false);

    fun appport(): Int {
        return when(this) {
            LOCALLY_WITHIN_DOCKER -> 8080
            LOCALLY_OUTSIDE_DOCKER -> 8083
            NAIS -> 8080
        }
    }

    fun hostport(): Int {
        return when(this) {
            LOCALLY_WITHIN_DOCKER -> 8083
            LOCALLY_OUTSIDE_DOCKER -> 8083
            NAIS -> 8080
        }
    }

    companion object {
        fun parse(value: String?): ApplicationMode = value
            ?.let { ApplicationMode.valueOf(it) }
            ?: NAIS
    }
}