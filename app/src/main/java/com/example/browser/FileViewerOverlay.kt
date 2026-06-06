package com.example.browser

import androidx.compose.runtime.Composable

@Composable
fun FileViewerOverlay(
    activeFile: ActiveViewerFile,
    viewModel: BrowserViewModel,
    onClose: () -> Unit
) {
    LocalViewerOverlay(
        activeFile = activeFile,
        viewModel = viewModel,
        onDismiss = onClose
    )
}
