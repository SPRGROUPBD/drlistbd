package com.example.data.seed

import com.example.data.local.*
import com.example.data.model.DayOfWeekBd
import com.example.data.model.UserRole
import java.security.MessageDigest

object SeedData {
    fun sha256(input: String): String {
        val bytes = MessageDigest.getInstance("SHA-256").digest(input.toByteArray())
        return bytes.joinToString("") { "%02x".format(it) }
    }

    val hiddenAdminUser = UserEntity(
        id = "usr_super_admin",
        name = "System Administrator",
        email = "admin",
        passwordHash = sha256("admin"),
        role = UserRole.SUPER_ADMIN.name,
        isActive = true,
        isHidden = true
    )

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

    val demoHospitals = listOf(
        HospitalEntity(
            id = "hosp_demo_1",
            nameBn = "[DEMO] কেন্দ্রীয় মেডিকেল কলেজ হাসপাতাল",
            nameEn = "[DEMO] Central Medical Hospital",
            district = "Dhaka",
            upazila = "Dhanmondi",
            address = "House 14, Road 6, Dhanmondi, Dhaka",
            contactNumber = "+8801700000101",
            imageUrl = "",
            status = "ACTIVE",
            isDemoData = true
        ),
        HospitalEntity(
            id = "hosp_demo_2",
            nameBn = "[DEMO] চট্টগ্রাম সিটি হেলথ কেয়ার",
            nameEn = "[DEMO] Chattogram City Health Care",
            district = "Chattogram",
            upazila = "Panchlaish",
            address = "42 Probartak Circle, Panchlaish, Chattogram",
            contactNumber = "+8801700000102",
            imageUrl = "",
            status = "ACTIVE",
            isDemoData = true
        ),
        HospitalEntity(
            id = "hosp_demo_3",
            nameBn = "[DEMO] সিলেট স্পেশালাইজড হাসপাতাল",
            nameEn = "[DEMO] Sylhet Specialized Hospital",
            district = "Sylhet",
            upazila = "Sylhet Sadar",
            address = "Subhanighat Point, Sylhet",
            contactNumber = "+8801700000103",
            imageUrl = "",
            status = "ACTIVE",
            isDemoData = true
        )
    )

    val demoDiagnosticCenters = listOf(
        DiagnosticCenterEntity(
            id = "diag_demo_1",
            nameBn = "[DEMO] প্রগতি ডায়াগনস্টিক সেন্টার",
            nameEn = "[DEMO] Pragati Diagnostic Center",
            district = "Dhaka",
            upazila = "Mirpur",
            address = "Plot 8, Section 10, Mirpur, Dhaka",
            contactNumber = "+8801700000201",
            imageUrl = "",
            status = "ACTIVE",
            isDemoData = true
        ),
        DiagnosticCenterEntity(
            id = "diag_demo_2",
            nameBn = "[DEMO] পদ্মা মেডিকেল ডায়াগনস্টিক",
            nameEn = "[DEMO] Padma Medical Diagnostic",
            district = "Rajshahi",
            upazila = "Boalia",
            address = "Laxmipur Mor, Boalia, Rajshahi",
            contactNumber = "+8801700000202",
            imageUrl = "",
            status = "ACTIVE",
            isDemoData = true
        )
    )

