package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.BuildConfig
import com.example.R
import com.example.data.local.AdConfigEntity
import com.example.data.local.PlacementRateCardEntity
import com.example.data.local.SiteSettingsEntity
import com.example.data.local.SponsorEntity
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.ads.AboutUsSponsorSection
import com.example.ui.ads.PlacementRateCardEngine
import com.example.ui.ads.PlacementSponsorSlot
import com.example.ui.components.StatusPill
import com.example.ui.theme.*

@Composable
fun AboutUsScreen(
    lang: AppLang,
    settings: SiteSettingsEntity,
    adConfig: AdConfigEntity = AdConfigEntity(),
    sponsors: List<SponsorEntity> = emptyList(),
    rateCards: List<PlacementRateCardEntity> = PlacementRateCardEngine.defaultRateCards(),
    isOnline: Boolean = true,
    nowMs: Long = System.currentTimeMillis(),
    onSponsorDisplayed: (String, Boolean, String) -> Unit = { _, _, _ -> },
    onSponsorClicked: (String, Boolean) -> Unit = { _, _ -> },
    onOpenAdvertiserPortal: () -> Unit = {},
    doctorsCount: Int,
    hospitalsCount: Int,
    diagnosticCount: Int,
    specialtiesCount: Int,
    onNavigateInfo: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appTitle = settings.siteTitle.ifBlank { "Doctor List BD" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .testTag("about_us_screen"),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Brand Hero Banner
        item {
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = MedicalBlueDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                listOf(MedicalBlueDark, MedicalBlue, MedicalTeal)
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = Color.White,
                            shadowElevation = 6.dp,
                            modifier = Modifier.size(84.dp)
                        ) {
                            Image(
                                painter = painterResource(id = R.drawable.doc_dir_icon_1794967683712),
                                contentDescription = "$appTitle logo",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(5.dp)
                                    .clip(RoundedCornerShape(18.dp))
                            )
                        }
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = appTitle,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = tr(lang, settings.taglineBn, settings.taglineEn),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.92f),
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(12.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            StatusPill(
                                text = "v${BuildConfig.VERSION_NAME}",
                                bgColor = Color.White.copy(alpha = 0.2f),
                                textColor = Color.White,
                                icon = Icons.Rounded.Verified
                            )
                            StatusPill(
                                text = tr(lang, "বাংলাদেশ হেলথ ডিরেক্টরি", "Bangladesh Healthcare"),
                                bgColor = Color.White.copy(alpha = 0.2f),
                                textColor = Color.White,
                                icon = Icons.Rounded.HealthAndSafety
                            )
                        }
                    }
                }
            }
        }

        // Live Directory Stats Strip
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                AboutStatCard(
                    value = doctorsCount.toString(),
                    label = tr(lang, "ডাক্তার", "Doctors"),
                    icon = Icons.Rounded.MedicalServices,
                    tint = MedicalBlue,
                    modifier = Modifier.weight(1f)
                )
                AboutStatCard(
                    value = (hospitalsCount + diagnosticCount).toString(),
                    label = tr(lang, "হাসপাতাল", "Facilities"),
                    icon = Icons.Rounded.LocalHospital,
                    tint = MedicalTeal,
                    modifier = Modifier.weight(1f)
                )
                AboutStatCard(
                    value = specialtiesCount.toString(),
                    label = tr(lang, "বিভাগ", "Specialties"),
                    icon = Icons.Rounded.Category,
                    tint = MedicalBlueDark,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // Mission & Overview Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Info,
                            contentDescription = null,
                            tint = MedicalBlue,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = tr(lang, "আমাদের লক্ষ্য ও উদ্দেশ্য", "Our Mission"),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = tr(
                            lang,
                            "Doctor List BD হলো বাংলাদেশের রোগী ও পরিবারের জন্য তৈরি একটি উন্মুক্ত ও নির্ভরযোগ্য ডাক্তার ডিরেক্টরি। বিশেষজ্ঞ ডাক্তার কোথায় বসেন, কোন দিন এবং কোন সময়ে চেম্বার করেন, শিক্ষাগত যোগ্যতা, BMDC নিবন্ধন ও সিরিয়ালের যোগাযোগ নম্বর — সব তথ্য এক জায়গায় সহজভাবে খুঁজে পেতে এই অ্যাপটি তৈরি করা হয়েছে।",
                            "Doctor List BD is a trusted, open healthcare directory built for patients and families across Bangladesh. Easily discover where specialist doctors practice, their weekly chamber schedules, qualifications, BMDC verification status, hospitals, diagnostic centers, and direct serial contact numbers — all in one fast app."
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        // Core Features Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = tr(lang, "অ্যাপটির প্রধান বৈশিষ্ট্যসমূহ", "Why Doctor List BD"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    AboutFeatureRow(
                        icon = Icons.Rounded.Search,
                        tint = MedicalBlue,
                        title = tr(lang, "স্মার্ট ও দ্রুত অনুসন্ধান", "Advanced Doctor & Chamber Search"),
                        subtitle = tr(
                            lang,
                            "নাম, বিভাগ, জেলা, উপজেলা, হাসপাতাল ও বার অনুযায়ী মুহূর্তেই ডাক্তার খুঁজুন।",
                            "Filter doctors instantly by name, specialty, district, upazila, hospital, or visiting day."
                        )
                    )
                    AboutFeatureRow(
                        icon = Icons.Rounded.VerifiedUser,
                        tint = EmeraldAvailable,
                        title = tr(lang, "BMDC ভেরিফায়েড প্রোফাইল", "BMDC Verified Profiles"),
                        subtitle = tr(
                            lang,
                            "অ্যাডমিন যাচাইকৃত ডাক্তারদের প্রোফাইলে বিশেষ ভেরিফিকেশন ব্যাজ দেখানো হয়।",
                            "Administrator-reviewed profiles display clear BMDC verification badges for confidence."
                        )
                    )
                    AboutFeatureRow(
                        icon = Icons.Rounded.LockOpen,
                        tint = MedicalTeal,
                        title = tr(lang, "কোনো লগইন ছাড়াই সম্পূর্ণ ফ্রি", "Free Access — Zero Login Required"),
                        subtitle = tr(
                            lang,
                            "অ্যাকাউন্ট খোলা ছাড়াই প্রিয় ডাক্তার সেভ করুন ও অফলাইনেও তথ্য দেখুন।",
                            "Browse everything, save favorites locally, and access cached schedules without signing in."
                        )
                    )
                    AboutFeatureRow(
                        icon = Icons.Rounded.Sync,
                        tint = MedicalBlueDark,
                        title = tr(lang, "তাৎক্ষণিক ক্লাউড আপডেট", "Real-Time Directory Sync"),
                        subtitle = tr(
                            lang,
                            "নতুন ডাক্তার, হাসপাতাল বা চেম্বার সময়সূচী যুক্ত হলেই অ্যাপে আপডেট হয়ে যায়।",
                            "New doctors, hospitals, and updated chamber timings sync smoothly to your device."
                        )
                    )
                }
            }
        }

        // Administrator-Controlled Direct Sponsor Advertisement Section (OTH-ABOUT) + Advertiser Portal CTA
        item {
            PlacementSponsorSlot(
                placementId = PlacementRateCardEngine.OTH_ABOUT,
                sponsors = sponsors,
                rateCards = rateCards,
                adConfig = adConfig,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked,
                modifier = Modifier.padding(bottom = 10.dp)
            )
            AboutUsSponsorSection(
                sponsors = sponsors,
                adConfig = adConfig,
                isOnline = isOnline,
                nowMs = nowMs,
                lang = lang,
                onSponsorDisplayed = onSponsorDisplayed,
                onSponsorClicked = onSponsorClicked,
                onOpenAdvertiserPortal = onOpenAdvertiserPortal
            )
        }

        // Contact, Share & Support Card
        item {
            Surface(
                shape = RoundedCornerShape(20.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.dp, OutlineSoft),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = tr(lang, "যোগাযোগ ও সহায়তা", "Contact & Support"),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = tr(
                            lang,
                            "যেকোনো তথ্য সংশোধন, নতুন ডাক্তার বা হাসপাতাল যুক্ত করতে যোগাযোগ করুন:",
                            "Reach out for listing updates, corrections, or directory support:"
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                    Spacer(Modifier.height(12.dp))

                    // Email & Phone info rows
                    ContactDetailRow(
                        icon = Icons.Outlined.Email,
                        label = tr(lang, "ইমেইল", "Email"),
                        value = settings.contactEmail.ifBlank { "support@doctorlistbd.org" }
                    )
                    Spacer(Modifier.height(8.dp))
                    ContactDetailRow(
                        icon = Icons.Outlined.Phone,
                        label = tr(lang, "হেল্পলাইন", "Helpline"),
                        value = settings.contactPhone.ifBlank { "+880 1700-000000" }
                    )

                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Button(
                            onClick = {
                                val phone = settings.contactPhone.ifBlank { "+8801700000000" }
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone")))
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MedicalTeal),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("about_call_btn")
                        ) {
                            Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "কল করুন", "Call"))
                        }
                        OutlinedButton(
                            onClick = {
                                val email = settings.contactEmail.ifBlank { "support@doctorlistbd.org" }
                                runCatching {
                                    val intent = Intent(Intent.ACTION_SENDTO).apply {
                                        data = Uri.parse("mailto:$email")
                                        putExtra(Intent.EXTRA_SUBJECT, "Doctor List BD Support")
                                    }
                                    context.startActivity(intent)
                                }
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("about_email_btn")
                        ) {
                            Icon(Icons.Rounded.Email, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(tr(lang, "ইমেইল", "Email"))
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    FilledTonalButton(
                        onClick = {
                            val shareText = tr(
                                lang,
                                "Doctor List BD — বাংলাদেশের ডাক্তারদের চেম্বার ও সময়সূচী খুঁজুন এক অ্যাপেই!",
                                "Doctor List BD — Find verified doctor chambers & schedules across Bangladesh in one app!"
                            )
                            runCatching {
                                val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(Intent.EXTRA_SUBJECT, appTitle)
                                    putExtra(Intent.EXTRA_TEXT, shareText)
                                }
                                context.startActivity(Intent.createChooser(sendIntent, appTitle))
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("about_share_btn")
                    ) {
                        Icon(Icons.Rounded.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr(lang, "বন্ধুদের সাথে অ্যাপ শেয়ার করুন", "Share Doctor List BD"))
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = SurfaceMuted)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = tr(lang, "গুরুত্বপূর্ণ লিংকসমূহ", "Important Information"),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MedicalBlueDark
                    )
                    Spacer(Modifier.height(6.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { onNavigateInfo("contact") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("about_contact_btn")
                        ) {
                            Icon(Icons.Rounded.SupportAgent, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(AppStrings.contactUs(lang), fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onNavigateInfo("privacy") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("about_privacy_btn")
                            ) {
                                Text(AppStrings.privacyPolicy(lang), maxLines = 1)
                            }
                            OutlinedButton(
                                onClick = { onNavigateInfo("terms") },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("about_terms_btn")
                            ) {
                                Text(AppStrings.termsConditions(lang), maxLines = 1)
                            }
                        }
                    }
                }
            }
        }

        // App Developer Information Card (SPR IT ZONE)
        item {
            Surface(
                shape = RoundedCornerShape(22.dp),
                color = SurfaceWhite,
                border = BorderStroke(1.2.dp, MedicalTeal.copy(alpha = 0.45f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("about_developer_card")
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MedicalTealLight,
                            modifier = Modifier.size(42.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Rounded.DeveloperMode,
                                    contentDescription = null,
                                    tint = MedicalTealDark,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = tr(lang, "অ্যাপ ডেভেলপার", "App Developer"),
                                style = MaterialTheme.typography.labelMedium,
                                color = MedicalTealDark,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "SPR IT ZONE",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextPrimary
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    ContactDetailRow(
                        icon = Icons.Rounded.Person,
                        label = tr(lang, "নাম", "Name"),
                        value = "Md Sajjad Ahmed"
                    )
                    Spacer(Modifier.height(8.dp))
                    ContactDetailRow(
                        icon = Icons.Rounded.LocationOn,
                        label = tr(lang, "ঠিকানা", "Address"),
                        value = "Munsigonj, Alamdanga, Chuadanga-7201"
                    )
                    Spacer(Modifier.height(8.dp))
                    ContactDetailRow(
                        icon = Icons.Rounded.Phone,
                        label = tr(lang, "মোবাইল", "Mobile"),
                        value = "01756144900"
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:01756144900")))
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MedicalTealDark),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("about_dev_call_btn")
                    ) {
                        Icon(Icons.Rounded.Call, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(tr(lang, "ডেভেলপারকে কল করুন (01756144900)", "Call Developer (01756144900)"), fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Medical Directory Disclaimer
        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MedicalBlueLight.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MedicalBlueContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.HealthAndSafety,
                        contentDescription = null,
                        tint = MedicalBlueDark,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = tr(
                            lang,
                            "সতর্কতা: চেম্বারে যাওয়ার আগে অনুগ্রহ করে সিরিয়াল নম্বরে ফোন করে ডাক্তারের উপস্থিতি ও সময়সূচী নিশ্চিত হয়ে নিন। এই অ্যাপে কোনো ভিজিট বা ফি প্রদর্শন করা হয় না।",
                            "Note: Please call the chamber serial number before visiting to confirm availability. Doctor List BD does not display consultation fees or provide medical advice."
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun AboutStatCard(
    value: String,
    label: String,
    icon: ImageVector,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = SurfaceWhite,
        border = BorderStroke(1.dp, OutlineSoft),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
            Spacer(Modifier.height(6.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = TextSecondary,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun AboutFeatureRow(
    icon: ImageVector,
    tint: Color,
    title: String,
    subtitle: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = tint.copy(alpha = 0.12f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = TextPrimary
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun ContactDetailRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(icon, contentDescription = null, tint = MedicalBlue, modifier = Modifier.size(18.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall, color = TextMuted)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = TextPrimary, fontWeight = FontWeight.Medium)
        }
    }
}
