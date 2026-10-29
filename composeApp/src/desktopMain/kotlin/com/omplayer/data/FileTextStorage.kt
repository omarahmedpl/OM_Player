package com.omplayer.data

import java.io.File

class FileTextStorage(private val file: File = File(AppDirs.config, "library.txt")) : TextStorage {
    override fun read(): String? = runCatching { if (file.exists()) file.readText() else null }.getOrNull()
    override fun write(text: String) {
        file.parentFile?.mkdirs()
        val tmp = File(file.path + ".tmp")
        tmp.writeText(text)
        java.nio.file.Files.move(tmp.toPath(), file.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING)
    }
}
