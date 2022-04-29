package no.nav.modialogin.common

import java.io.File

object FileUtils {
    fun copyAndProcessFiles(src: File, dest: File, processor: (content: String) -> String) {
        src.copyRecursively(target = dest, overwrite = true)
        for (file in dest.walkTopDown()) {
            if (file.isFile) {
                file.writeText(
                    processor(
                        file.readText()
                    )
                )
            }
        }
    }
}