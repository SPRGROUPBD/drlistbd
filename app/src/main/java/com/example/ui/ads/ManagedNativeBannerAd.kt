package com.example.ui.ads

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * Compatibility placeholder for legacy network-native placements.
 * Third-party ad networks are intentionally removed; direct Firebase sponsor cards
 * are rendered by NativeSponsorAdCard and fair-rotation campaign components instead.
 */
@Composable
@Suppress("UNUSED_PARAMETER")
fun ManagedNativeBannerAd(
    adConfig: Any,
    isOnline: Boolean,
    refreshKey: String,
    enforce300x200: Boolean = false,
    modifier: Modifier = Modifier
) {
    // Intentionally renders nothing: never load third-party network ads.
}
