// Bilingual Dictionary (Bangla & English), Bangladesh 8-Division / 64-District Hierarchy,
// Safe DOM helpers, and URL/Phone sanitizers for Doctor List BD Public Website.

export const DICT = {
  bn: {
    site_name: "Doctor List BD",
    header_tagline: "বাংলাদেশ স্বাস্থ্যসেবা ডিরেক্টরি",
    nav_home: "হোম",
    nav_doctors: "ডাক্তার খুঁজুন",
    nav_hospitals: "হাসপাতাল",
    nav_specialties: "বিশেষজ্ঞতা",
    nav_about: "আমাদের সম্পর্কে",
    nav_contact: "যোগাযোগ",
    nav_privacy: "প্রাইভেসি পলিসি",
    nav_terms: "ব্যবহারের শর্তাবলী",

    search_placeholder_global: "ডাক্তার, বিশেষজ্ঞতা, হাসপাতাল, জেলা বা উপজেলা খুঁজুন...",
    btn_search: "খুঁজুন",
    btn_reset: "রিসেট",
    btn_view_profile: "প্রোফাইল দেখুন",
    btn_view_details: "বিস্তারিত দেখুন",
    btn_view_all: "সব দেখুন",
    btn_back_home: "হোমে ফিরে যান",
    btn_call_now: "কল করুন",
    btn_whatsapp: "হোয়াটসঅ্যাপ",
    btn_directions: "গুগল ম্যাপ",
    btn_share: "শেয়ার করুন",
    btn_visit_website: "ওয়েবসাইট ভিজিট",

    hero_badge: "বাংলাদেশের বিশ্বস্ত হেলথকেয়ার ডিরেক্টরি",
    hero_heading: "বাংলাদেশের সকল ডাক্তার ও হাসপাতাল এক ঠিকানায়",
    hero_subtitle: "সহজেই খুঁজে নিন আপনার প্রয়োজনীয় বিশেষজ্ঞ ডাক্তার ও হাসপাতাল।",
    hero_tab_doctor: "ডাক্তার খুঁজুন",
    hero_tab_hospital: "হাসপাতাল খুঁজুন",
    hero_tab_specialty: "বিশেষজ্ঞতা",
    hero_tab_location: "অবস্থান অনুযায়ী",
    hero_input_doctor: "ডাক্তারের নাম বা ডিগ্রি লিখুন...",
    hero_input_hospital: "হাসপাতাল বা ক্লিনিকের নাম লিখুন...",
    hero_input_specialty: "বিশেষজ্ঞতার নাম লিখুন...",
    hero_input_location: "জেলা বা উপজেলার নাম লিখুন...",

    stat_doctors: "মোট ডাক্তার",
    stat_hospitals: "মোট হাসপাতাল",
    stat_specialties: "মোট বিশেষজ্ঞতা",
    stat_districts: "মোট জেলা",

    sec_featured_doctors: "বিশিষ্ট ডাক্তারবৃন্দ",
    sec_featured_doctors_sub: "সক্রিয় ও যাচাইকৃত বিশেষজ্ঞ চিকিৎসকদের তালিকা",
    sec_popular_specialties: "জনপ্রিয় বিশেষজ্ঞতা",
    sec_popular_specialties_sub: "বিভাগ অনুযায়ী আপনার প্রয়োজনীয় বিশেষজ্ঞ ডাক্তার খুঁজুন",
    sec_featured_hospitals: "উল্লেখযোগ্য হাসপাতাল ও ক্লিনিক",
    sec_featured_hospitals_sub: "দেশব্যাপী নিবন্ধিত হাসপাতাল ও ডায়াগনস্টিক সেন্টার",
    sec_latest_doctors: "নতুন যুক্ত ডাক্তার",
    sec_latest_doctors_sub: "সম্প্রতি ডিরেক্টরিতে যুক্ত হওয়া সক্রিয় ডাক্তারবৃন্দ",
    sec_location_explorer: "অবস্থান অনুযায়ী খুঁজুন",
    sec_location_explorer_sub: "দেশ → বিভাগ → জেলা → উপজেলা অনুযায়ী ডাক্তার ও হাসপাতাল নির্বাচন করুন",

    country_bangladesh: "বাংলাদেশ",
    all_divisions: "সকল বিভাগ",
    all_districts: "সকল জেলা",
    all_upazilas: "সকল উপজেলা",
    all_specialties: "সকল বিশেষজ্ঞতা",
    all_hospitals: "সকল হাসপাতাল",
    all_types: "সকল ধরন",

    doctors_page_title: "ডাক্তার ডিরেক্টরি",
    doctors_page_sub: "বিশেষজ্ঞতা, জেলা, উপজেলা বা হাসপাতাল অনুযায়ী বাংলাদেশের সক্রিয় ডাক্তার খুঁজুন।",
    search_doctor_placeholder: "ডাক্তারের নাম, ডিগ্রি বা চেম্বার...",
    sort_recent: "নতুন যুক্ত আগে",
    sort_name_asc: "নাম অনুযায়ী (A–Z)",
    sort_name_desc: "নাম অনুযায়ী (Z–A)",
    showing_results: "মোট ফলাফল",
    doctors_unit: "জন ডাক্তার",
    hospitals_unit: "টি হাসপাতাল",
    specialties_unit: "টি বিশেষজ্ঞতা",

    hospitals_page_title: "হাসপাতাল ও ক্লিনিক ডিরেক্টরি",
    hospitals_page_sub: "জেলা ও উপজেলা অনুযায়ী বাংলাদেশের সক্রিয় হাসপাতাল এবং ডায়াগনস্টিক সেন্টার।",
    search_hospital_placeholder: "হাসপাতাল বা ক্লিনিকের নাম...",

    specialties_page_title: "সকল বিশেষজ্ঞতা",
    specialties_page_sub: "যেকোনো বিশেষজ্ঞতায় ক্লিক করে সংশ্লিষ্ট ডাক্তারদের তালিকা দেখুন।",
    search_specialty_placeholder: "বিশেষজ্ঞতা খুঁজুন...",

    lbl_bmdc_verified: "BMDC যাচাইকৃত",
    lbl_experience: "অভিজ্ঞতা",
    lbl_years: "বছর",
    lbl_designation: "পদবী",
    lbl_hospital: "হাসপাতাল / প্রতিষ্ঠান",
    lbl_specialty: "বিশেষজ্ঞতা",
    lbl_chambers: "চেম্বার ও সময়সূচী",
    lbl_visiting_schedule: "ভিজিটিং সময়সূচী",
    lbl_consultation_fee: "পরামর্শ ফি",
    lbl_appointment_contact: "সিরিয়াল ও যোগাযোগ",
    lbl_phone: "ফোন নম্বর",
    lbl_address: "ঠিকানা",
    lbl_about_doctor: "ডাক্তার সম্পর্কে",
    lbl_quick_info: "সংক্ষিপ্ত তথ্য",
    lbl_chamber_count: "চেম্বার সংখ্যা",
    lbl_instructions: "নির্দেশনা",
    lbl_not_specified: "উল্লেখ নেই",
    lbl_taka: "টাকা",
    lbl_multiple_chambers: "একাধিক চেম্বার",

    lbl_hospital_type: "ধরন",
    lbl_full_address: "সম্পূর্ণ ঠিকানা",
    lbl_district: "জেলা",
    lbl_upazila: "উপজেলা",
    lbl_division: "বিভাগ",
    lbl_website: "ওয়েবসাইট",
    lbl_departments: "উপলব্ধ বিভাগসমূহ",
    lbl_associated_doctors: "সংশ্লিষ্ট ডাক্তারবৃন্দ",
    lbl_about_hospital: "হাসপাতাল সম্পর্কে",

    notif_title: "নোটিফিকেশন",
    notif_mark_read: "সব পঠিত",
    notif_empty: "এখনো কোনো নোটিফিকেশন প্রকাশিত হয়নি।",
    notif_view_doctor: "ডাক্তারের প্রোফাইল দেখুন",
    filter_all: "সব",
    type_new_doctor: "নতুন ডাক্তার",
    type_app_update: "অ্যাপ আপডেট",
    type_general: "সাধারণ",

    loading_directory: "ডাক্তার ও হাসপাতালের তথ্য লোড হচ্ছে...",
    empty_doctors_title: "কোনো ডাক্তার পাওয়া যায়নি",
    empty_doctors_desc: "আপনার খোঁজা শর্ত অনুযায়ী কোনো সক্রিয় ডাক্তার পাওয়া যায়নি। অনুগ্রহ করে ফিল্টার পরিবর্তন করে আবার চেষ্টা করুন।",
    empty_hospitals_title: "কোনো হাসপাতাল পাওয়া যায়নি",
    empty_hospitals_desc: "আপনার নির্বাচিত শর্ত অনুযায়ী কোনো সক্রিয় হাসপাতাল পাওয়া যায়নি।",
    empty_specialties_title: "কোনো বিশেষজ্ঞতা পাওয়া যায়নি",
    empty_specialties_desc: "অ্যাডমিন প্যানেল থেকে বিশেষজ্ঞতা যুক্ত হলে এখানে প্রদর্শিত হবে।",
    not_found_title: "তথ্য পাওয়া যায়নি",
    not_found_doctor: "এই ডাক্তারের প্রোফাইলটি পাওয়া যায়নি অথবা বর্তমানে নিষ্ক্রিয় আছে।",
    not_found_hospital: "এই হাসপাতালের তথ্য পাওয়া যায়নি অথবা বর্তমানে নিষ্ক্রিয় আছে।",
    error_title: "তথ্য লোড করতে সমস্যা হয়েছে",
    error_desc: "ইন্টারনেট সংযোগ পরীক্ষা করে পুনরায় চেষ্টা করুন।",
    btn_retry: "পুনরায় চেষ্টা করুন",

    auto_group_doctors: "ডাক্তার",
    auto_group_specialties: "বিশেষজ্ঞতা",
    auto_group_hospitals: "হাসপাতাল",
    auto_group_locations: "জেলা ও উপজেলা",
    auto_no_match: "কোনো মিল পাওয়া যায়নি",

    toast_link_copied: "প্রোফাইল লিংক কপি করা হয়েছে!",
    toast_share_failed: "লিংক শেয়ার করা যায়নি।",
    toast_marked_read: "সব নোটিফিকেশন পঠিত হিসেবে চিহ্নিত হয়েছে।",

    footer_desc: "বাংলাদেশের নির্ভরযোগ্য ডাক্তার, চেম্বারের সময়সূচী, হাসপাতাল ও ডায়াগনস্টিক সেন্টার ডিরেক্টরি।",
    footer_directory: "ডিরেক্টরি",
    footer_info: "গুরুত্বপূর্ণ তথ্য",
    footer_contact: "যোগাযোগ",
    footer_country: "বাংলাদেশ (৬৪ জেলা)",
    footer_rights: "সর্বস্বত্ব সংরক্ষিত।",
    footer_disclaimer: "জরুরি চিকিৎসা সেবায় নিকটস্থ হাসপাতালের জরুরি বিভাগে যোগাযোগ করুন।",

    prev_page: "পূর্ববর্তী",
    next_page: "পরবর্তী",

    about_title: "আমাদের সম্পর্কে — Doctor List BD",
    about_sub: "বাংলাদেশের প্রতিটি মানুষের হাতের নাগালে সঠিক ডাক্তার ও হাসপাতালের তথ্য পৌঁছে দেওয়াই আমাদের লক্ষ্য।",
    contact_title: "যোগাযোগ করুন",
    contact_sub: "তথ্য হালনাগাদ, নতুন ডাক্তার বা হাসপাতাল যুক্তকরণ এবং যেকোনো সহায়তার জন্য যোগাযোগ করুন।",
    privacy_title: "প্রাইভেসি পলিসি",
    privacy_sub: "Doctor List BD ভিজিটরদের গোপনীয়তা ও তথ্যের নিরাপত্তাকে সর্বোচ্চ গুরুত্ব দেয়।",
    terms_title: "ব্যবহারের শর্তাবলী",
    terms_sub: "Doctor List BD ওয়েবসাইট ও ডিরেক্টরি সেবা ব্যবহারের নিয়মাবলী।"
  },

  en: {
    site_name: "Doctor List BD",
    header_tagline: "Bangladesh Healthcare Directory",
    nav_home: "Home",
    nav_doctors: "Find Doctors",
    nav_hospitals: "Hospitals",
    nav_specialties: "Specialties",
    nav_about: "About Us",
    nav_contact: "Contact Us",
    nav_privacy: "Privacy Policy",
    nav_terms: "Terms of Service",

    search_placeholder_global: "Search doctors, specialties, hospitals, districts or upazilas...",
    btn_search: "Search",
    btn_reset: "Reset",
    btn_view_profile: "View Profile",
    btn_view_details: "View Details",
    btn_view_all: "View All",
    btn_back_home: "Back to Home",
    btn_call_now: "Call Now",
    btn_whatsapp: "WhatsApp",
    btn_directions: "Get Directions",
    btn_share: "Share Profile",
    btn_visit_website: "Visit Website",

    hero_badge: "Trusted Healthcare Directory of Bangladesh",
    hero_heading: "All Doctors & Hospitals of Bangladesh in One Place",
    hero_subtitle: "Easily find your required specialist doctors, chambers, and hospitals across Bangladesh.",
    hero_tab_doctor: "Doctor Search",
    hero_tab_hospital: "Hospital Search",
    hero_tab_specialty: "Specialty Search",
    hero_tab_location: "Location Search",
    hero_input_doctor: "Type doctor name or degree...",
    hero_input_hospital: "Type hospital or clinic name...",
    hero_input_specialty: "Type specialty name...",
    hero_input_location: "Type district or upazila...",

    stat_doctors: "Total Doctors",
    stat_hospitals: "Total Hospitals",
    stat_specialties: "Total Specialties",
    stat_districts: "Total Districts",

    sec_featured_doctors: "Featured Doctors",
    sec_featured_doctors_sub: "Active specialist physicians across Bangladesh",
    sec_popular_specialties: "Popular Specialties",
    sec_popular_specialties_sub: "Browse verified specialists by medical department",
    sec_featured_hospitals: "Featured Hospitals & Clinics",
    sec_featured_hospitals_sub: "Registered hospitals and diagnostic centers nationwide",
    sec_latest_doctors: "Latest Doctors",
    sec_latest_doctors_sub: "Recently added active doctors in our directory",
    sec_location_explorer: "Browse by Location",
    sec_location_explorer_sub: "Explore doctors and hospitals via Country → Division → District → Upazila",

    country_bangladesh: "Bangladesh",
    all_divisions: "All Divisions",
    all_districts: "All Districts",
    all_upazilas: "All Upazilas",
    all_specialties: "All Specialties",
    all_hospitals: "All Hospitals",
    all_types: "All Types",

    doctors_page_title: "Doctor Directory",
    doctors_page_sub: "Search active doctors across Bangladesh by specialty, district, upazila, or hospital.",
    search_doctor_placeholder: "Doctor name, degree or chamber...",
    sort_recent: "Recently Added",
    sort_name_asc: "Name (A–Z)",
    sort_name_desc: "Name (Z–A)",
    showing_results: "Total Results",
    doctors_unit: "Doctors",
    hospitals_unit: "Hospitals",
    specialties_unit: "Specialties",

    hospitals_page_title: "Hospital Directory",
    hospitals_page_sub: "Find active hospitals, clinics, and diagnostic centers by district and upazila.",
    search_hospital_placeholder: "Hospital or clinic name...",

    specialties_page_title: "Specialty Directory",
    specialties_page_sub: "Click any medical specialty to view its associated active doctors.",
    search_specialty_placeholder: "Search specialties...",

    lbl_bmdc_verified: "BMDC Verified",
    lbl_experience: "Experience",
    lbl_years: "Years",
    lbl_designation: "Designation",
    lbl_hospital: "Hospital / Clinic",
    lbl_specialty: "Specialty",
    lbl_chambers: "Chambers & Visiting Schedule",
    lbl_visiting_schedule: "Visiting Schedule",
    lbl_consultation_fee: "Consultation Fee",
    lbl_appointment_contact: "Appointment Contact",
    lbl_phone: "Phone Number",
    lbl_address: "Address",
    lbl_about_doctor: "About Doctor",
    lbl_quick_info: "Quick Info",
    lbl_chamber_count: "Chambers",
    lbl_instructions: "Instructions",
    lbl_not_specified: "Not specified",
    lbl_taka: "BDT",
    lbl_multiple_chambers: "Multiple Chambers",

    lbl_hospital_type: "Hospital Type",
    lbl_full_address: "Full Address",
    lbl_district: "District",
    lbl_upazila: "Upazila",
    lbl_division: "Division",
    lbl_website: "Website",
    lbl_departments: "Available Departments",
    lbl_associated_doctors: "Associated Doctors",
    lbl_about_hospital: "About Hospital",

    notif_title: "Notifications",
    notif_mark_read: "Mark all read",
    notif_empty: "No notifications published yet.",
    notif_view_doctor: "View Doctor Profile",
    filter_all: "All",
    type_new_doctor: "New Doctor",
    type_app_update: "App Update",
    type_general: "General",

    loading_directory: "Loading doctors and hospitals...",
    empty_doctors_title: "No Doctors Found",
    empty_doctors_desc: "No active doctors matched your filters. Try adjusting or resetting your search criteria.",
    empty_hospitals_title: "No Hospitals Found",
    empty_hospitals_desc: "No active hospitals matched your filters.",
    empty_specialties_title: "No Specialties Found",
    empty_specialties_desc: "Specialties added via the Admin Panel will appear here automatically.",
    not_found_title: "Record Not Found",
    not_found_doctor: "This doctor profile was not found or is currently inactive.",
    not_found_hospital: "This hospital profile was not found or is currently inactive.",
    error_title: "Unable to Load Directory",
    error_desc: "Please check your internet connection and try again.",
    btn_retry: "Retry",

    auto_group_doctors: "Doctors",
    auto_group_specialties: "Specialties",
    auto_group_hospitals: "Hospitals",
    auto_group_locations: "Districts & Upazilas",
    auto_no_match: "No matching results",

    toast_link_copied: "Profile link copied to clipboard!",
    toast_share_failed: "Unable to share link.",
    toast_marked_read: "All notifications marked as read.",

    footer_desc: "Trusted healthcare directory of specialist doctors, chamber schedules, hospitals, and diagnostic centers in Bangladesh.",
    footer_directory: "Directory",
    footer_info: "Information",
    footer_contact: "Contact",
    footer_country: "Bangladesh (64 Districts)",
    footer_rights: "All rights reserved.",
    footer_disclaimer: "In case of a medical emergency, please contact your nearest hospital emergency department.",

    prev_page: "Previous",
    next_page: "Next",

    about_title: "About Doctor List BD",
    about_sub: "Making verified doctor schedules and hospital information accessible to everyone in Bangladesh.",
    contact_title: "Contact Us",
    contact_sub: "Reach out for listing updates, corrections, or general healthcare directory support.",
    privacy_title: "Privacy Policy",
    privacy_sub: "How Doctor List BD handles visitor information and safeguards privacy.",
    terms_title: "Terms of Service",
    terms_sub: "Guidelines and conditions for using the Doctor List BD public directory."
  }
};

