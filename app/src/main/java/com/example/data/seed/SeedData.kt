package com.example.data.seed

import com.example.data.local.*

object SeedData {
    // Structural reference data only — ZERO demo doctors, hospitals, or diagnostic centers.
    val specialties = listOf(
        SpecialtyEntity("sp_cardio", "হৃদরোগ বিশেষজ্ঞ", "Cardiology", "heart"),
        SpecialtyEntity("sp_med", "মেডিসিন বিশেষজ্ঞ", "Internal Medicine", "stethoscope"),
        SpecialtyEntity("sp_ped", "শিশু রোগ বিশেষজ্ঞ", "Pediatrics", "child"),
        SpecialtyEntity("sp_gyn", "প্রসূতি ও স্ত্রীরোগ", "Gynecology & Obstetrics", "female"),
        SpecialtyEntity("sp_ortho", "অর্থোপেডিক্স", "Orthopedics", "bone"),
        SpecialtyEntity("sp_neuro", "নিউরোলজি (স্নায়ুরোগ)", "Neurology", "brain"),
        SpecialtyEntity("sp_derm", "চর্ম ও যৌন রোগ", "Dermatology", "skin"),
        SpecialtyEntity("sp_ent", "নাক, কান ও গলা (ENT)", "ENT Specialist", "ear"),
        SpecialtyEntity("sp_eye", "চক্ষু রোগ বিশেষজ্ঞ", "Ophthalmology", "eye"),
        SpecialtyEntity("sp_surg", "সার্জারি বিশেষজ্ঞ", "General Surgery", "scalpel"),
        SpecialtyEntity("sp_psych", "মানসিক রোগ বিশেষজ্ঞ", "Psychiatry", "mind"),
        SpecialtyEntity("sp_dental", "ডেন্টাল ও মুখগহ্বর", "Dentistry", "tooth")
    )

    val locations = listOf(
        LocationEntity("loc_1", "Dhaka", "Dhaka", "Dhanmondi", "Satmasjid Road"),
        LocationEntity("loc_2", "Dhaka", "Dhaka", "Mirpur", "Mirpur 10"),
        LocationEntity("loc_3", "Dhaka", "Dhaka", "Uttara", "Sector 7"),
        LocationEntity("loc_4", "Dhaka", "Dhaka", "Shahbagh", "BSMMU Area"),
        LocationEntity("loc_5", "Dhaka", "Gazipur", "Gazipur Sadar", "Chowrasta"),
        LocationEntity("loc_6", "Dhaka", "Narayanganj", "Narayanganj Sadar", "Chashara"),
        LocationEntity("loc_7", "Chattogram", "Chattogram", "Panchlaish", "Probartak Circle"),
        LocationEntity("loc_8", "Chattogram", "Chattogram", "Halishahar", "Port Connecting Rd"),
        LocationEntity("loc_9", "Chattogram", "Cox's Bazar", "Cox's Bazar Sadar", "Kolatoli"),
        LocationEntity("loc_10", "Sylhet", "Sylhet", "Sylhet Sadar", "Subhanighat"),
        LocationEntity("loc_11", "Rajshahi", "Rajshahi", "Boalia", "Laxmipur"),
        LocationEntity("loc_12", "Khulna", "Khulna", "Sonadanga", "KDA Avenue"),
        LocationEntity("loc_13", "Barishal", "Barishal", "Barishal Sadar", "Banglabazar"),
        LocationEntity("loc_14", "Rangpur", "Rangpur", "Rangpur Sadar", "Dhap"),
        LocationEntity("loc_15", "Mymensingh", "Mymensingh", "Mymensingh Sadar", "Charpara")
    )

    suspend fun ensureCleanBootstrap(db: AppDatabase) {
        // Always ensure zero demo rows exist in local Room DB
        db.doctorDao().deleteDemoDoctors()
        db.hospitalDao().deleteDemoHospitals()
        db.diagnosticCenterDao().deleteDemoCenters()

        if (db.specialtyDao().count() == 0) {
            db.specialtyDao().insertAll(specialties)
        }
        if (db.locationDao().count() == 0) {
            db.locationDao().insertAll(locations)
        }
        if (db.settingsDao().getOnce() == null) {
            db.settingsDao().save(
                SiteSettingsEntity(
                    id = "global",
                    siteTitle = "Doctor List BD",
                    taglineBn = "ডাক্তার কোথায় বসেন, কখন বসেন — সব তথ্য এক জায়গায়।",
                    taglineEn = "Where doctors sit & when they sit — all verified chamber info in one place.",
                    contactEmail = "support@doctorlistbd.org",
                    contactPhone = "+880 1700-000000",
                    allowOnlineBooking = true,
                    showDemoData = false
                )
            )
        }
    }
}