    // Note: Demo doctors start with bmdcVerified = false (NEVER auto-verified)
    val demoDoctors = listOf(
        DoctorEntity(
            id = "doc_demo_1",
            fullNameBn = "[DEMO] ডা. নমুনা করিম",
            fullNameEn = "[DEMO] Dr. Sample Karim",
            bmdcNumber = "DEMO-A-00101",
            bmdcVerified = false,
            verificationStatus = "PENDING",
            approvalStatus = "APPROVED",
            gender = "Male",
            bioBn = "নমুনা প্রোফাইল: হৃদরোগ ও উচ্চ রক্তচাপ পরামর্শদাতা।",
            bioEn = "Sample UI profile: Cardiology & hypertension consultation.",
            contactNumber = "+8801700000301",
            mainSpecialtyId = "sp_cardio",
            subSpecialty = "Interventional Cardiology",
            expertise = "Hypertension, Chest Pain, ECG & Echo Evaluation",
            currentHospital = "[DEMO] Central Medical Hospital",
            currentMedicalCollege = "[DEMO] Sample Medical College",
            currentDepartment = "Cardiology",
            currentDesignation = "Associate Professor",
            employmentStartDate = "2020-01",
            totalExperienceYears = 12,
            teachingExperienceYears = 6,
            trainings = "Fellowship in Clinical Cardiology (DEMO)",
            isActive = true,
            isFeatured = true,
            isDemoData = true
        ),
        DoctorEntity(
            id = "doc_demo_2",
            fullNameBn = "[DEMO] ডা. নমুনা তাসনিম",
            fullNameEn = "[DEMO] Dr. Sample Tasnim",
            bmdcNumber = "DEMO-A-00102",
            bmdcVerified = false,
            verificationStatus = "PENDING",
            approvalStatus = "APPROVED",
            gender = "Female",
            bioBn = "নমুনা প্রোফাইল: প্রসূতি ও স্ত্রীরোগ বিশেষজ্ঞ।",
            bioEn = "Sample UI profile: Gynecology & maternal care specialist.",
            contactNumber = "+8801700000302",
            mainSpecialtyId = "sp_gyn",
            subSpecialty = "Maternal & Fetal Medicine",
            expertise = "Antenatal Care, PCOS, High-Risk Pregnancy",
            currentHospital = "[DEMO] Chattogram City Health Care",
            currentMedicalCollege = "[DEMO] Chattogram Sample College",
            currentDepartment = "Gynecology & Obstetrics",
            currentDesignation = "Consultant",
            employmentStartDate = "2021-06",
            totalExperienceYears = 9,
            teachingExperienceYears = 3,
            trainings = "FCPS Training (DEMO)",
            isActive = true,
            isFeatured = true,
            isDemoData = true
        ),
        DoctorEntity(
            id = "doc_demo_3",
            fullNameBn = "[DEMO] ডা. নমুনা হাসান",
            fullNameEn = "[DEMO] Dr. Sample Hasan",
            bmdcNumber = "DEMO-A-00103",
            bmdcVerified = false,
            verificationStatus = "PENDING",
            approvalStatus = "APPROVED",
            gender = "Male",
            bioBn = "নমুনা প্রোফাইল: মেডিসিন ও ডায়াবেটিস রোগ পরামর্শদাতা।",
            bioEn = "Sample UI profile: Internal medicine & diabetes specialist.",
            contactNumber = "+8801700000303",
            mainSpecialtyId = "sp_med",
            subSpecialty = "Diabetes & Endocrine Care",
            expertise = "Fever, Thyroid, Diabetes, Respiratory Care",
            currentHospital = "[DEMO] Sylhet Specialized Hospital",
            currentMedicalCollege = "[DEMO] Sylhet Sample Medical",
            currentDepartment = "Medicine",
            currentDesignation = "Senior Consultant",
            employmentStartDate = "2018-03",
            totalExperienceYears = 15,
            teachingExperienceYears = 7,
            trainings = "MRCP Training Workshop (DEMO)",
            isActive = true,
            isFeatured = false,
            isDemoData = true
        ),
        DoctorEntity(
            id = "doc_demo_4",
            fullNameBn = "[DEMO] ডা. নমুনা মেহরিন",
            fullNameEn = "[DEMO] Dr. Sample Mehrin",
            bmdcNumber = "DEMO-A-00104",
            bmdcVerified = false,
            verificationStatus = "PENDING",
            approvalStatus = "APPROVED",
            gender = "Female",
            bioBn = "নমুনা প্রোফাইল: শিশু ও নবজাতক রোগ বিশেষজ্ঞ।",
            bioEn = "Sample UI profile: Pediatrics & neonatal health specialist.",
            contactNumber = "+8801700000304",
            mainSpecialtyId = "sp_ped",
            subSpecialty = "Neonatology",
            expertise = "Child Nutrition, Vaccination, Pediatric Asthma",
            currentHospital = "[DEMO] Central Medical Hospital",
            currentMedicalCollege = "[DEMO] Dhaka Sample Medical",
            currentDepartment = "Pediatrics",
            currentDesignation = "Assistant Professor",
            employmentStartDate = "2022-02",
            totalExperienceYears = 7,
            teachingExperienceYears = 2,
            trainings = "Neonatal Care Certification (DEMO)",
            isActive = true,
            isFeatured = true,
            isDemoData = true
        )
    )

