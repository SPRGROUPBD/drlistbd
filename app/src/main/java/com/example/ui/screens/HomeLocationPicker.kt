package com.example.ui.screens

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.animation.core.tween
import androidx.compose.ui.layout.ContentScale
import coil.compose.AsyncImage
import com.example.data.seed.DistrictInfo
import com.example.data.seed.SeedData
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.ResolvedHomeSliderSlide
import com.example.ui.ads.decodeAdImageModel
import com.example.ui.components.ExternalImageAvatar
import com.example.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Locale

object DistrictAutoDetector {
    fun hasLocationPermission(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    suspend fun detectBangladeshDistrict(context: Context): DistrictInfo? = withContext(Dispatchers.IO) {
        if (!hasLocationPermission(context)) return@withContext null
        runCatching {
            val lm = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return@runCatching null
            val providers = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER
            )
            var bestLocation: Location? = null
            for (provider in providers) {
                val loc = runCatching {
                    if (lm.isProviderEnabled(provider)) lm.getLastKnownLocation(provider) else null
                }.getOrNull() ?: continue
                if (bestLocation == null || loc.time > bestLocation.time) {
                    bestLocation = loc
                }
            }
            val loc = bestLocation ?: return@runCatching null
            if (!Geocoder.isPresent()) return@runCatching null
            val geocoder = Geocoder(context, Locale.ENGLISH)
            @Suppress("DEPRECATION")
            val addresses = runCatching {
                geocoder.getFromLocation(loc.latitude, loc.longitude, 3)
            }.getOrNull().orEmpty()

            val candidateTokens = addresses.flatMap { addr ->
                listOfNotNull(
                    addr.subAdminArea,
                    addr.locality,
                    addr.adminArea,
                    addr.getAddressLine(0)
                )
            }.map { it.lowercase(Locale.ENGLISH) }

            SeedData.allDistricts.firstOrNull { dist ->
                val en = dist.nameEn.lowercase(Locale.ENGLISH)
                candidateTokens.any { token -> token.contains(en) }
            }
        }.getOrNull()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DistrictPickerBottomSheet(
    lang: AppLang,
    selectedDistrictEn: String?,
    onSelectDistrict: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    var selectedDivisionEn by remember { mutableStateOf<String?>(null) }

    val filteredDistricts = remember(selectedDivisionEn) {
        if (selectedDivisionEn == null) {
            SeedData.allDistricts
        } else {
            SeedData.bangladeshDivisions
                .firstOrNull { it.nameEn == selectedDivisionEn }
                ?.districts ?: SeedData.allDistricts
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = colors.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("home_district_picker_sheet")
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocationOn,
                        contentDescription = null,
                        tint = Color(0xFF10B96B)
                    )
                    Text(
                        text = tr(lang, "আপনার জেলা নির্বাচন করুন", "Select Your District"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = colors.onSurface
                    )
                }
                if (selectedDistrictEn != null) {
                    TextButton(
                        onClick = {
                            onSelectDistrict(null)
                            onDismiss()
                        },
                        modifier = Modifier.testTag("district_picker_all_bd_btn")
                    ) {
                        Text(
                            text = tr(lang, "সারা বাংলাদেশ", "All Bangladesh"),
                            color = Color(0xFF10B96B),
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.testTag("district_picker_divisions")
            ) {
                item {
                    FilterChip(
                        selected = selectedDivisionEn == null,
                        onClick = { selectedDivisionEn = null },
                        label = { Text(tr(lang, "সব বিভাগ (৬৪)", "All (64)"), style = MaterialTheme.typography.labelSmall) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B96B),
                            selectedLabelColor = Color.White
                        )
                    )
                }
                items(SeedData.bangladeshDivisions, key = { it.nameEn }) { div ->
                    FilterChip(
                        selected = selectedDivisionEn == div.nameEn,
                        onClick = {
                            selectedDivisionEn = if (selectedDivisionEn == div.nameEn) null else div.nameEn
                        },
                        label = {
                            Text(
                                text = tr(lang, div.nameBn.removeSuffix(" বিভাগ"), div.nameEn),
                                style = MaterialTheme.typography.labelSmall
                            )
                        },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Color(0xFF10B96B),
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 360.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                item(key = "all_bangladesh_option") {
                    val isSelected = selectedDistrictEn == null
                    Surface(
                        onClick = {
                            onSelectDistrict(null)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF10B96B).copy(alpha = 0.14f) else colors.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B96B) else colors.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Rounded.Public, contentDescription = null, tint = Color(0xFF10B96B), modifier = Modifier.size(18.dp))
                                Text(
                                    text = tr(lang, "সারা বাংলাদেশ (৬৪ জেলা)", "All Bangladesh (64 Districts)"),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = colors.onSurface
                                )
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B96B), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                items(filteredDistricts, key = { "${it.divisionEn}_${it.nameEn}" }) { dist ->
                    val isSelected = selectedDistrictEn.equals(dist.nameEn, ignoreCase = true)
                    Surface(
                        onClick = {
                            onSelectDistrict(dist.nameEn)
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (isSelected) Color(0xFF10B96B).copy(alpha = 0.14f) else colors.surface,
                        border = BorderStroke(1.dp, if (isSelected) Color(0xFF10B96B) else colors.outline)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                Icon(Icons.Outlined.LocationOn, contentDescription = null, tint = Color(0xFF10B96B), modifier = Modifier.size(18.dp))
                                Column {
                                    Text(
                                        text = tr(lang, dist.nameBn, dist.nameEn),
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = colors.onSurface
                                    )
                                    Text(
                                        text = "${dist.nameEn}, ${dist.divisionEn}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colors.onSurfaceVariant
                                    )
                                }
                            }
                            if (isSelected) {
                                Icon(Icons.Rounded.CheckCircle, contentDescription = null, tint = Color(0xFF10B96B), modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Admin-managed Auto-Advancing Home Slider with pagination dots directly below.
 * Strictly renders ONLY slides added by the Admin (or Admin-approved slider ads);
 * displays nothing when no Admin slides are configured.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeHeroBannerSlider(
    slides: List<ResolvedHomeSliderSlide>,
    autoSlideIntervalMs: Long,
    lang: AppLang,
    onSlideVisible: (ResolvedHomeSliderSlide) -> Unit,
    onSlideClick: (ResolvedHomeSliderSlide) -> Unit,
    modifier: Modifier = Modifier
) {
    if (slides.isEmpty()) return

    val isDark = LocalIsDarkTheme.current
    val emeraldAccent = Color(0xFF10B96B)

    val isSingleSlide = slides.size == 1
    val virtualPageCount = if (isSingleSlide) 1 else Int.MAX_VALUE
    val initialPage = if (isSingleSlide) {
        0
    } else {
        val mid = Int.MAX_VALUE / 2
        mid - (mid % slides.size)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { virtualPageCount }
    )
    val isDragged by pagerState.interactionSource.collectIsDraggedAsState()

    var isAppForeground by remember { mutableStateOf(true) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> isAppForeground = true
                Lifecycle.Event.ON_PAUSE, Lifecycle.Event.ON_STOP -> isAppForeground = false
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val currentSlideIndex = if (isSingleSlide) 0 else (pagerState.currentPage % slides.size)
    LaunchedEffect(currentSlideIndex, slides) {
        slides.getOrNull(currentSlideIndex)?.let(onSlideVisible)
    }

    LaunchedEffect(slides.size, isDragged, isAppForeground, autoSlideIntervalMs) {
        if (!isSingleSlide && !isDragged && isAppForeground) {
            while (true) {
                delay(autoSlideIntervalMs.coerceIn(2000L, 15000L))
                if (!pagerState.isScrollInProgress && isAppForeground && !isDragged) {
                    pagerState.animateScrollToPage(
                        page = pagerState.currentPage + 1,
                        animationSpec = tween(durationMillis = 450)
                    )
                }
            }
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        HorizontalPager(
            state = pagerState,
            userScrollEnabled = !isSingleSlide,
            contentPadding = PaddingValues(horizontal = 14.dp),
            pageSpacing = 10.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("home_compact_auto_slider")
        ) { page ->
            val item = slides[if (isSingleSlide) 0 else page % slides.size]
            val title = item.title.ifBlank { tr(lang, "আপনার স্বাস্থ্য\nআমাদের অগ্রাধিকার", "Your Health\nOur Priority") }
            val subtitle = item.subtitle.ifBlank { item.description }.ifBlank { tr(lang, "সেরা ডাক্তার খুঁজুন", "Find the Best Doctor") }
            val badgeLabel = item.categoryBadge.ifBlank { tr(lang, "ফিচার্ড", "FEATURED") }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = if (isDark) {
                                listOf(Color(0xFF13263B), Color(0xFF1A3552))
                            } else {
                                listOf(Color(0xFFE7F5FF), Color(0xFFCDE9FF))
                            }
                        )
                    )
                    .border(
                        width = 1.dp,
                        color = if (isDark) Color(0xFF284764) else Color(0xFFBDDDF7),
                        shape = RoundedCornerShape(20.dp)
                    )
                    .clickable { onSlideClick(item) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Surface(
                                color = if (isDark) Color(0xFF38BDF8).copy(alpha = 0.18f) else Color(0xFF0C3B82).copy(alpha = 0.12f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = badgeLabel,
                                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                                    fontWeight = FontWeight.Bold,
                                    color = if (isDark) Color(0xFF7DD3FC) else Color(0xFF0C3B82),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                )
                            }
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp, lineHeight = 20.sp),
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFFF1F7FF) else Color(0xFF0B2C66),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = subtitle,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isDark) Color(0xFFB5D2EC) else Color(0xFF154275),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }

                        Surface(
                            color = emeraldAccent,
                            shape = RoundedCornerShape(50),
                            shadowElevation = 2.dp
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = tr(lang, "বিস্তারিত দেখুন", "View Details"),
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .width(102.dp)
                            .height(126.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(if (isDark) Color(0xFF0F2032) else Color.White.copy(alpha = 0.65f))
                            .border(
                                1.dp,
                                if (isDark) Color(0xFF284764) else Color.White,
                                RoundedCornerShape(14.dp)
                            )
                    ) {
                        val decodedModel = remember(item.imageUrl) { decodeAdImageModel(item.imageUrl) }
                        var slideImgFailed by remember(item.slideId, item.imageUrl) { mutableStateOf(false) }
                        if (decodedModel != null && !slideImgFailed) {
                            AsyncImage(
                                model = decodedModel,
                                contentDescription = title,
                                contentScale = ContentScale.Crop,
                                onError = { slideImgFailed = true },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            ExternalImageAvatar(
                                imageUrl = item.imageUrl,
                                contentDescription = title,
                                fallbackIcon = Icons.Rounded.MedicalServices,
                                size = 126.dp,
                                shape = RoundedCornerShape(14.dp)
                            )
                        }
                    }
                }
            }
        }

        if (slides.size > 1) {
            Spacer(Modifier.height(8.dp))
            SliderDotIndicators(
                count = slides.size.coerceAtMost(6),
                activeIndex = currentSlideIndex % slides.size.coerceAtMost(6),
                activeColor = emeraldAccent,
                inactiveColor = if (isDark) Color(0xFF31445A) else Color(0xFFC5D8EC)
            )
        }
    }
}

@Composable
private fun SliderDotIndicators(
    count: Int,
    activeIndex: Int,
    activeColor: Color,
    inactiveColor: Color
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        repeat(count) { idx ->
            val active = idx == activeIndex
            Box(
                modifier = Modifier
                    .height(7.dp)
                    .width(if (active) 18.dp else 7.dp)
                    .clip(CircleShape)
                    .background(if (active) activeColor else inactiveColor)
            )
        }
    }
}