// Complete Bangladesh 8 Divisions -> 64 Districts mapping (English & Bangla aliases)
export const BD_DIVISIONS = [
  {
    id: "dhaka",
    en: "Dhaka",
    bn: "ঢাকা",
    districts: [
      "Dhaka", "Faridpur", "Gazipur", "Gopalganj", "Kishoreganj", "Madaripur",
      "Manikganj", "Munshiganj", "Narayanganj", "Narsingdi", "Rajbari", "Shariatpur", "Tangail"
    ]
  },
  {
    id: "chattogram",
    en: "Chattogram",
    bn: "চট্টগ্রাম",
    districts: [
      "Bandarban", "Brahmanbaria", "Chandpur", "Chattogram", "Chittagong", "Cumilla", "Comilla",
      "Cox's Bazar", "Feni", "Khagrachhari", "Lakshmipur", "Noakhali", "Rangamati"
    ]
  },
  {
    id: "rajshahi",
    en: "Rajshahi",
    bn: "রাজশাহী",
    districts: [
      "Bogura", "Bogra", "Chapainawabganj", "Joypurhat", "Naogaon", "Natore", "Pabna", "Rajshahi", "Sirajganj"
    ]
  },
  {
    id: "khulna",
    en: "Khulna",
    bn: "খুলনা",
    districts: [
      "Bagerhat", "Chuadanga", "Jashore", "Jessore", "Jhenaidah", "Khulna",
      "Kushtia", "Magura", "Meherpur", "Narail", "Satkhira"
    ]
  },
  {
    id: "barishal",
    en: "Barishal",
    bn: "বরিশাল",
    districts: [
      "Barguna", "Barishal", "Barisal", "Bhola", "Jhalokati", "Patuakhali", "Pirojpur"
    ]
  },
  {
    id: "sylhet",
    en: "Sylhet",
    bn: "সিলেট",
    districts: ["Habiganj", "Moulvibazar", "Sunamganj", "Sylhet"]
  },
  {
    id: "rangpur",
    en: "Rangpur",
    bn: "রংপুর",
    districts: [
      "Dinajpur", "Gaibandha", "Kurigram", "Lalmonirhat", "Nilphamari", "Panchagarh", "Rangpur", "Thakurgaon"
    ]
  },
  {
    id: "mymensingh",
    en: "Mymensingh",
    bn: "ময়মনসিংহ",
    districts: ["Jamalpur", "Mymensingh", "Netrokona", "Sherpur"]
  }
];

