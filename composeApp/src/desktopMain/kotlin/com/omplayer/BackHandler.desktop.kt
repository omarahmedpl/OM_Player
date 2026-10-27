package com.omplayer

import androidx.compose.runtime.Composable

/** Desktop has no system Back; Esc is handled by each screen's key handler. */
@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {}
