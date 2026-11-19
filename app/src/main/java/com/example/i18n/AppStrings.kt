package com.example.i18n

enum class AppLang { BN, EN }

fun tr(lang: AppLang, bn: String, en: String): String = if (lang == AppLang.BN) bn else en

object AppStrings {
    fun appTitle(lang: AppLang) = "Doctor List BD"
    fun tagline(lang: AppLang) = tr(
        lang,
        "ডাক্তার কোথায় বসেন, কখন বসেন — সব তথ্য এক জায়গায়।",
        "Where doctors sit & when they sit — all verified chamber info in one place."
    )

    fun navHome(lang: AppLang) = tr(lang, "হোম", "Home")
    fun navDoctors(lang: AppLang) = tr(lang, "ডাক্তার", "Doctors")
    fun navFacilities(lang: AppLang) = tr(lang, "হাসপাতাল", "Facilities")
    fun navPortal(lang: AppLang) = tr(lang, "সেভড", "Saved")

    fun searchHint(lang: AppLang) = tr(
        lang,
        "ডাক্তার, বিশেষজ্ঞতা, জেলা বা হাসপাতাল খুঁজুন...",
        "Search doctor, specialty, district or hospital..."
    )

    fun allLabel(lang: AppLang) = tr(lang, "সব", "All")
    fun availableToday(lang: AppLang) = tr(lang, "আজ বসবেন", "Available Today")
    fun verifiedOnly(lang: AppLang) = tr(lang, "ভেরিফায়েড", "Verified")
    fun mostExperienced(lang: AppLang) = tr(lang, "সর্বোচ্চ অভিজ্ঞতা", "Most Experienced")
    fun recentlyUpdated(lang: AppLang) = tr(lang, "সম্প্রতি আপডেটেড", "Recently Updated")
    fun nameAZ(lang: AppLang) = tr(lang, "নাম (A-Z)", "Name (A-Z)")
    fun verifiedFirst(lang: AppLang) = tr(lang, "ভেরিফায়েড আগে", "Verified First")

    fun featuredDoctors(lang: AppLang) = tr(lang, "বিশেষজ্ঞ ও নির্বাচিত ডাক্তার", "Featured Doctors")
    fun verifiedDoctors(lang: AppLang) = tr(lang, "BMDC ভেরিফায়েড ডাক্তার", "Verified Doctors")
    fun recentlyUpdatedDoctors(lang: AppLang) = tr(lang, "সম্প্রতি আপডেটকৃত ডাক্তার", "Recently Updated Doctors")
    fun popularHospitals(lang: AppLang) = tr(lang, "জনপ্রিয় হাসপাতালসমূহ", "Popular Hospitals")
    fun specialties(lang: AppLang) = tr(lang, "চিকিৎসা বিশেষজ্ঞতা", "Medical Specialties")
    fun selectDistrict(lang: AppLang) = tr(lang, "জেলা নির্বাচন করুন", "Select District")

    fun hospitals(lang: AppLang) = tr(lang, "হাসপাতাল ডিরেক্টরি", "Hospitals")
    fun diagnosticCenters(lang: AppLang) = tr(lang, "ডায়াগনস্টিক সেন্টার", "Diagnostic Centers")
    fun chamberSchedule(lang: AppLang) = tr(lang, "চেম্বার সময়সূচী", "Chamber Schedule")

    fun callForSerial(lang: AppLang) = tr(lang, "সিরিয়ালের জন্য কল করুন", "Call for Serial")
    fun openMaps(lang: AppLang) = tr(lang, "গুগল ম্যাপ", "Google Maps")
    fun shareProfile(lang: AppLang) = tr(lang, "শেয়ার", "Share")
    fun saveFavorite(lang: AppLang) = tr(lang, "সেভ", "Save")
    fun saved(lang: AppLang) = tr(lang, "সেভড", "Saved")
    fun reportIncorrect(lang: AppLang) = tr(lang, "ভুল তথ্য রিপোর্ট", "Report Info")
    fun requestAppointment(lang: AppLang) = tr(lang, "অ্যাপয়েন্টমেন্ট অনুরোধ", "Request Serial")

    fun bmdcVerified(lang: AppLang) = tr(lang, "BMDC ভেরিফায়েড", "BMDC Verified")
    fun unverified(lang: AppLang) = tr(lang, "ভেরিফিকেশন পেন্ডিং", "Unverified")
    fun pendingApproval(lang: AppLang) = tr(lang, "অনুমোদনের অপেক্ষায়", "Pending Approval")
    fun rejected(lang: AppLang) = tr(lang, "বাতিলকৃত", "Rejected")

    fun login(lang: AppLang) = tr(lang, "লগইন", "Login")
    fun registerDoctor(lang: AppLang) = tr(lang, "ডাক্তার নিবন্ধন", "Doctor Registration")
    fun registerPatient(lang: AppLang) = tr(lang, "রোগী নিবন্ধন", "Patient Registration")
    fun registerHospital(lang: AppLang) = tr(lang, "হাসপাতাল নিবন্ধন", "Hospital Registration")
    fun logout(lang: AppLang) = tr(lang, "লগআউট", "Logout")

    fun aboutUs(lang: AppLang) = tr(lang, "আমাদের সম্পর্কে", "About Us")
    fun contactUs(lang: AppLang) = tr(lang, "যোগাযোগ", "Contact Us")
    fun privacyPolicy(lang: AppLang) = tr(lang, "গোপনীয়তা নীতি", "Privacy Policy")
    fun termsConditions(lang: AppLang) = tr(lang, "শর্তাবলী", "Terms & Conditions")

    fun emptyDoctors(lang: AppLang) = tr(
        lang,
        "আপনার ফিল্টারের সাথে মিলছে এমন কোনো ডাক্তার পাওয়া যায়নি।",
        "No doctors matched your current search filters."
    )
}
