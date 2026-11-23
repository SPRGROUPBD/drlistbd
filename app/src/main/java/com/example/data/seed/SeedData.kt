package com.example.data.seed

import com.example.data.local.*

data class DivisionInfo(
    val nameEn: String,
    val nameBn: String,
    val districts: List<DistrictInfo>
)

data class DistrictInfo(
    val nameEn: String,
    val nameBn: String,
    val divisionEn: String,
    val defaultUpazila: String
)

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

    // বাংলাদেশের ৮টি বিভাগ ও ৬৪টি জেলার সম্পূর্ণ তালিকা (8 Divisions & 64 Districts of Bangladesh)
    val bangladeshDivisions: List<DivisionInfo> = listOf(
        DivisionInfo(
            nameEn = "Dhaka",
            nameBn = "ঢাকা বিভাগ",
            districts = listOf(
                DistrictInfo("Dhaka", "ঢাকা", "Dhaka", "Dhanmondi"),
                DistrictInfo("Faridpur", "ফরিদপুর", "Dhaka", "Faridpur Sadar"),
                DistrictInfo("Gazipur", "গাজীপুর", "Dhaka", "Gazipur Sadar"),
                DistrictInfo("Gopalganj", "গোপালগঞ্জ", "Dhaka", "Gopalganj Sadar"),
                DistrictInfo("Kishoreganj", "কিশোরগঞ্জ", "Dhaka", "Kishoreganj Sadar"),
                DistrictInfo("Madaripur", "মাদারীপুর", "Dhaka", "Madaripur Sadar"),
                DistrictInfo("Manikganj", "মানিকগঞ্জ", "Dhaka", "Manikganj Sadar"),
                DistrictInfo("Munshiganj", "মুন্সীগঞ্জ", "Dhaka", "Munshiganj Sadar"),
                DistrictInfo("Narayanganj", "নারায়ণগঞ্জ", "Dhaka", "Narayanganj Sadar"),
                DistrictInfo("Narsingdi", "নরসিংদী", "Dhaka", "Narsingdi Sadar"),
                DistrictInfo("Rajbari", "রাজবাড়ী", "Dhaka", "Rajbari Sadar"),
                DistrictInfo("Shariatpur", "শরীয়তপুর", "Dhaka", "Shariatpur Sadar"),
                DistrictInfo("Tangail", "টাঙ্গাইল", "Dhaka", "Tangail Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Chattogram",
            nameBn = "চট্টগ্রাম বিভাগ",
            districts = listOf(
                DistrictInfo("Bandarban", "বান্দরবান", "Chattogram", "Bandarban Sadar"),
                DistrictInfo("Brahmanbaria", "ব্রাহ্মণবাড়িয়া", "Chattogram", "Brahmanbaria Sadar"),
                DistrictInfo("Chandpur", "চাঁদপুর", "Chattogram", "Chandpur Sadar"),
                DistrictInfo("Chattogram", "চট্টগ্রাম", "Chattogram", "Panchlaish"),
                DistrictInfo("Cumilla", "কুমিল্লা", "Chattogram", "Cumilla Sadar"),
                DistrictInfo("Cox's Bazar", "কক্সবাজার", "Chattogram", "Cox's Bazar Sadar"),
                DistrictInfo("Feni", "ফেনী", "Chattogram", "Feni Sadar"),
                DistrictInfo("Khagrachhari", "খাগড়াছড়ি", "Chattogram", "Khagrachhari Sadar"),
                DistrictInfo("Lakshmipur", "লক্ষ্মীপুর", "Chattogram", "Lakshmipur Sadar"),
                DistrictInfo("Noakhali", "নোয়াখালী", "Chattogram", "Noakhali Sadar"),
                DistrictInfo("Rangamati", "রাঙ্গামাটি", "Chattogram", "Rangamati Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Rajshahi",
            nameBn = "রাজশাহী বিভাগ",
            districts = listOf(
                DistrictInfo("Bogura", "বগুড়া", "Rajshahi", "Bogura Sadar"),
                DistrictInfo("Chapainawabganj", "চাঁপাইনবাবগঞ্জ", "Rajshahi", "Chapainawabganj Sadar"),
                DistrictInfo("Joypurhat", "জয়পুরহাট", "Rajshahi", "Joypurhat Sadar"),
                DistrictInfo("Naogaon", "নওগাঁ", "Rajshahi", "Naogaon Sadar"),
                DistrictInfo("Natore", "নাটোর", "Rajshahi", "Natore Sadar"),
                DistrictInfo("Pabna", "পাবনা", "Rajshahi", "Pabna Sadar"),
                DistrictInfo("Rajshahi", "রাজশাহী", "Rajshahi", "Boalia"),
                DistrictInfo("Sirajganj", "সিরাজগঞ্জ", "Rajshahi", "Sirajganj Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Khulna",
            nameBn = "খুলনা বিভাগ",
            districts = listOf(
                DistrictInfo("Bagerhat", "বাগেরহাট", "Khulna", "Bagerhat Sadar"),
                DistrictInfo("Chuadanga", "চুয়াডাঙ্গা", "Khulna", "Chuadanga Sadar"),
                DistrictInfo("Jashore", "যশোর", "Khulna", "Jashore Sadar"),
                DistrictInfo("Jhenaidah", "ঝিনাইদহ", "Khulna", "Jhenaidah Sadar"),
                DistrictInfo("Khulna", "খুলনা", "Khulna", "Sonadanga"),
                DistrictInfo("Kushtia", "কুষ্টিয়া", "Khulna", "Kushtia Sadar"),
                DistrictInfo("Magura", "মাগুরা", "Khulna", "Magura Sadar"),
                DistrictInfo("Meherpur", "মেহেরপুর", "Khulna", "Meherpur Sadar"),
                DistrictInfo("Narail", "নড়াইল", "Khulna", "Narail Sadar"),
                DistrictInfo("Satkhira", "সাতক্ষীরা", "Khulna", "Satkhira Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Barishal",
            nameBn = "বরিশাল বিভাগ",
            districts = listOf(
                DistrictInfo("Barguna", "বরগুনা", "Barishal", "Barguna Sadar"),
                DistrictInfo("Barishal", "বরিশাল", "Barishal", "Barishal Sadar"),
                DistrictInfo("Bhola", "ভোলা", "Barishal", "Bhola Sadar"),
                DistrictInfo("Jhalokati", "ঝালকাঠি", "Barishal", "Jhalokati Sadar"),
                DistrictInfo("Patuakhali", "পটুয়াখালী", "Barishal", "Patuakhali Sadar"),
                DistrictInfo("Pirojpur", "পিরোজপুর", "Barishal", "Pirojpur Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Sylhet",
            nameBn = "সিলেট বিভাগ",
            districts = listOf(
                DistrictInfo("Habiganj", "হবিগঞ্জ", "Sylhet", "Habiganj Sadar"),
                DistrictInfo("Moulvibazar", "মৌলভীবাজার", "Sylhet", "Moulvibazar Sadar"),
                DistrictInfo("Sunamganj", "সুনামগঞ্জ", "Sylhet", "Sunamganj Sadar"),
                DistrictInfo("Sylhet", "সিলেট", "Sylhet", "Sylhet Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Rangpur",
            nameBn = "রংপুর বিভাগ",
            districts = listOf(
                DistrictInfo("Dinajpur", "দিনাজপুর", "Rangpur", "Dinajpur Sadar"),
                DistrictInfo("Gaibandha", "গাইবান্ধা", "Rangpur", "Gaibandha Sadar"),
                DistrictInfo("Kurigram", "কুড়িগ্রাম", "Rangpur", "Kurigram Sadar"),
                DistrictInfo("Lalmonirhat", "লালমনিরহাট", "Rangpur", "Lalmonirhat Sadar"),
                DistrictInfo("Nilphamari", "নীলফামারী", "Rangpur", "Nilphamari Sadar"),
                DistrictInfo("Panchagarh", "পঞ্চগড়", "Rangpur", "Panchagarh Sadar"),
                DistrictInfo("Rangpur", "রংপুর", "Rangpur", "Rangpur Sadar"),
                DistrictInfo("Thakurgaon", "ঠাকুরগাঁও", "Rangpur", "Thakurgaon Sadar")
            )
        ),
        DivisionInfo(
            nameEn = "Mymensingh",
            nameBn = "ময়মনসিংহ বিভাগ",
            districts = listOf(
                DistrictInfo("Jamalpur", "জামালপুর", "Mymensingh", "Jamalpur Sadar"),
                DistrictInfo("Mymensingh", "ময়মনসিংহ", "Mymensingh", "Mymensingh Sadar"),
                DistrictInfo("Netrokona", "নেত্রকোনা", "Mymensingh", "Netrokona Sadar"),
                DistrictInfo("Sherpur", "শেরপুর", "Mymensingh", "Sherpur Sadar")
            )
        )
    )

    val allDistricts: List<DistrictInfo> = bangladeshDivisions.flatMap { it.districts }

    private val districtBnLookup: Map<String, String> = allDistricts.associate {
        it.nameEn.lowercase() to it.nameBn
    }

    private val divisionBnLookup: Map<String, String> = bangladeshDivisions.associate {
        it.nameEn.lowercase() to it.nameBn
    }

    fun districtBnName(districtEn: String): String =
        districtBnLookup[districtEn.trim().lowercase()] ?: districtEn

    fun divisionBnName(divisionEn: String): String =
        divisionBnLookup[divisionEn.trim().lowercase()] ?: divisionEn

    // All 64 districts mapped as default LocationEntity rows + popular Dhaka/Chattogram medical areas
    val locations: List<LocationEntity> = buildList {
        allDistricts.forEachIndexed { idx, d ->
            add(
                LocationEntity(
                    id = "bd_dist_${idx + 1}",
                    division = d.divisionEn,
                    district = d.nameEn,
                    upazila = d.defaultUpazila,
                    area = d.nameBn
                )
            )
        }
        // Extra popular Dhaka & Chattogram medical hub upazilas
        add(LocationEntity("bd_hub_dhk_mirpur", "Dhaka", "Dhaka", "Mirpur", "Mirpur 10"))
        add(LocationEntity("bd_hub_dhk_uttara", "Dhaka", "Dhaka", "Uttara", "Sector 7"))
        add(LocationEntity("bd_hub_dhk_shahbagh", "Dhaka", "Dhaka", "Shahbagh", "BSMMU Area"))
        add(LocationEntity("bd_hub_dhk_panthapath", "Dhaka", "Dhaka", "Panthapath", "Green Road"))
        add(LocationEntity("bd_hub_dhk_gulshan", "Dhaka", "Dhaka", "Gulshan", "Gulshan 2"))
        add(LocationEntity("bd_hub_ctg_halishahar", "Chattogram", "Chattogram", "Halishahar", "Port Connecting Rd"))
        add(LocationEntity("bd_hub_ctg_gec", "Chattogram", "Chattogram", "GEC Circle", "Chawkbazar"))
    }

    suspend fun ensureCleanBootstrap(db: AppDatabase) {
        // Always ensure zero demo rows exist in local Room DB
        db.doctorDao().deleteDemoDoctors()
        db.hospitalDao().deleteDemoHospitals()
        db.diagnosticCenterDao().deleteDemoCenters()

        if (db.specialtyDao().count() == 0) {
            db.specialtyDao().insertAll(specialties)
        }
        // Always guarantee all 8 divisions & 64 districts exist in Room
        if (db.locationDao().count() < 64) {
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
                    allowOnlineBooking = false,
                    showDemoData = false
                )
            )
        }
        if (db.adConfigDao().getOnce() == null) {
            db.adConfigDao().save(AdConfigEntity())
        }
    }
}