const BN_DISTRICT_TO_DIVISION = {
  "ঢাকা": "Dhaka", "ফরিদপুর": "Dhaka", "গাজীপুর": "Dhaka", "গোপালগঞ্জ": "Dhaka",
  "কিশোরগঞ্জ": "Dhaka", "মাদারীপুর": "Dhaka", "মানিকগঞ্জ": "Dhaka", "মুন্সীগঞ্জ": "Dhaka",
  "নারায়ণগঞ্জ": "Dhaka", "নরসিংদী": "Dhaka", "রাজবাড়ী": "Dhaka", "শরীয়তপুর": "Dhaka", "টাঙ্গাইল": "Dhaka",
  "চট্টগ্রাম": "Chattogram", "বান্দরবান": "Chattogram", "ব্রাহ্মণবাড়িয়া": "Chattogram", "চাঁদপুর": "Chattogram",
  "কুমিল্লা": "Chattogram", "কক্সবাজার": "Chattogram", "ফেনী": "Chattogram", "খাগড়াছড়ি": "Chattogram",
  "লক্ষ্মীপুর": "Chattogram", "নোয়াখালী": "Chattogram", "রাঙ্গামাটি": "Chattogram",
  "রাজশাহী": "Rajshahi", "বগুড়া": "Rajshahi", "চাঁপাইনবাবগঞ্জ": "Rajshahi", "জয়পুরহাট": "Rajshahi",
  "নওগাঁ": "Rajshahi", "নাটোর": "Rajshahi", "পাবনা": "Rajshahi", "সিরাজগঞ্জ": "Rajshahi",
  "খুলনা": "Khulna", "বাগেরহাট": "Khulna", "চুয়াডাঙ্গা": "Khulna", "যশোর": "Khulna", "ঝিনাইদহ": "Khulna",
  "কুষ্টিয়া": "Khulna", "মাগুরা": "Khulna", "মেহেরপুর": "Khulna", "নড়াইল": "Khulna", "সাতক্ষীরা": "Khulna",
  "বরিশাল": "Barishal", "বরগুনা": "Barishal", "ভোলা": "Barishal", "ঝালকাঠি": "Barishal", "পটুয়াখালী": "Barishal", "পিরোজপুর": "Barishal",
  "সিলেট": "Sylhet", "হবিগঞ্জ": "Sylhet", "মৌলভীবাজার": "Sylhet", "সুনামগঞ্জ": "Sylhet",
  "রংপুর": "Rangpur", "দিনাজপুর": "Rangpur", "গাইবান্ধা": "Rangpur", "কুড়িগ্রাম": "Rangpur",
  "লালমনিরহাট": "Rangpur", "নীলফামারী": "Rangpur", "পঞ্চগড়": "Rangpur", "ঠাকুরগাঁও": "Rangpur",
  "ময়মনসিংহ": "Mymensingh", "জামালপুর": "Mymensingh", "নেত্রকোনা": "Mymensingh", "শেরপুর": "Mymensingh"
};

