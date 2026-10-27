package com.omplayer.data

import java.io.File

internal object AppDirs {
    val config: File by lazy {
        val os = System.getProperty("os.name").orEmpty().lowercase()
        val home = System.getProperty("user.home")
        val dir = when {
            "win" in os -> File(System.getenv("APPDATA") ?: "$home/AppData/Roaming", "OM Player")
            "mac" in os -> File(home, "Library/Application Support/OM Player")
            else -> File(System.getenv("XDG_CONFIG_HOME") ?: "$home/.config", "om-player")
        }
        dir.also { it.mkdirs() }
    }
}