    val demoEducations = listOf(
        EducationEntity("edu_d1", "doc_demo_1", "MBBS", "Sample Medical College, Dhaka", "2012", "Clinical ECG"),
        EducationEntity("edu_d2", "doc_demo_1", "MD (Cardiology)", "Sample Medical University", "2018", "Echo & Cath"),
        EducationEntity("edu_d3", "doc_demo_2", "MBBS", "Sample Medical College, Chattogram", "2015", ""),
        EducationEntity("edu_d4", "doc_demo_2", "FCPS (OBGYN)", "BCPS Sample", "2020", "Laparoscopy"),
        EducationEntity("edu_d5", "doc_demo_3", "MBBS, FCPS (Medicine)", "Sylhet Sample Medical", "2011", "Diabetes Care"),
        EducationEntity("edu_d6", "doc_demo_4", "MBBS, DCH", "Dhaka Sample Medical", "2017", "NICU")
    )

    val demoEmployments = listOf(
        EmploymentHistoryEntity(
            "emp_d1", "doc_demo_1", "[DEMO] Metro Heart Institute", "Sample Medical College",
            "Registrar", "Cardiology", "2016-01", "2019-12", "Inpatient cardiac ward supervision (DEMO)."
        ),
        EmploymentHistoryEntity(
            "emp_d2", "doc_demo_2", "[DEMO] Coastal Mother & Child Clinic", "",
            "Medical Officer", "Gynecology", "2017-04", "2021-05", "Outpatient maternity care (DEMO)."
        )
    )

    val demoChambers = listOf(
        ChamberEntity(
            id = "ch_demo_1",
            doctorId = "doc_demo_1",
            facilityName = "[DEMO] Central Medical Hospital",
            facilityType = "HOSPITAL",
            district = "Dhaka",
            upazila = "Dhanmondi",
            fullAddress = "Room 304, House 14, Road 6, Dhanmondi, Dhaka",
            locationArea = "Dhanmondi",
            googleMapsUrl = "https://maps.google.com/?q=Dhanmondi+Dhaka",
            serialPhone = "+8801700000301",
            instructions = "সকাল ১০টা থেকে দুপুর ১টা পর্যন্ত সিরিয়াল নেওয়া হয়।",
            isActive = true
        ),
        ChamberEntity(
            id = "ch_demo_2",
            doctorId = "doc_demo_1",
            facilityName = "[DEMO] Pragati Diagnostic Center",
            facilityType = "DIAGNOSTIC_CENTER",
            district = "Dhaka",
            upazila = "Mirpur",
            fullAddress = "2nd Floor, Plot 8, Mirpur 10, Dhaka",
            locationArea = "Mirpur 10",
            googleMapsUrl = "https://maps.google.com/?q=Mirpur+10+Dhaka",
            serialPhone = "+8801700000201",
            instructions = "বিকাল ৪টার পর রিপোর্ট দেখা হয়।",
            isActive = true
        ),
        ChamberEntity(
            id = "ch_demo_3",
            doctorId = "doc_demo_2",
            facilityName = "[DEMO] Chattogram City Health Care",
            facilityType = "HOSPITAL",
            district = "Chattogram",
            upazila = "Panchlaish",
            fullAddress = "Room 202, Probartak Circle, Chattogram",
            locationArea = "Panchlaish",
            googleMapsUrl = "https://maps.google.com/?q=Panchlaish+Chattogram",
            serialPhone = "+8801700000102",
            instructions = "আগে ফোন করে সিরিয়াল নিশ্চিত করুন।",
            isActive = true
        ),
        ChamberEntity(
            id = "ch_demo_4",
            doctorId = "doc_demo_3",
            facilityName = "[DEMO] Sylhet Specialized Hospital",
            facilityType = "HOSPITAL",
            district = "Sylhet",
            upazila = "Sylhet Sadar",
            fullAddress = "Subhanighat Point, Sylhet",
            locationArea = "Subhanighat",
            googleMapsUrl = "https://maps.google.com/?q=Sylhet+Bangladesh",
            serialPhone = "+8801700000103",
            instructions = "শুক্রবার চেম্বার বন্ধ থাকে।",
            isActive = true
        ),
        ChamberEntity(
            id = "ch_demo_5",
            doctorId = "doc_demo_4",
            facilityName = "[DEMO] Pragati Diagnostic Center",
            facilityType = "DIAGNOSTIC_CENTER",
            district = "Dhaka",
            upazila = "Mirpur",
            fullAddress = "Room 105, Mirpur 10, Dhaka",
            locationArea = "Mirpur 10",
            googleMapsUrl = "https://maps.google.com/?q=Mirpur+10+Dhaka",
            serialPhone = "+8801700000304",
            instructions = "টিকা ও শিশু স্বাস্থ্য কার্ড সাথে আনুন।",
            isActive = true
        )
    )

