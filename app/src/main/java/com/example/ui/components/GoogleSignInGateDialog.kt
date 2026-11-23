package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountCircle
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.example.auth.GoogleAuthManager
import com.example.auth.GoogleUserAccount
import com.example.i18n.AppLang
import com.example.i18n.tr
import com.example.ui.ads.findActivity
import com.example.ui.theme.*

/**
 * Reusable Material 3 "Continue with Google" interactive sign-in button powered by
 * Jetpack Credential Manager (`GetSignInWithGoogleOption`).
 */
@Composable
fun ContinueWithGoogleButton(
    label: String,
    enabled: Boolean = true,
    onSuccess: (GoogleUserAccount) -> Unit,
    onError: (String) -> Unit,
    modifier: Modifier = Modifier,
    testTagName: String = "google_sign_in_btn"
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }

    OutlinedButton(
        onClick = {
            val activity = context.findActivity()
            if (activity == null) {
                onError("Unable to launch Google Sign-In from current screen.")
                return@OutlinedButton
            }
            isLoading = true
            GoogleAuthManager.signInWithGoogle(
                activity = activity,
                scope = scope,
                onSuccess = { account ->
                    isLoading = false
                    onSuccess(account)
                },
                onError = { msg ->
                    isLoading = false
                    onError(msg)
                },
                onCancelled = {
                    isLoading = false
                }
            )
        },
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.2.dp, MedicalBlue.copy(alpha = 0.45f)),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = SurfaceWhite,
            contentColor = TextPrimary
        ),
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 50.dp)
            .testTag(testTagName)
    ) {
        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.2.dp,
                color = MedicalBlue
            )
            Spacer(Modifier.width(10.dp))
            Text(label, fontWeight = FontWeight.SemiBold)
        } else {
            Surface(
                shape = CircleShape,
                color = Color(0xFFEA4335).copy(alpha = 0.12f),
                modifier = Modifier.size(26.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "G",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF4285F4)
                    )
                }
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }
    }
}

/**
 * Shown when an unauthenticated visitor attempts to save a Favorite Doctor, Hospital,
 * Diagnostic Center, or Chamber. Allows immediate Google Sign-In or dismissal to keep browsing.
 */
@Composable
fun VisitorGoogleSignInGateDialog(
    lang: AppLang,
    isOnline: Boolean,
    errorMessage: String?,
    onAuthSuccess: (GoogleUserAccount) -> Unit,
    onAuthError: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            tonalElevation = 8.dp,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("visitor_google_signin_dialog")
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MedicalBlueLight,
                    modifier = Modifier.size(58.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MedicalBlueDark,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                Text(
                    text = tr(
                        lang,
                        "প্রিয় তালিকা সেভ করতে Google দিয়ে সাইন-ইন করুন",
                        "Sign in with Google to Save Favorites"
                    ),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = tr(
                        lang,
                        "আপনি লগইন ছাড়াই সব ডাক্তার ও হাসপাতাল দেখতে পারবেন। তবে প্রিয় ডাক্তার, হাসপাতাল বা ডায়াগনস্টিক সেভ করে সব ডিভাইসে সিঙ্ক রাখতে Google অ্যাকাউন্ট প্রয়োজন।",
                        "You can browse all doctors and facilities freely without an account. Sign in with Google to save favorites and sync them across all your devices."
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = TextSecondary,
                    textAlign = TextAlign.Center
                )

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BackgroundIce,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(Icons.Rounded.CloudDone, contentDescription = null, tint = MedicalTealDark, modifier = Modifier.size(18.dp))
                        Text(
                            text = tr(
                                lang,
                                "যেকোনো ডিভাইসে একই Google অ্যাকাউন্ট দিয়ে লগইন করলেই সেভ করা তালিকা পাওয়া যাবে।",
                                "Automatic multi-device sync tied securely to your Google account."
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MedicalTealDark
                        )
                    }
                }

                if (!isOnline) {
                    Text(
                        text = tr(lang, "Google সাইন-ইন করতে ইন্টারনেট সংযোগ চালু করুন।", "Please connect to the internet to sign in with Google."),
                        style = MaterialTheme.typography.labelMedium,
                        color = RoseAlert,
                        textAlign = TextAlign.Center
                    )
                }

                if (!errorMessage.isNullOrBlank()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = RoseAlert,
                        textAlign = TextAlign.Center
                    )
                }

                ContinueWithGoogleButton(
                    label = tr(lang, "Google দিয়ে চালিয়ে যান", "Continue with Google"),
                    enabled = isOnline,
                    onSuccess = onAuthSuccess,
                    onError = onAuthError,
                    testTagName = "visitor_gate_google_btn"
                )

                TextButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("visitor_gate_dismiss_btn")
                ) {
                    Text(
                        text = tr(lang, "এখন নয়, ব্রাউজ চালিয়ে যান", "Not now, keep browsing"),
                        color = TextSecondary
                    )
                }
            }
        }
    }
}
