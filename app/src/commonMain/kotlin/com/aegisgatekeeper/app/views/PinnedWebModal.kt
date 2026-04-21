package com.aegisgatekeeper.app.views

import androidx.compose.runtime.Composable

@Suppress("FunctionName")
@Composable
expect fun PinnedWebModal(
    url: String,
    onClose: () -> Unit,
)
