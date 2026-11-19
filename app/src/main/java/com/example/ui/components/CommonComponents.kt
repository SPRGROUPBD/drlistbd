package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.data.local.SpecialtyEntity
import com.example.data.model.DoctorFullProfile
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.theme.*

@Composable
fun ExternalImageAvatar(
    imageUrl: String,
    contentDescription: String,
    fallbackIcon: ImageVector = Icons.Rounded.MedicalServices,
    size: Dp = 64.dp,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    modifier: Modifier = Modifier
) {
    val validUrl = imageUrl.trim().let { it.startsWith("http://") || it.startsWith("https://") }
    var loadError by remember(imageUrl) { mutableStateOf(false) }

    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(
                Brush.linearGradient(listOf(MedicalBlueLight, MedicalTealLight))
            ),
        contentAlignment = Alignment.Center
    ) {
        if (validUrl && !loadError) {
            AsyncImage(
                model = ImageRequest.Builder(LocalContext.current)
                    .data(imageUrl.trim())
                    .crossfade(true)
                    .build(),
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                onError = { loadError = true },
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                imageVector = fallbackIcon,
                contentDescription = contentDescription,
                tint = MedicalBlueDark,
                modifier = Modifier.size(size * 0.48f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicalTopBar(
    title: String,
    lang: AppLang,
    unreadNotifications: Int,
    onToggleLang: () -> Unit,
    onNotificationsClick: () -> Unit,
    onBackClick: (() -> Unit)? = null
) {
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = SurfaceWhite,
            titleContentColor = TextPrimary
        ),
        navigationIcon = {
            if (onBackClick != null) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.testTag("top_back_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ArrowBackIosNew,
                        contentDescription = "Back",
                        tint = TextPrimary
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .padding(start = 12.dp)
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(MedicalBlue, MedicalTeal))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.LocalHospital,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        },
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        actions = {
            // Bilingual toggle pill
            Surface(
                onClick = onToggleLang,
                shape = RoundedCornerShape(50),
                color = MedicalBlueLight,
                modifier = Modifier
                    .padding(end = 4.dp)
                    .heightIn(min = 36.dp)
                    .testTag("lang_toggle_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Translate,
                        contentDescription = "Switch language",
                        tint = MedicalBlueDark,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (lang == AppLang.BN) "EN" else "বাং",
                        style = MaterialTheme.typography.labelLarge,
                        color = MedicalBlueDark
                    )
                }
            }

            IconButton(
                onClick = onNotificationsClick,
                modifier = Modifier.testTag("notifications_button")
            ) {
                BadgedBox(
                    badge = {
                        if (unreadNotifications > 0) {
                            Badge(containerColor = RoseAlert) {
                                Text(unreadNotifications.toString(), color = Color.White)
                            }
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Notifications,
                        contentDescription = "Notifications",
                        tint = TextPrimary
                    )
                }
            }
        }
    )
}

@Composable
fun StatusPill(
    text: String,
    bgColor: Color,
    textColor: Color,
    icon: ImageVector? = null
) {
    Surface(
        shape = RoundedCornerShape(50),
        color = bgColor
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = textColor,
                    modifier = Modifier.size(12.dp)
                )
                Spacer(Modifier.width(4.dp))
            }
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = textColor,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DoctorCard(
    item: DoctorFullProfile,
    lang: AppLang,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val doc = item.doctor
    val availableToday = item.isAvailableToday()
    val primaryChamber = item.chambers.firstOrNull { it.chamber.isActive }?.chamber
    val specName = item.specialty?.let { tr(lang, it.nameBn, it.nameEn) } ?: doc.subSpecialty

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, OutlineSoft, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("doctor_card_${doc.id}"),
        color = SurfaceWhite,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 0.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                ExternalImageAvatar(
                    imageUrl = doc.photoUrl,
                    contentDescription = tr(lang, doc.fullNameBn, doc.fullNameEn),
                    fallbackIcon = Icons.Rounded.Person,
                    size = 66.dp,
                    shape = RoundedCornerShape(16.dp)
                )

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tr(lang, doc.fullNameBn, doc.fullNameEn),
                            style = MaterialTheme.typography.titleMedium,
                            color = TextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = onToggleFavorite,
                            modifier = Modifier
                                .size(36.dp)
                                .testTag("favorite_btn_${doc.id}")
                        ) {
                            Icon(
                                imageVector = if (isFavorite) Icons.Rounded.Favorite else Icons.Outlined.FavoriteBorder,
                                contentDescription = AppStrings.saveFavorite(lang),
                                tint = if (isFavorite) RoseAlert else TextMuted,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    if (doc.qualification.isNotBlank()) {
                        Text(
                            text = doc.qualification,
                            style = MaterialTheme.typography.labelMedium,
                            color = MedicalTealDark,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Text(
                        text = if (doc.subSpecialty.isNotBlank()) "$specName • ${doc.subSpecialty}" else specName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MedicalBlueDark,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (doc.currentHospital.isNotBlank() || doc.currentDesignation.isNotBlank()) {
                        val workLine = listOf(doc.currentDesignation, doc.currentHospital)
                            .filter { it.isNotBlank() }
                            .joinToString(" • ")
                        Text(
                            text = workLine,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = SurfaceMuted)
            Spacer(Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    if (availableToday) {
                        StatusPill(
                            text = AppStrings.availableToday(lang),
                            bgColor = EmeraldSoft,
                            textColor = Color(0xFF065F46),
                            icon = Icons.Rounded.FiberManualRecord
                        )
                    }
                    if (primaryChamber != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Outlined.LocationOn,
                                contentDescription = null,
                                tint = MedicalTeal,
                                modifier = Modifier.size(14.dp)
                            )
                            Spacer(Modifier.width(2.dp))
                            Text(
                                text = "${primaryChamber.upazila.ifBlank { primaryChamber.locationArea }}, ${primaryChamber.district}",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (doc.totalExperienceYears > 0) {
                    Text(
                        text = tr(lang, "${doc.totalExperienceYears} বছর অভিজ্ঞতা", "${doc.totalExperienceYears} yrs exp"),
                        style = MaterialTheme.typography.labelMedium,
                        color = TextMuted
                    )
                }
            }
        }
    }
}

fun specialtyIcon(iconKey: String): ImageVector = when (iconKey.lowercase()) {
    "heart" -> Icons.Rounded.Favorite
    "stethoscope" -> Icons.Rounded.MedicalServices
    "child" -> Icons.Rounded.ChildCare
    "female" -> Icons.Rounded.PregnantWoman
    "bone" -> Icons.Rounded.AccessibilityNew
    "brain" -> Icons.Rounded.Psychology
    "skin" -> Icons.Rounded.Spa
    "ear" -> Icons.Rounded.Hearing
    "eye" -> Icons.Rounded.Visibility
    "scalpel" -> Icons.Rounded.Healing
    "mind" -> Icons.Rounded.SelfImprovement
    "tooth" -> Icons.Rounded.Mood
    else -> Icons.Rounded.LocalHospital
}

@Composable
fun SpecialtyStrip(
    specialties: List<SpecialtyEntity>,
    selectedId: String?,
    lang: AppLang,
    onSelect: (String?) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(horizontal = 16.dp)
    ) {
        items(specialties, key = { it.id }) { spec ->
            val selected = spec.id == selectedId
            Surface(
                onClick = { onSelect(if (selected) null else spec.id) },
                shape = RoundedCornerShape(16.dp),
                color = if (selected) MedicalBlue else SurfaceWhite,
                border = BorderStroke(1.dp, if (selected) MedicalBlue else OutlineSoft),
                modifier = Modifier
                    .widthIn(min = 96.dp, max = 128.dp)
                    .testTag("specialty_chip_${spec.id}")
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color.White.copy(alpha = 0.2f) else MedicalBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = specialtyIcon(spec.iconKey),
                            contentDescription = tr(lang, spec.nameBn, spec.nameEn),
                            tint = if (selected) Color.White else MedicalBlueDark,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tr(lang, spec.nameBn, spec.nameEn),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (selected) Color.White else TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = TextPrimary,
            fontWeight = FontWeight.Bold
        )
        if (actionLabel != null && onAction != null) {
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MedicalBlue)
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MedicalBlue,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        shape = RoundedCornerShape(20.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft)
    ) {
        Column(
            modifier = Modifier.padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MedicalBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MedicalBlue, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, color = TextPrimary)
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
            if (actionText != null && onAction != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(onClick = onAction) { Text(actionText) }
            }
        }
    }
}
