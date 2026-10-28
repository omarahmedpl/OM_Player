package com.omplayer

import androidx.compose.runtime.Composable

@Composable
actual fun FullscreenHost(active: Boolean, onExit: () -> Unit, content: @Composable () -> Unit) = content()
