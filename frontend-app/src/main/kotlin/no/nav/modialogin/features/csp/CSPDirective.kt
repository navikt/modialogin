package no.nav.modialogin.features.csp

class CSPDirective(private val directives: MutableMap<Directive, MutableList<String>>) {
    override fun toString(): String = directives
        .filter { it.value.isNotEmpty() }
        .map {
            val directive = arrayOf(it.key.directive, *it.value.toTypedArray())
            directive.joinToString(" ")
        }.joinToString(";")

    fun copy(): CSPDirective = CSPDirective(
        directives.entries.associateTo(LinkedHashMap()) { it.key to it.value.toMutableList() }
    )

    fun add(directive: Directive, part: String) {
        directives.getOrPut(directive) { mutableListOf() }.add(part)
    }

    companion object {
        operator fun invoke(value: String): CSPDirective {
            val directives = value.split(";")
                .filter { it.isNotBlank() }
                .associateTo(LinkedHashMap()) { directive ->
                    val words = directive.split(" ").filter { it.isNotBlank() }
                    val type = requireNotNull(Directive.reverseLUT[words.first().trim()]) {
                        "${words.first()} not found in reverseLUT"
                    }
                    val directiveValue = words.drop(1).toMutableList()
                    type to directiveValue
                }
            return CSPDirective(directives)
        }
    }

    enum class Directive(val directive: String) {
        DEFAULT_SRC("default-src"),
        CHILD_SRC("child-src"),
        CONNECT_SRC("connect-src"),
        FONT_SRC("font-src"),
        FRAME_SRC("frame-src"),
        IMG_SRC("img-src"),
        MANIFEST_SRC("manifest-src"),
        MEDIA_SRC("media-src"),
        OBJECT_SRC("object-src"),
        PREFETCH_SRC("prefetch-src"),
        SCRIPT_SRC("script-src"),
        STYLE_SRC("style-src"),
        WORKER_SRC("worker-src"),
        REPORT_URI("report-uri");

        companion object {
            val reverseLUT: Map<String, Directive> = Directive.values().associateBy { it.directive }
        }
    }
}
