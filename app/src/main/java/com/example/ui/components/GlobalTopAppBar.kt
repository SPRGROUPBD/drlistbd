package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.DiagnosticCenterEntity
import com.example.data.local.HospitalEntity
import com.example.data.local.NotificationEntity
import com.example.data.model.AutoSyncStatus
import com.example.data.model.DoctorFullProfile
import com.example.data.model.GlobalSearchEngine
import com.example.data.model.GlobalSearchSuggestion
import com.example.data.model.GlobalSearchType
import com.example.data.model.SyncUiState
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun GlobalTopAppBar(
    lang: AppLang,
    syncState: SyncUiState,
    unreadNotifications: Int,
    searchExpanded: Boolean,
    searchQuery: String,
    doctors: List<DoctorFullProfile>,
    hospitals: List<HospitalEntity>,
    diagnosticCenters: List<DiagnosticCenterEntity>,
    onLogoClick: () -> Unit,
    onToggleSearch: () -> Unit,
    onSearchQueryChange: (String) -> Unit,
    onSearchSubmit: (String) -> Unit,
    onSuggestionSelected: (GlobalSearchSuggestion) -> Unit,
    onSyncIconClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    val suggestions by remember(searchQuery, doctors, hospitals, diagnosticCenters, lang) {
        derivedStateOf {
            GlobalSearchEngine.search(
                query = searchQuery,
                doctors = doctors,
                hospitals = hospitals,
                diagnostics = diagnosticCenters,
                lang = lang
            )
        }
    }

    LaunchedEffect(searchExpanded) {
        if (searchExpanded) {
            runCatching { focusRequester.requestFocus() }
        }
    }

    val colors = MaterialTheme.colorScheme
    val isDark = LocalIsDarkTheme.current
    val headerBg = if (isDark) Color(0xFF0A121C) else LightSurfaceWhite
    val iconPillBg = if (isDark) Color(0xFF172536) else Color(0xFFEBF3FA)
    val iconTint = if (isDark) Color(0xFFE6F0FA) else Color(0xFF0F2C59)
    val brandGreen = Color(0xFF10B96B)
    val brandBlue = if (isDark) Color(0xFFFFFFFF) else Color(0xFF0C2E66)

    val isSyncingNow = syncState.status == AutoSyncStatus.SYNCING

    Surface(
        color = headerBg,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_top_app_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Fixed single-row header: Brand Logo + Title on extreme far left, 3 icons on extreme far right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(62.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT SIDE: Existing Doctor List BD App Logo + Brand Wordmark -> navigates to Home
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClick = onLogoClick)
                        .padding(end = 6.dp)
                        .testTag("global_header_logo"),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isDark) Color(0xFF132234) else SurfaceWhite,
                        border = BorderStroke(1.dp, if (isDark) Color(0xFF23384F) else MedicalBlueLight),
                        shadowElevation = 1.dp,
                        modifier = Modifier.size(40.dp)
                    ) {
                        Image(
                            painter = painterResource(id = R.drawable.doc_dir_icon_1794967683712),
                            contentDescription = tr(lang, "Doctor List BD হোম", "Doctor List BD Home"),
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(RoundedCornerShape(12.dp))
                        )
                    }
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "Doctor ",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = brandBlue,
                                maxLines = 1
                            )
                            Text(
                                text = "List BD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = brandGreen,
                                maxLines = 1
                            )
                        }
                        Text(
                            text = tr(lang, "আপনার সঠিক ডাক্তার খুঁজুন", "Find the right doctor for you"),
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                // RIGHT SIDE (STRICT ORDER): 1. Search  2. Auto Sync  3. Notification Bell
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. Search Icon
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("global_header_search_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (searchExpanded) MedicalBlueLight else iconPillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = tr(lang, "অনুসন্ধান", "Search"),
                                tint = if (searchExpanded) MedicalBlueDark else iconTint,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // 2. Auto Sync Icon — spins ONLY while loading new data, stops immediately once loaded
                    IconButton(
                        onClick = onSyncIconClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("global_header_sync_btn")
                    ) {
                        val isFailure = syncState.status == AutoSyncStatus.FAILURE
                        val isSuccess = syncState.status == AutoSyncStatus.SUCCESS

                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(iconPillBg),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isSyncingNow) {
                                SpinningSyncIcon(
                                    contentDescription = tr(lang, "নতুন ডাটা লোড হচ্ছে...", "Syncing new data..."),
                                    tint = MedicalBlue
                                )
                            } else {
                                Icon(
                                    imageVector = if (isFailure) Icons.Rounded.SyncProblem else Icons.Rounded.Sync,
                                    contentDescription = tr(lang, "অটো সিঙ্ক স্ট্যাটাস", "Auto Sync Status"),
                                    tint = when {
                                        isFailure -> RoseAlert
                                        isSuccess -> brandGreen
                                        else -> iconTint
                                    },
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            val dotColor = when {
                                isSyncingNow -> MedicalBlue
                                isFailure -> RoseAlert
                                isSuccess -> brandGreen
                                !syncState.isOnline -> AmberPending
                                else -> Color.Transparent
                            }
                            if (dotColor != Color.Transparent) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .offset(x = (-1).dp, y = 1.dp)
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(headerBg)
                                        .padding(1.dp)
                                        .clip(CircleShape)
                                        .background(dotColor)
                                )
                            }
                        }
                    }

                    // 3. Notification Bell Icon with live unread count badge
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("global_header_notification_btn")
                    ) {
                        BadgedBox(
                            badge = {
                                if (unreadNotifications > 0) {
                                    Badge(containerColor = RoseAlert) {
                                        Text(
                                            text = if (unreadNotifications > 99) "99+" else unreadNotifications.toString(),
                                            color = Color.White,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    }
                                }
                            }
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(iconPillBg),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (unreadNotifications > 0) Icons.Rounded.Notifications else Icons.Outlined.Notifications,
                                    contentDescription = tr(lang, "নোটিফিকেশন সেন্টার", "Notification Center"),
                                    tint = iconTint,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Expandable Search Box + Live Categorized Suggestions directly below the header
            AnimatedVisibility(
                visible = searchExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(headerBg)
                        .padding(start = 12.dp, end = 12.dp, bottom = 10.dp)
                        .testTag("global_search_drawer")
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        placeholder = {
                            Text(
                                text = tr(
                                    lang,
                                    "ডাক্তার, হাসপাতাল, ডায়াগনস্টিক বা চেম্বার খুঁজুন...",
                                    "Search doctors, hospitals, diagnostics & chambers..."
                                ),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodyMedium
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Rounded.Search,
                                contentDescription = null,
                                tint = colors.primary
                            )
                        },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(
                                    onClick = { onSearchQueryChange("") },
                                    modifier = Modifier.testTag("global_search_clear_btn")
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Cancel,
                                        contentDescription = tr(lang, "মুছুন", "Clear"),
                                        tint = colors.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceVariant,
                            unfocusedContainerColor = colors.surfaceVariant,
                            focusedBorderColor = colors.primary,
                            unfocusedBorderColor = colors.outline,
                            focusedTextColor = colors.onSurface,
                            unfocusedTextColor = colors.onSurface
                        ),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                if (searchQuery.trim().length >= GlobalSearchEngine.MIN_QUERY_LENGTH) {
                                    keyboardController?.hide()
                                    onSearchSubmit(searchQuery.trim())
                                }
                            }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester)
                            .testTag("global_search_input")
                    )

                    val trimmed = searchQuery.trim()
                    if (trimmed.length < GlobalSearchEngine.MIN_QUERY_LENGTH) {
                        Text(
                            text = tr(
                                lang,
                                "লাইভ সাজেশন দেখতে কমপক্ষে ২টি অক্ষর লিখুন",
                                "Type at least 2 characters for live suggestions"
                            ),
                            style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                        )
                    } else if (suggestions.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = colors.surfaceVariant,
                            border = BorderStroke(1.dp, colors.outline),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = colors.onSurfaceVariant)
                                Text(
                                    text = tr(
                                        lang,
                                        "\"$trimmed\" এর জন্য কোনো ডাক্তার, হাসপাতাল, ডায়াগনস্টিক বা চেম্বার পাওয়া যায়নি।",
                                        "No doctors, hospitals, diagnostic centers or chambers found for \"$trimmed\"."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.onSurfaceVariant
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = colors.surface,
                            border = BorderStroke(1.dp, colors.outline),
                            shadowElevation = 4.dp,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                                .heightIn(max = 320.dp)
                                .testTag("global_search_suggestions_list")
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(vertical = 4.dp)
                            ) {
                                items(
                                    items = suggestions,
                                    key = { "${it.type.name}_${it.id}" }
                                ) { item ->
                                    GlobalSearchSuggestionRow(
                                        item = item,
                                        lang = lang,
                                        onClick = {
                                            keyboardController?.hide()
                                            onSuggestionSelected(item)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 1.dp, color = colors.outlineVariant)
        }
    }
}

@Composable
private fun SpinningSyncIcon(
    contentDescription: String,
    tint: Color
) {
    val infiniteTransition = rememberInfiniteTransition(label = "global_sync_spin_active")
    val syncRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sync_rotation_active"
    )
    Icon(
        imageVector = Icons.Rounded.Sync,
        contentDescription = contentDescription,
        tint = tint,
        modifier = Modifier
            .size(20.dp)
            .rotate(syncRotation)
    )
}

@Composable
private fun GlobalSearchSuggestionRow(
    item: GlobalSearchSuggestion,
    lang: AppLang,
    onClick: () -> Unit
) {
    val (icon, badgeLabel, badgeBg, badgeFg) = when (item.type) {
        GlobalSearchType.DOCTOR -> Quad(
            Icons.Rounded.MedicalServices,
            tr(lang, "ডাক্তার", "Doctor"),
            MedicalBlueLight,
            MedicalBlueDark
        )
        GlobalSearchType.HOSPITAL -> Quad(
            Icons.Rounded.LocalHospital,
            tr(lang, "হাসপাতাল", "Hospital"),
            MedicalTealLight,
            MedicalTealDark
        )
        GlobalSearchType.DIAGNOSTIC -> Quad(
            Icons.Rounded.Biotech,
            tr(lang, "ডায়াগনস্টিক", "Diagnostic"),
            EmeraldSoft,
            Color(0xFF065F46)
        )
        GlobalSearchType.CHAMBER -> Quad(
            Icons.Rounded.LocationOn,
            tr(lang, "চেম্বার", "Chamber"),
            AmberSoft,
            Color(0xFF92400E)
        )
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp)
            .testTag("search_suggestion_${item.type.name.lowercase()}_${item.id}"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(badgeBg),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = badgeLabel,
                tint = badgeFg,
                modifier = Modifier.size(20.dp)
            )
        }

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                StatusPill(
                    text = badgeLabel,
                    bgColor = badgeBg,
                    textColor = badgeFg
                )
            }
            if (item.subtitle.isNotBlank()) {
                Text(
                    text = item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (item.secondaryInfo.isNotBlank() && item.secondaryInfo != item.subtitle) {
                Text(
                    text = item.secondaryInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(18.dp)
        )
    }
    HorizontalDivider(color = SurfaceMuted, modifier = Modifier.padding(horizontal = 12.dp))
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncStatusBottomSheet(
    lang: AppLang,
    syncState: SyncUiState,
    doctorsCount: Int,
    hospitalsCount: Int,
    diagnosticsCount: Int,
    onSyncNow: () -> Unit,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .testTag("sync_status_sheet"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MedicalBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (syncState.status) {
                                AutoSyncStatus.FAILURE -> Icons.Rounded.SyncProblem
                                AutoSyncStatus.SUCCESS -> Icons.Rounded.CloudDone
                                AutoSyncStatus.SYNCING -> Icons.Rounded.CloudSync
                                AutoSyncStatus.IDLE -> Icons.Rounded.Sync
                            },
                            contentDescription = null,
                            tint = when (syncState.status) {
                                AutoSyncStatus.FAILURE -> RoseAlert
                                AutoSyncStatus.SUCCESS -> EmeraldAvailable
                                else -> MedicalBlueDark
                            }
                        )
                    }
                    Column {
                        Text(
                            text = tr(lang, "Firebase অটো সিঙ্ক", "Firebase Auto Sync"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = tr(
                                lang,
                                "ডাক্তার, হাসপাতাল, ডায়াগনস্টিক ও নোটিফিকেশন",
                                "Doctors, hospitals, diagnostics & notifications"
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }

                val (statusLabel, statusBg, statusFg) = when {
                    syncState.status == AutoSyncStatus.SYNCING -> Triple(
                        tr(lang, "সিঙ্ক হচ্ছে...", "Syncing..."),
                        MedicalBlueLight,
                        MedicalBlueDark
                    )
                    syncState.status == AutoSyncStatus.FAILURE -> Triple(
                        tr(lang, "ব্যর্থ", "Failed"),
                        RoseSoft,
                        RoseAlert
                    )
                    syncState.status == AutoSyncStatus.SUCCESS -> Triple(
                        tr(lang, "সফল", "Synced"),
                        EmeraldSoft,
                        Color(0xFF065F46)
                    )
                    !syncState.isOnline -> Triple(
                        tr(lang, "অফলাইন", "Offline"),
                        AmberSoft,
                        Color(0xFF92400E)
                    )
                    else -> Triple(
                        tr(lang, "প্রস্তুত", "Ready"),
                        SurfaceMuted,
                        TextSecondary
                    )
                }
                StatusPill(text = statusLabel, bgColor = statusBg, textColor = statusFg)
            }

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = BackgroundIce,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "সর্বশেষ সফল সিঙ্ক", "Last Successful Sync"),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = formatSyncTime(syncState.lastSuccessAtMs, lang),
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, "সংযোগ অবস্থা", "Connection"),
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                        Text(
                            text = if (syncState.isOnline)
                                tr(lang, "অনলাইন (স্বয়ংক্রিয় সিঙ্ক চালু)", "Online (Auto Sync Active)")
                            else
                                tr(lang, "অফলাইন (লোকাল ক্যাশ)", "Offline (Cached Data)"),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = if (syncState.isOnline) MedicalTealDark else Color(0xFF92400E)
                        )
                    }

                    HorizontalDivider(color = OutlineSoft)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        SyncMiniStat(tr(lang, "ডাক্তার", "Doctors"), doctorsCount.toString())
                        SyncMiniStat(tr(lang, "হাসপাতাল", "Hospitals"), hospitalsCount.toString())
                        SyncMiniStat(tr(lang, "ডায়াগনস্টিক", "Diagnostics"), diagnosticsCount.toString())
                    }
                }
            }

            if (syncState.status == AutoSyncStatus.FAILURE && !syncState.lastError.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = RoseSoft,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = RoseAlert, modifier = Modifier.size(18.dp))
                        Text(
                            text = syncState.lastError,
                            style = MaterialTheme.typography.bodySmall,
                            color = RoseAlert
                        )
                    }
                }
            }

            Button(
                onClick = onSyncNow,
                enabled = syncState.status != AutoSyncStatus.SYNCING,
                colors = ButtonDefaults.buttonColors(containerColor = MedicalBlue),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("sync_now_button")
            ) {
                if (syncState.status == AutoSyncStatus.SYNCING) {
                    CircularProgressIndicator(
                        color = Color.White,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(tr(lang, "সিঙ্ক হচ্ছে...", "Syncing Firebase Data..."))
                } else {
                    Icon(Icons.Rounded.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = tr(lang, "এখনই সিঙ্ক করুন (Sync Now)", "Sync Now"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun SyncMiniStat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MedicalBlueDark)
        Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationCenterBottomSheet(
    lang: AppLang,
    notifications: List<NotificationEntity>,
    isSyncing: Boolean,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
    onRefresh: () -> Unit,
    onOpenNotification: (NotificationEntity) -> Unit = {},
    onDismiss: () -> Unit
) {
    val unreadCount = notifications.count { !it.isRead }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = SurfaceWhite
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp)
                .testTag("notification_center_sheet")
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Notifications,
                        contentDescription = null,
                        tint = MedicalBlueDark
                    )
                    Text(
                        text = tr(lang, "নোটিফিকেশন সেন্টার", "Notification Center"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    if (unreadCount > 0) {
                        StatusPill(
                            text = tr(lang, "$unreadCount নতুন", "$unreadCount unread"),
                            bgColor = RoseSoft,
                            textColor = RoseAlert
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (unreadCount > 0) {
                        TextButton(
                            onClick = onMarkAllRead,
                            modifier = Modifier.testTag("mark_all_notifications_read_btn")
                        ) {
                            Text(
                                text = tr(lang, "সব পঠিত", "Mark all read"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MedicalBlue
                            )
                        }
                    }
                    IconButton(
                        onClick = onRefresh,
                        enabled = !isSyncing,
                        modifier = Modifier.testTag("refresh_notifications_btn")
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = tr(lang, "রিফ্রেশ", "Refresh"),
                                tint = MedicalBlueDark
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = OutlineSoft)
            Spacer(Modifier.height(10.dp))

            if (notifications.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Outlined.Notifications,
                    title = tr(lang, "কোনো নোটিফিকেশন নেই", "No Notifications Yet"),
                    subtitle = tr(
                        lang,
                        "অ্যাডমিন থেকে নতুন কোনো আপডেট বা সতর্কতা পাঠানো হলে এখানে দেখা যাবে।",
                        "Firebase announcements and directory updates will appear here."
                    ),
                    actionText = tr(lang, "রিফ্রেশ করুন", "Refresh"),
                    onAction = onRefresh
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 440.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    contentPadding = PaddingValues(bottom = 12.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        val title = tr(
                            lang,
                            item.titleBn.ifBlank { item.titleEn },
                            item.titleEn.ifBlank { item.titleBn }
                        ).ifBlank { tr(lang, "নোটিফিকেশন", "Notification") }
                        val body = tr(
                            lang,
                            item.messageBn.ifBlank { item.messageEn },
                            item.messageEn.ifBlank { item.messageBn }
                        )

                        val typePill = when (item.messageType.uppercase()) {
                            "NEW_DOCTOR" -> Triple(
                                tr(lang, "নতুন ডাক্তার", "New Doctor"),
                                Color(0xFFDCFCE7),
                                Color(0xFF15803D)
                            )
                            "APP_UPDATE" -> Triple(
                                tr(lang, "অ্যাপ আপডেট", "App Update"),
                                Color(0xFFDBEAFE),
                                Color(0xFF1D4ED8)
                            )
                            else -> Triple(
                                tr(lang, "সাধারণ", "General"),
                                Color(0xFFEDE9FE),
                                Color(0xFF6D28D9)
                            )
                        }
                        val hasValidImage = item.imageUrl.trim().let {
                            it.startsWith("https://", ignoreCase = true) || it.startsWith("http://", ignoreCase = true)
                        }
                        val hasActionTarget = item.selectedDoctorId.isNotBlank() || item.deepLink.isNotBlank()

                        Surface(
                            onClick = {
                                if (!item.isRead) onMarkRead(item.id)
                                if (hasActionTarget) onOpenNotification(item)
                            },
                            shape = RoundedCornerShape(16.dp),
                            color = if (item.isRead) BackgroundIce else MedicalBlueLight.copy(alpha = 0.45f),
                            border = BorderStroke(
                                1.dp,
                                if (item.isRead) OutlineSoft else MedicalBlueContainer
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("notification_item_${item.id}")
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .padding(top = 5.dp)
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (item.isRead) OutlineSoft else MedicalBlue)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        StatusPill(
                                            text = typePill.first,
                                            bgColor = typePill.second,
                                            textColor = typePill.third
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = formatNotificationTime(item.createdAt, lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    Spacer(Modifier.height(6.dp))
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    if (body.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    if (hasValidImage) {
                                        Spacer(Modifier.height(8.dp))
                                        AsyncImage(
                                            model = item.imageUrl.trim(),
                                            contentDescription = title,
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(120.dp)
                                                .clip(RoundedCornerShape(12.dp))
                                        )
                                    }
                                    if (hasActionTarget) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = tr(lang, "বিস্তারিত দেখতে ট্যাপ করুন →", "Tap to view details →"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MedicalBlueDark,
                                            fontWeight = FontWeight.Bold
                                        )
                                    } else if (!item.isRead) {
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            text = tr(lang, "পঠিত হিসেবে চিহ্নিত করতে ট্যাপ করুন", "Tap to mark as read"),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MedicalBlueDark,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatSyncTime(timestampMs: Long, lang: AppLang): String {
    if (timestampMs <= 0L) {
        return tr(lang, "এখনো সিঙ্ক হয়নি", "Not synced yet")
    }
    val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    val formatted = formatter.format(Date(timestampMs))
    val diffSec = ((System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)) / 1000L
    val rel = when {
        diffSec < 60L -> tr(lang, "এইমাত্র", "Just now")
        diffSec < 3600L -> {
            val mins = diffSec / 60L
            tr(lang, "$mins মিনিট আগে", "$mins min ago")
        }
        diffSec < 86_400L -> {
            val hrs = diffSec / 3600L
            tr(lang, "$hrs ঘণ্টা আগে", "$hrs hr ago")
        }
        else -> ""
    }
    return if (rel.isNotBlank()) "$formatted ($rel)" else formatted
}

private fun formatNotificationTime(timestampMs: Long, lang: AppLang): String {
    if (timestampMs <= 0L) return ""
    val diffSec = ((System.currentTimeMillis() - timestampMs).coerceAtLeast(0L)) / 1000L
    return when {
        diffSec < 60L -> tr(lang, "এইমাত্র", "Just now")
        diffSec < 3600L -> tr(lang, "${diffSec / 60L} মি.", "${diffSec / 60L}m ago")
        diffSec < 86_400L -> tr(lang, "${diffSec / 3600L} ঘ.", "${diffSec / 3600L}h ago")
        else -> SimpleDateFormat("dd MMM", Locale.getDefault()).format(Date(timestampMs))
    }
}