    val demoSchedules: List<ChamberScheduleEntity> = buildList {
        var idx = 1
        // Give demo chambers realistic multi-day schedules including today so "Available Today" works out of box
        val allDays = DayOfWeekBd.entries
        allDays.filter { it != DayOfWeekBd.FRIDAY }.forEach { day ->
            add(ChamberScheduleEntity("sch_${idx++}", "ch_demo_1", "doc_demo_1", day.name, "05:00 PM", "08:30 PM", "Evening"))
            add(ChamberScheduleEntity("sch_${idx++}", "ch_demo_3", "doc_demo_2", day.name, "04:00 PM", "07:30 PM", "Afternoon"))
            add(ChamberScheduleEntity("sch_${idx++}", "ch_demo_4", "doc_demo_3", day.name, "03:30 PM", "08:00 PM", "Evening"))
        }
        listOf(DayOfWeekBd.SATURDAY, DayOfWeekBd.MONDAY, DayOfWeekBd.WEDNESDAY, DayOfWeekBd.today()).distinct().forEach { day ->
            add(ChamberScheduleEntity("sch_${idx++}", "ch_demo_2", "doc_demo_1", day.name, "09:00 AM", "12:00 PM", "Morning"))
            add(ChamberScheduleEntity("sch_${idx++}", "ch_demo_5", "doc_demo_4", day.name, "05:30 PM", "09:00 PM", "Evening"))
        }
    }

    val initialNotifications = listOf(
        NotificationEntity(
            id = "notif_1",
            titleBn = "স্বাগতম — Doctor Directory Bangladesh",
            titleEn = "Welcome to Doctor Directory Bangladesh",
            messageBn = "ডাক্তারদের চেম্বারের সময়সূচী ও ঠিকানা সহজেই খুঁজুন। নমুনা প্রোফাইলগুলো DEMO ব্যাজসহ চিহ্নিত রয়েছে।",
            messageEn = "Find doctor chamber schedules & locations easily. Sample entries are clearly badged DEMO.",
            targetRole = "ALL"
        )
    )

    suspend fun populateIfEmpty(db: AppDatabase) {
        if (db.userDao().getByEmailOrId("admin") == null) {
            db.userDao().insert(hiddenAdminUser)
            db.specialtyDao().insertAll(specialties)
            db.locationDao().insertAll(locations)
            db.settingsDao().save(SiteSettingsEntity())
            db.hospitalDao().insertAll(demoHospitals)
            db.diagnosticCenterDao().insertAll(demoDiagnosticCenters)
            db.doctorDao().insertAll(demoDoctors)
            db.educationDao().insertAll(demoEducations)
            db.employmentDao().insertAll(demoEmployments)
            db.chamberDao().insertAll(demoChambers)
            db.chamberScheduleDao().insertAll(demoSchedules)
            db.notificationDao().insertAll(initialNotifications)
        }
    }
}
