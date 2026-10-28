package com.omplayer.player

import com.sun.jna.NativeLibrary
import java.io.File
import uk.co.caprica.vlcj.factory.discovery.NativeDiscovery
import uk.co.caprica.vlcj.factory.discovery.strategy.NativeDiscoveryStrategy

/** Linux fallback for systems that ship only the versioned libvlc.so.5 (no dev symlink). */
private class LinuxVersionedStrategy : NativeDiscoveryStrategy {
    private val dirs = listOf(
        "/app/lib",   // Flatpak/Flathub build: libVLC is bundled into the app
        "/usr/lib/x86_64-linux-gnu", "/usr/lib/aarch64-linux-gnu", "/usr/lib64", "/usr/lib",
        "/usr/local/lib", "/var/lib/flatpak/exports/lib", "/snap/vlc/current/usr/lib",
    )

    override fun supported() = System.getProperty("os.name").orEmpty().lowercase().contains("linux")

    override fun discover(): String? = dirs.firstOrNull { d ->
        File(d).listFiles { f -> f.name.startsWith("libvlc.so") }?.isNotEmpty() == true
    }

    override fun onFound(path: String): Boolean {
        NativeLibrary.addSearchPath("vlc", path)
        NativeLibrary.addSearchPath("vlccore", path)
        return true
    }

    override fun onSetPluginPath(path: String) = true
}

internal object VlcDiscovery {
    /** Default vlcj discovery first (Windows/macOS/Linux with symlink), then the versioned-Linux fallback. */
    fun discover(): Boolean {
        if (NativeDiscovery().discover()) return true
        return NativeDiscovery(LinuxVersionedStrategy()).discover()
    }
}
