package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.local.SiteSettingsEntity
import com.example.i18n.AppLang
import com.example.i18n.AppStrings
import com.example.i18n.tr
import com.example.ui.theme.*

@Composable
fun StaticInfoScreen(
    pageKey: String,
    lang: AppLang,
    settings: SiteSettingsEntity,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    val (title, body) = when (pageKey) {
        "about" -> AppStrings.aboutUs(lang) to tr(
            lang,
            "Doctor List BD বাংলাদেশের রোগীদের জন্য ডাক্তারদের সঠিক চেম্বার অবস্থান, সাপ্তাহিক সময়সূচী, শিক্ষাগত যোগ্যতা ও কর্মস্থলের তথ্য এক জায়গায় পৌঁছে দেওয়ার একটি নির্ভরযোগ্য ডিরেক্টরি অ্যাপ।\n\nকোনো লগইন ছাড়াই আপনি সহজেই বিশেষজ্ঞ ডাক্তার, হাসপাতাল ও ডায়াগনস্টিক সেন্টার খুঁজে পেতে পারেন।",
            "Doctor List BD is a dedicated healthcare directory for Bangladesh helping patients find accurate doctor chamber locations, weekly schedules, qualifications, and current workplaces in one place — with no user login required."
        )
        "contact" -> AppStrings.contactUs(lang) to tr(
            lang,
            "সহায়তা বা তথ্য সংশোধনের জন্য যোগাযোগ করুন:\n\nঅ্যাপ ডেভেলপার: SPR IT ZONE\nনাম: Md Sajjad Ahmed\nঠিকানা: Munsigonj, Alamdanga, Chuadanga-7201\nমোবাইল: 01756144900\n\nইমেইল: ${settings.contactEmail}\nহেল্পলাইন: ${settings.contactPhone}\n\nযেকোনো ভুল চেম্বার তথ্য সরাসরি ডাক্তারের প্রোফাইলের 'ভুল তথ্য রিপোর্ট' বাটনের মাধ্যমে জানাতে পারেন।",
            "Contact Support:\n\nApp Developer: SPR IT ZONE\nName: Md Sajjad Ahmed\nAddress: Munsigonj, Alamdanga, Chuadanga-7201\nMobile: 01756144900\n\nEmail: ${settings.contactEmail}\nHelpline: ${settings.contactPhone}\n\nYou can also flag outdated chamber info directly using the Report button on any doctor profile."
        )
        "privacy" -> AppStrings.privacyPolicy(lang) to tr(
            lang,
            "গোপনীয়তা নীতি:\n\n১. এই অ্যাপে কোনো ইউজার অ্যাকাউন্ট বা লগইনের প্রয়োজন নেই।\n২. আপনার সেভ করা প্রিয় ডাক্তারগুলো কেবল আপনার ডিভাইসের লোকাল ডাটাবেজে সংরক্ষিত থাকে।\n৩. কোনো কনসালটেশন ফি বা পেমেন্ট তথ্য সংগ্রহ করা হয় না।",
            "Privacy Policy:\n\n1. No user account or login is required to browse Doctor List BD.\n2. Saved favorites are stored locally on your device via Room database.\n3. No consultation fees or financial payment data are collected."
        )
        else -> AppStrings.termsConditions(lang) to tr(
            lang,
            "শর্তাবলী:\n\n১. BMDC ভেরিফিকেশন ব্যাজ কেবল অ্যাডমিন যাচাইয়ের পর প্রদান করা হয়।\n২. চেম্বারে যাওয়ার আগে প্রদর্শিত সিরিয়াল নম্বরে ফোন করে সময়সূচী নিশ্চিত করার পরামর্শ দেওয়া হলো।",
            "Terms & Conditions:\n\n1. BMDC verification badges are granted solely after administrator review.\n2. Patients are advised to call the chamber serial number before visiting."
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(BackgroundIce)
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            color = SurfaceWhite,
            border = BorderStroke(1.dp, OutlineSoft),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Text(title, style = MaterialTheme.typography.headlineSmall, color = MedicalBlueDark, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(12.dp))
                Text(body, style = MaterialTheme.typography.bodyLarge, color = TextPrimary)
            }
        }
    }
}
