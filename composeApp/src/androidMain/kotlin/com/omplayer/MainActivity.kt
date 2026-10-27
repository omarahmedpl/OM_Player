package com.omplayer

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.omplayer.data.InMemoryProgressStore
import com.omplayer.data.SampleMediaRepository
import com.omplayer.player.FakePlayerEngine

/** Scaffold entry point. The Android agent replaces the fakes with Media3 + MediaStore. */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        val deps = AppDeps(
            repository = SampleMediaRepository(),
            progress = InMemoryProgressStore(),
            engine = FakePlayerEngine(),
            platform = object : PlatformActions { override val formFactor = FormFactor.Phone },
        )
        setContent { App(deps) }
    }
}
