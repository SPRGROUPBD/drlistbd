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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    onToggleLang: () -> Unit = {},
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

    val isSyncingNow = syncState.status == AutoSyncStatus.SYNCING
    val syncRotation = if (isSyncingNow) {
        val infiniteTransition = rememberInfiniteTransition(label = "global_sync_spin")
        val angle by infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 360f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 900, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "sync_rotation"
        )
        angle
    } else {
        0f
    }

    Surface(
        color = SurfaceWhite,
        shadowElevation = 2.dp,
        modifier = modifier
            .fillMaxWidth()
            .testTag("global_top_app_bar")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
        ) {
            // Sticky white single-row header:
            // Left: Stethoscope app badge + "Doctor List BD" title
            // Right (exact order): 1) Search, 2) Translate ("EN"/"বাং"), 3) AutoSync, 4) Notification Bell with red dot
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // LEFT SIDE: Stethoscope logo badge + "Doctor List BD" title
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
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                Brush.linearGradient(
                                    colors = listOf(MedicalBlue, MedicalTeal)
                                )
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.MedicalServices,
                            contentDescription = tr(lang, "Doctor List BD লোগো", "Doctor List BD Logo"),
                            tint = Color.White,
                            modifier = Modifier.size(19.dp)
                        )
                    }
                    Text(
                        text = "Doctor List BD",
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // RIGHT SIDE (EXACT ORDER): 1. Search  2. Translate ("EN"/"বাং")  3. AutoSync  4. Notification Bell
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // 1. Search Button (toggles collapsible inline search bar filtering slider in real time)
                    IconButton(
                        onClick = onToggleSearch,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("global_header_search_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (searchExpanded) MintCardSurface else BackgroundIce),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (searchExpanded) Icons.Rounded.Close else Icons.Rounded.Search,
                                contentDescription = tr(lang, "অনুসন্ধান", "Search"),
                                tint = if (searchExpanded) MedicalBlue else TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }

                    // 2. Translate Toggle Button ("EN" / "বাং")
                    Surface(
                        onClick = onToggleLang,
                        shape = RoundedCornerShape(50),
                        color = MintCardSurface,
                        border = BorderStroke(1.dp, MintBorder),
                        modifier = Modifier
                            .height(34.dp)
                            .testTag("global_header_lang_btn")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Translate,
                                contentDescription = tr(lang, "ভাষা পরিবর্তন", "Switch language"),
                                tint = MedicalBlue,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = if (lang == AppLang.BN) "EN" else "বাং",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MedicalBlue
                            )
                        }
                    }

                    // 3. AutoSync Button (rotates icon while syncing and refreshes slider feed)
                    IconButton(
                        onClick = onSyncIconClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("global_header_sync_btn")
                    ) {
                        val isFailure = syncState.status == AutoSyncStatus.FAILURE
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isSyncingNow) MedicalTealLight else BackgroundIce),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (isFailure) Icons.Rounded.SyncProblem else Icons.Rounded.Sync,
                                contentDescription = tr(lang, "অটো সিঙ্ক", "AutoSync"),
                                tint = when {
                                    isSyncingNow -> MedicalBlue
                                    isFailure -> RoseAlert
                                    else -> MedicalTeal
                                },
                                modifier = Modifier
                                    .size(19.dp)
                                    .rotate(syncRotation)
                            )
                        }
                    }

                    // 4. Notification Bell Button (with red dot indicator; opens Firebase Configuration modal)
                    IconButton(
                        onClick = onNotificationsClick,
                        modifier = Modifier
                            .size(44.dp)
                            .testTag("global_header_notification_btn")
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(BackgroundIce),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = if (unreadNotifications > 0) {
                                    Icons.Rounded.NotificationsActive
                                } else {
                                    Icons.Outlined.Notifications
                                },
                                contentDescription = tr(lang, "নোটিফিকেশন ও Firebase কনফিগারেশন", "Notifications & Firebase Config"),
                                tint = TextPrimary,
                                modifier = Modifier.size(19.dp)
                            )
                            // Crisp red dot indicator on bell
                            Box(
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(top = 6.dp, end = 6.dp)
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(SurfaceWhite)
                                    .padding(1.dp)
                                    .clip(CircleShape)
                                    .background(RoseAlert)
                            )
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
                        .background(SurfaceWhite)
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
                                tint = MedicalBlue
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
                                        tint = TextMuted
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = BackgroundIce,
                            unfocusedContainerColor = BackgroundIce,
                            focusedBorderColor = MedicalBlue,
                            unfocusedBorderColor = OutlineSoft
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
                            color = TextMuted,
                            modifier = Modifier.padding(start = 6.dp, top = 6.dp)
                        )
                    } else if (suggestions.isEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = BackgroundIce,
                            border = BorderStroke(1.dp, OutlineSoft),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(Icons.Rounded.SearchOff, contentDescription = null, tint = TextMuted)
                                Text(
                                    text = tr(
                                        lang,
                                        "\"$trimmed\" এর জন্য কোনো ডাক্তার, হাসপাতাল, ডায়াগনস্টিক বা চেম্বার পাওয়া যায়নি।",
                                        "No doctors, hospitals, diagnostic centers or chambers found for \"$trimmed\"."
                                    ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }
                    } else {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = SurfaceWhite,
                            border = BorderStroke(1.dp, OutlineSoft),
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

            HorizontalDivider(thickness = 1.dp, color = OutlineSoft)
        }
    }
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

                        Surface(
                            onClick = { if (!item.isRead) onMarkRead(item.id) },
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
                                        Text(
                                            text = title,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                            color = TextPrimary,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            text = formatNotificationTime(item.createdAt, lang),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextMuted
                                        )
                                    }
                                    if (body.isNotBlank()) {
                                        Spacer(Modifier.height(4.dp))
                                        Text(
                                            text = body,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = TextSecondary
                                        )
                                    }
                                    if (!item.isRead) {
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

/**
 * Notifications Modal Bottom Sheet
 * Opened by tapping the Notification Bell button in the Top App Bar.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FirebaseConfigAndNotificationSheet(
    lang: AppLang,
    notifications: List<NotificationEntity>,
    onSyncNow: () -> Unit,
    onMarkRead: (String) -> Unit,
    onMarkAllRead: () -> Unit,
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
                .padding(bottom = 26.dp)
                .testTag("notifications_modal_sheet"),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(MintCardSurface),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Notifications,
                            contentDescription = null,
                            tint = MedicalBlue,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = tr(lang, "নোটিফিকেশন ও বিজ্ঞপ্তি", "Notifications & Notices"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Text(
                            text = tr(lang, "সাম্প্রতিক আপডেট ও বিজ্ঞপ্তি", "Recent updates and announcements"),
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMuted
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Rounded.Close, contentDescription = tr(lang, "বন্ধ করুন", "Close"), tint = TextSecondary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = tr(lang, "সাম্প্রতিক বিজ্ঞপ্তি", "Recent Notifications"),
                    style = MaterialTheme.typography.labelLarge,
                    color = TextSecondary
                )
                if (unreadCount > 0) {
                    TextButton(onClick = onMarkAllRead) {
                        Text(tr(lang, "সব পঠিত", "Mark all read"), color = MedicalBlue)
                    }
                }
            }

            if (notifications.isEmpty()) {
                EmptyStateCard(
                    icon = Icons.Outlined.Notifications,
                    title = tr(lang, "কোনো নোটিফিকেশন নেই", "No Notifications Yet"),
                    subtitle = tr(
                        lang,
                        "অ্যাডমিন থেকে নতুন কোনো আপডেট বা বিজ্ঞপ্তি পাঠানো হলে এখানে দেখা যাবে।",
                        "Firebase announcements and directory updates will appear here."
                    ),
                    actionText = tr(lang, "রিফ্রেশ করুন", "Refresh"),
                    onAction = onSyncNow
                )
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(notifications, key = { it.id }) { item ->
                        val title = tr(
                            lang,
                            item.titleBn.ifBlank { item.titleEn },
                            item.titleEn.ifBlank { item.titleBn }
                        ).ifBlank { tr(lang, "বিজ্ঞপ্তি", "Notice") }
                        val body = tr(
                            lang,
                            item.messageBn.ifBlank { item.messageEn },
                            item.messageEn.ifBlank { item.messageBn }
                        )
                        Surface(
                            onClick = { if (!item.isRead) onMarkRead(item.id) },
                            shape = RoundedCornerShape(14.dp),
                            color = if (item.isRead) BackgroundIce else MintCardSurface,
                            border = BorderStroke(1.dp, MintBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (item.isRead) FontWeight.Medium else FontWeight.Bold,
                                        color = TextPrimary,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = formatNotificationTime(item.createdAt, lang),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextMuted
                                    )
                                }
                                if (body.isNotBlank()) {
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = body,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextSecondary
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