export function inferDivisionFromDistrict(districtName) {
  const raw = String(districtName || "").trim();
  if (!raw) return "";
  if (BN_DISTRICT_TO_DIVISION[raw]) return BN_DISTRICT_TO_DIVISION[raw];
  const lower = raw.toLowerCase();
  for (const div of BD_DIVISIONS) {
    if (div.en.toLowerCase() === lower || div.bn === raw) return div.en;
    if (div.districts.some((d) => d.toLowerCase() === lower)) return div.en;
  }
  return "";
}

export function getDivisionLabel(divisionEn, lang) {
  const found = BD_DIVISIONS.find(
    (d) => d.en.toLowerCase() === String(divisionEn || "").toLowerCase() || d.bn === divisionEn
  );
  if (!found) return divisionEn || "";
  return lang === "bn" ? found.bn : found.en;
}

export function t(lang, key) {
  const table = DICT[lang] || DICT.bn;
  return table[key] ?? DICT.en[key] ?? key;
}

export function pickLang(lang, bnVal, enVal) {
  const bn = String(bnVal || "").trim();
  const en = String(enVal || "").trim();
  if (lang === "bn") return bn || en;
  return en || bn;
}

export function slugify(text, fallbackId = "") {
  const base = String(text || "")
    .normalize("NFKD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9\s-]/g, " ")
    .trim()
    .replace(/\s+/g, "-")
    .replace(/-+/g, "-")
    .slice(0, 72);
  if (base) return base;
  const cleanId = String(fallbackId || "")
    .toLowerCase()
    .replace(/[^a-z0-9-_]/g, "");
  return cleanId || "profile";
}

export function safeHttpUrl(url) {
  const raw = String(url || "").trim();
  if (!raw) return "";
  try {
    const parsed = new URL(raw);
    if (parsed.protocol === "https:" || parsed.protocol === "http:") {
      return parsed.href;
    }
    return "";
  } catch {
    return "";
  }
}

export function cleanPhoneDigits(phone) {
  const raw = String(phone || "").trim();
  if (!raw) return "";
  const cleaned = raw.replace(/[^+\d]/g, "");
  return cleaned.length >= 6 ? cleaned : "";
}

export function buildTelHref(phone) {
  const cleaned = cleanPhoneDigits(phone);
  return cleaned ? `tel:${cleaned}` : "";
}

export function buildWhatsAppHref(phone, messageText = "") {
  const digits = String(phone || "").replace(/\D/g, "");
  if (digits.length < 7) return "";
  let intl = digits;
  if (intl.startsWith("01") && intl.length === 11) {
    intl = "88" + intl;
  } else if (intl.startsWith("0") && intl.length === 10) {
    intl = "880" + intl.slice(1);
  }
  const base = `https://wa.me/${intl}`;
  return messageText ? `${base}?text=${encodeURIComponent(messageText)}` : base;
}

export function buildGoogleMapsHref(explicitUrl, fallbackQuery) {
  const valid = safeHttpUrl(explicitUrl);
  if (valid) return valid;
  const q = String(fallbackQuery || "").trim();
  if (!q) return "";
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}`;
}

export function formatDateTime(ts, lang = "bn") {
  const n = Number(ts);
  if (!n || Number.isNaN(n)) return "";
  try {
    return new Intl.DateTimeFormat(lang === "bn" ? "bn-BD" : "en-GB", {
      day: "2-digit",
      month: "short",
      year: "numeric",
      hour: "2-digit",
      minute: "2-digit"
    }).format(new Date(n));
  } catch {
    return new Date(n).toLocaleString();
  }
}

export function formatNumber(num, lang = "bn") {
  const n = Number(num) || 0;
  try {
    return new Intl.NumberFormat(lang === "bn" ? "bn-BD" : "en-US").format(n);
  } catch {
    return String(n);
  }
}

// Safe DOM builder — never assigns untrusted HTML strings.
export function el(tag, attrs = {}, children = []) {
  const node = document.createElement(tag);
  if (attrs && typeof attrs === "object") {
    for (const [key, val] of Object.entries(attrs)) {
      if (val === null || val === undefined || val === false) continue;
      if (key === "className") {
        node.className = String(val);
      } else if (key === "text") {
        node.textContent = String(val);
      } else if (key === "dataset" && typeof val === "object") {
        for (const [dk, dv] of Object.entries(val)) {
          if (dv !== undefined && dv !== null) node.dataset[dk] = String(dv);
        }
      } else if (key.startsWith("on") && typeof val === "function") {
        node.addEventListener(key.slice(2).toLowerCase(), val);
      } else if (key === "value" && ("value" in node)) {
        node.value = String(val);
      } else if (val === true) {
        node.setAttribute(key, "");
      } else {
        node.setAttribute(key, String(val));
      }
    }
  }
  const list = Array.isArray(children) ? children : [children];
  for (const child of list) {
    if (child === null || child === undefined || child === false) continue;
    if (typeof child === "string" || typeof child === "number") {
      node.appendChild(document.createTextNode(String(child)));
    } else if (child instanceof Node) {
      node.appendChild(child);
    }
  }
  return node;
}

export function svgIcon(paths, size = 18, viewBox = "0 0 24 24") {
  const ns = "http://www.w3.org/2000/svg";
  const svg = document.createElementNS(ns, "svg");
  svg.setAttribute("width", String(size));
  svg.setAttribute("height", String(size));
  svg.setAttribute("viewBox", viewBox);
  svg.setAttribute("fill", "none");
  svg.setAttribute("stroke", "currentColor");
  svg.setAttribute("stroke-width", "2");
  svg.setAttribute("stroke-linecap", "round");
  svg.setAttribute("stroke-linejoin", "round");
  svg.setAttribute("aria-hidden", "true");
  const arr = Array.isArray(paths) ? paths : [paths];
  for (const d of arr) {
    const p = document.createElementNS(ns, "path");
    p.setAttribute("d", d);
    svg.appendChild(p);
  }
  return svg;
}
