package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.remote.HomeAutoSliderItem
import com.example.i18n.AppLang
import kotlinx.coroutines.delay

@Composable
fun CompactHomeAutoSlider(
    slides: List<HomeAutoSliderItem>,
    lang: AppLang,
    autoSlideIntervalMs: Long = 4000L,
    onViewDetails: (HomeAutoSliderItem) -> Unit,
    onSlideVisible: (HomeAutoSliderItem) -> Unit,
    onClearFilter: (() -> Unit)? = null
) {
    val listState = rememberLazyListState()
    var currentIndex by remember(slides) { mutableIntStateOf(0) }

    LaunchedEffect(slides, autoSlideIntervalMs) {
        if (slides.size < 2) return@LaunchedEffect
        while (true) {
            delay(autoSlideIntervalMs.coerceAtLeast(1500L))
            currentIndex = (currentIndex + 1) % slides.size
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(Modifier.fillMaxWidth()) {
        if (slides.isEmpty()) {
            Surface(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Box(Modifier.height(150.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("Doctor List BD", style = MaterialTheme.typography.titleMedium)
                }
            }
        } else {
            LazyRow(
                state = listState,
                contentPadding = PaddingValues(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                itemsIndexed(slides, key = { _, item -> item.id }) { index, item ->
                    LaunchedEffect(item.id) { onSlideVisible(item) }
                    Surface(
                        modifier = Modifier.width(320.dp).height(166.dp)
                            .clickable { onViewDetails(item) },
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surface,
                        tonalElevation = 2.dp
                    ) {
                        Box {
                            if (item.imageUrl.startsWith("http", true)) {
                                AsyncImage(
                                    model = item.imageUrl,
                                    contentDescription = item.displayName(lang),
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    Modifier.fillMaxSize().background(
                                        MaterialTheme.colorScheme.primaryContainer
                                    )
                                )
                            }
                            Box(
                                Modifier.align(Alignment.BottomStart).fillMaxWidth()
                                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.68f))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Column {
                                    Text(
                                        item.displayName(lang).ifBlank { "Doctor List BD" },
                                        color = MaterialTheme.colorScheme.inverseOnSurface,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val subtitle = item.displaySubtitle(lang).ifBlank { item.displaySpecialty(lang) }
                                    if (subtitle.isNotBlank()) {
                                        Text(
                                            subtitle,
                                            color = MaterialTheme.colorScheme.inverseOnSurface,
                                            style = MaterialTheme.typography.bodySmall,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        if (onClearFilter != null) {
            TextButton(
                onClick = onClearFilter,
                modifier = Modifier.align(Alignment.End).padding(end = 12.dp)
            ) { Text(if (lang == AppLang.BN) "ফিল্টার মুছুন" else "Clear filter") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SliderDetailBottomSheet(
    item: HomeAutoSliderItem,
    lang: AppLang,
    onOpenLinkedRecord: (HomeAutoSliderItem) -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (item.imageUrl.startsWith("http", true)) {
                AsyncImage(
                    model = item.imageUrl,
                    contentDescription = item.displayName(lang),
                    modifier = Modifier.fillMaxWidth().height(190.dp).clip(RoundedCornerShape(14.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Text(item.displayName(lang), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            val subtitle = item.displaySubtitle(lang)
            if (subtitle.isNotBlank()) Text(subtitle, style = MaterialTheme.typography.titleMedium)
            val description = item.displayDescription(lang)
            if (description.isNotBlank()) Text(description, style = MaterialTheme.typography.bodyMedium)
            val address = item.displayAddress(lang)
            if (address.isNotBlank()) Text(address, style = MaterialTheme.typography.bodySmall)
            Button(
                onClick = { onOpenLinkedRecord(item) },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (lang == AppLang.BN) "বিস্তারিত দেখুন" else "View details") }
            Spacer(Modifier.height(4.dp))
        }
    }
}
