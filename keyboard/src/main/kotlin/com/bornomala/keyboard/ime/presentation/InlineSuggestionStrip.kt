package com.bornomala.keyboard.ime.presentation

import android.view.View
import android.view.ViewGroup
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * The top strip while an autofill service offers inline suggestions (saved logins, addresses,
 * one-time codes): a horizontally scrolling row of the service's own chip views. Tapping a chip
 * is handled entirely by the platform.
 */
@Composable
internal fun InlineSuggestionStrip(views: List<View>, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        for (view in views) {
            key(view) {
                AndroidView(
                    factory = {
                        // A chip view can have only one parent; detach it from any earlier host.
                        (view.parent as? ViewGroup)?.removeView(view)
                        view
                    },
                )
            }
        }
    }
}
