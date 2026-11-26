package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.notifications.InAppUpdateBannerState
import com.example.notifications.PushNotificationManager
import com.example.ui.theme.*

data class GeneralDialogPayload(
    val notificationId: String,
    val title: String,
    val body: String,
    val imageUrl: String = ""
)

@Composable
fun GeneralNotificationDialog(
    payload: GeneralDialogPayload,
    lang: AppLang,
    onDismiss: () -> Unit
) {
    val safeImage = PushNotificationManager.sanitizeHttpUrl(payload.imageUrl)
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            tonalElevation = 8.dp,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("general_notification_dialog")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MedicalBlueLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Campaign,
                            contentDescription = tr(lang, "সাধারণ নোটিফিকেশন", "General Notification"),
                            tint = MedicalBlueDark
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        StatusPill(
                            text = tr(lang, "Doctor List BD বিজ্ঞপ্তি", "Doctor List BD Notice"),
                            bgColor = Color(0xFFEDE9FE),
                            textColor = Color(0xFF6D28D9)
                        )
                    }
                }

                Spacer(Modifier.height(14.dp))

                Text(
                    text = payload.title.ifBlank { tr(lang, "গুরুত্বপূর্ণ বিজ্ঞপ্তি", "Announcement") },
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )

                Spacer(Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    if (safeImage.isNotBlank()) {
                        AsyncImage(
                            model = safeImage,
                            contentDescription = payload.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(165.dp)
                                .clip(RoundedCornerShape(16.dp))
                        )
                        Spacer(Modifier.height(12.dp))
                    }
                    if (payload.body.isNotBlank()) {
                        Text(
                            text = payload.body,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("general_notification_close_btn")
                ) {
                    Text(
                        text = tr(lang, "ঠিক আছে", "OK / Close"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun NotificationPermissionRationaleDialog(
    lang: AppLang,
    isPermanentlyDenied: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("notification_permission_rationale_dialog"),
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MedicalBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Rounded.NotificationsActive,
                    contentDescription = tr(lang, "নোটিফিকেশন অনুমতি", "Notification Permission"),
                    tint = MedicalBlueDark
                )
            }
        },
        title = {
            Text(
                text = tr(lang, "গুরুত্বপূর্ণ আপডেট পান", "Stay Updated with Alerts"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        },
        text = {
            Text(
                text = if (isPermanentlyDenied) {
                    tr(
                        lang,
                        "নতুন বিশেষজ্ঞ ডাক্তার যুক্ত হলে, অ্যাপের নতুন ভার্সন এলে এবং জরুরি বিজ্ঞপ্তি পেতে Android Settings থেকে Doctor List BD-এর নোটিফিকেশন চালু করুন।",
                        "Enable notifications in Android Settings to receive alerts for newly added doctors, app updates, and important announcements."
                    )
                } else {
                    tr(
                        lang,
                        "নতুন বিশেষজ্ঞ ডাক্তার যুক্ত হলে, অ্যাপ আপডেট এলে এবং গুরুত্বপূর্ণ স্বাস্থ্য বিজ্ঞপ্তি সঙ্গে সঙ্গে পেতে নোটিফিকেশন অনুমতি দিন।",
                        "Allow notifications so you never miss newly added doctors, app updates, and important announcements."
                    )
                },
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("notification_permission_allow_btn")
            ) {
                Text(
                    text = if (isPermanentlyDenied) {
                        tr(lang, "সেটিংস খুলুন", "Open Settings")
                    } else {
                        tr(lang, "চালু করুন", "Allow")
                    },
                    fontWeight = FontWeight.Bold
                )
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("notification_permission_later_btn")
            ) {
                Text(tr(lang, "পরে করব", "Not Now"))
            }
        },
        containerColor = SurfaceWhite,
        shape = RoundedCornerShape(22.dp)
    )
}

@Composable
fun FlexibleUpdateBanner(
    state: InAppUpdateBannerState,
    lang: AppLang,
    onInstallClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (!state.isDownloading && !state.isReadyToInstall) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MedicalBlueDark,
        shadowElevation = 4.dp,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .testTag("flexible_update_banner")
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = if (state.isReadyToInstall) Icons.Rounded.SystemUpdate else Icons.Rounded.Downloading,
                contentDescription = tr(lang, "অ্যাপ আপডেট", "App Update"),
                tint = Color.White
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (state.isReadyToInstall) {
                        tr(lang, "নতুন আপডেট ইনস্টলের জন্য প্রস্তুত", "Update ready to install")
                    } else {
                        tr(
                            lang,
                            "আপডেট ডাউনলোড হচ্ছে (${state.progressPercent}%)",
                            "Downloading update (${state.progressPercent}%)"
                        )
                    },
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                if (state.isDownloading) {
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { (state.progressPercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.25f)
                    )
                }
            }
            if (state.isReadyToInstall) {
                FilledTonalButton(
                    onClick = onInstallClick,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("flexible_update_install_btn")
                ) {
                    Text(
                        text = tr(lang, "রিস্টার্ট করুন", "Restart"),
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
