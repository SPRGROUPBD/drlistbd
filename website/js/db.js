// Firebase Realtime Database Public Read-Only Data Layer for Doctor List BD
// Uses Firebase Modular SDK (no authentication required for public visitors).
// Strictly reads public published nodes only; never writes to or overwrites the database.

import { ref, onValue } from "https://www.gstatic.com/firebasejs/11.6.0/firebase-database.js";
import { getPublicDatabase } from "./firebase-config.js";
import { inferDivisionFromDistrict, safeHttpUrl, slugify } from "./i18n.js";

const DAY_ORDER = [
  "Saturday", "Sunday", "Monday", "Tuesday", "Wednesday", "Thursday", "Friday",
  "শনিবার", "রবিবার", "সোমবার", "মঙ্গলবার", "বুধবার", "বৃহস্পতিবার", "শুক্রবার"
];

function toEntries(raw) {
  if (!raw || typeof raw !== "object") return [];
  if (Array.isArray(raw)) {
    return raw
      .map((item, idx) => (item && typeof item === "object" ? [String(item.id || idx), item] : null))
      .filter(Boolean);
  }
  return Object.entries(raw).filter(([, val]) => val && typeof val === "object");
}

function isRecordActive(rec) {
  if (!rec || typeof rec !== "object") return false;
  if (rec.isDemoData === true) return false;
  if (rec.isActive === false || rec.active === false) return false;
  const status = String(rec.status || "").trim().toUpperCase();
  if (status && ["INACTIVE", "DRAFT", "REJECTED", "PENDING", "DELETED", "ARCHIVED", "SUSPENDED"].includes(status)) {
    return false;
  }
  const approval = String(rec.approvalStatus || "").trim().toUpperCase();
  if (approval && ["REJECTED", "PENDING", "DRAFT"].includes(approval)) {
    return false;
  }
  return true;
}

function parseTimestamp(val) {
  if (typeof val === "number" && Number.isFinite(val)) return val;
  if (typeof val === "string" && val.trim()) {
    const num = Number(val);
    if (Number.isFinite(num) && num > 1000000000) return num;
    const parsed = Date.parse(val);
    if (!Number.isNaN(parsed)) return parsed;
  }
  return 0;
}

function makeUniqueSlug(preferred, fallbackName, id, usedSet) {
  const base = slugify(preferred || fallbackName, id);
  if (!usedSet.has(base)) {
    usedSet.add(base);
    return base;
  }
  const suffix = slugify(String(id || "").slice(-6)) || String(usedSet.size + 1);
  let candidate = `${base}-${suffix}`;
  let counter = 2;
  while (usedSet.has(candidate)) {
    candidate = `${base}-${suffix}-${counter++}`;
  }
  usedSet.add(candidate);
  return candidate;
}

function normalizeSchedules(rawSchedules) {
  const byChamber = new Map();
  const byDoctor = new Map();
  for (const [id, s] of toEntries(rawSchedules)) {
    const item = {
      id,
      chamberId: String(s.chamberId || ""),
      doctorId: String(s.doctorId || ""),
      dayOfWeek: String(s.dayOfWeek || s.day || "").trim(),
      openTime: String(s.openTime || s.startTime || "").trim(),
      closeTime: String(s.closeTime || s.endTime || "").trim(),
      slotLabel: String(s.slotLabel || s.timeRange || "").trim()
    };
    if (!item.dayOfWeek && !item.slotLabel && !item.openTime) continue;
    if (item.chamberId) {
      if (!byChamber.has(item.chamberId)) byChamber.set(item.chamberId, []);
      byChamber.get(item.chamberId).push(item);
    }
    if (item.doctorId) {
      if (!byDoctor.has(item.doctorId)) byDoctor.set(item.doctorId, []);
      byDoctor.get(item.doctorId).push(item);
    }
  }
  const sortFn = (a, b) => {
    const ia = DAY_ORDER.indexOf(a.dayOfWeek);
    const ib = DAY_ORDER.indexOf(b.dayOfWeek);
    return (ia === -1 ? 99 : ia) - (ib === -1 ? 99 : ib);
  };
  for (const arr of byChamber.values()) arr.sort(sortFn);
  for (const arr of byDoctor.values()) arr.sort(sortFn);
  return { byChamber, byDoctor };
}

function normalizeChambers(rawChambers, scheduleMaps) {
  const byDoctor = new Map();
  for (const [id, c] of toEntries(rawChambers)) {
    if (!isRecordActive(c)) continue;
    const doctorId = String(c.doctorId || "").trim();
    if (!doctorId) continue;
    const district = String(c.district || "").trim();
    const chamber = {
      id,
      doctorId,
      facilityName: String(c.facilityName || c.hospitalName || c.chamberName || "").trim(),
      facilityType: String(c.facilityType || "").trim(),
      chamberName: String(c.chamberName || "").trim(),
      division: String(c.division || "").trim() || inferDivisionFromDistrict(district),
      district,
      upazila: String(c.upazila || c.area || "").trim(),
      address: String(c.fullAddress || c.address || "").trim(),
      googleMapsUrl: safeHttpUrl(c.googleMapsUrl || c.mapUrl || c.mapsUrl),
      appointmentPhone: String(c.serialPhone || c.appointmentPhone || c.phone || c.contactNumber || "").trim(),
      consultationFee: String(c.consultationFee || c.fee || "").trim(),
      visitingDays: String(c.visitingDays || "").trim(),
      visitingHours: String(c.visitingHours || c.visitingTime || "").trim(),
      instructions: String(c.instructions || c.notes || "").trim(),
      schedules: scheduleMaps.byChamber.get(id) || []
    };
    if (!byDoctor.has(doctorId)) byDoctor.set(doctorId, []);
    byDoctor.get(doctorId).push(chamber);
  }
  return byDoctor;
}

function extractEmbeddedChambers(rawDoctor, doctorId) {
  if (!rawDoctor.chambers) return [];
  return toEntries(rawDoctor.chambers)
    .filter(([, c]) => isRecordActive(c))
    .map(([cid, c]) => {
      const district = String(c.district || rawDoctor.district || "").trim();
      const rawSched = Array.isArray(c.schedules) ? c.schedules : toEntries(c.schedules).map(([, v]) => v);
      const schedules = rawSched
        .filter((s) => s && typeof s === "object")
        .map((s) => ({
          dayOfWeek: String(s.dayOfWeek || s.day || "").trim(),
          openTime: String(s.openTime || s.startTime || "").trim(),
          closeTime: String(s.closeTime || s.endTime || "").trim(),
          slotLabel: String(s.slotLabel || s.timeRange || "").trim()
        }))
        .filter((s) => s.dayOfWeek || s.slotLabel || s.openTime);
      return {
        id: cid,
        doctorId,
        facilityName: String(c.facilityName || c.hospitalName || c.chamberName || "").trim(),
        facilityType: String(c.facilityType || "").trim(),
        chamberName: String(c.chamberName || "").trim(),
        division: String(c.division || "").trim() || inferDivisionFromDistrict(district),
        district,
        upazila: String(c.upazila || c.area || "").trim(),
        address: String(c.fullAddress || c.address || "").trim(),
        googleMapsUrl: safeHttpUrl(c.googleMapsUrl || c.mapUrl || c.mapsUrl),
        appointmentPhone: String(c.serialPhone || c.appointmentPhone || c.phone || c.contactNumber || "").trim(),
        consultationFee: String(c.consultationFee || c.fee || "").trim(),
        visitingDays: String(c.visitingDays || "").trim(),
        visitingHours: String(c.visitingHours || c.visitingTime || "").trim(),
        instructions: String(c.instructions || c.notes || "").trim(),
        schedules
      };
    });
}

function summarizeVisitingHours(chambers) {
  for (const ch of chambers) {
    if (ch.visitingHours) {
      return ch.visitingDays ? `${ch.visitingDays} (${ch.visitingHours})` : ch.visitingHours;
    }
    if (ch.schedules && ch.schedules.length) {
      const first = ch.schedules[0];
      const time = first.slotLabel || [first.openTime, first.closeTime].filter(Boolean).join(" - ");
      if (time) return first.dayOfWeek ? `${first.dayOfWeek}: ${time}` : time;
    }
  }
  return "";
}

function normalizeSpecialtyMap(rawSpecialties) {
  const list = [];
  const byId = new Map();
  for (const [id, sp] of toEntries(rawSpecialties)) {
    if (sp.isActive === false) continue;
    const nameEn = String(sp.nameEn || sp.name || sp.title || "").trim();
    const nameBn = String(sp.nameBn || "").trim();
    if (!nameEn && !nameBn) continue;
    const obj = {
      id: String(sp.id || id),
      nameEn: nameEn || nameBn,
      nameBn: nameBn || nameEn,
      iconKey: String(sp.iconKey || sp.icon || "").trim(),
      iconUrl: safeHttpUrl(sp.iconUrl || sp.imageUrl || sp.logoUrl),
      doctorCount: 0
    };
    list.push(obj);
    byId.set(obj.id, obj);
    byId.set(id, obj);
    if (obj.nameEn) byId.set(obj.nameEn.toLowerCase(), obj);
    if (obj.nameBn) byId.set(obj.nameBn.toLowerCase(), obj);
  }
  return { list, byId };
}

function normalizeDoctors(rawDoctors, chambersByDoctor, scheduleMaps, specMap) {
  const doctors = [];
  const usedSlugs = new Set();

  for (const [id, d] of toEntries(rawDoctors)) {
    if (!isRecordActive(d)) continue;
    const nameEn = String(d.fullNameEn || d.doctorName || d.nameEn || d.name || "").trim();
    const nameBn = String(d.fullNameBn || d.nameBn || "").trim();
    if (!nameEn && !nameBn) continue;

    const externalChambers = chambersByDoctor.get(id) || [];
    const embeddedChambers = extractEmbeddedChambers(d, id);
    let chambers = externalChambers.length ? externalChambers : embeddedChambers;

    // If doctor has inline chamber fields without chamber sub-records, synthesize 1 chamber view
    if (!chambers.length && (d.chamberAddress || d.address || d.currentHospital || d.hospitalName || d.district)) {
      const dist = String(d.district || "").trim();
      chambers = [
        {
          id: `${id}_primary`,
          doctorId: id,
          facilityName: String(d.currentHospital || d.hospitalName || d.chamberName || "").trim(),
          facilityType: "",
          chamberName: String(d.chamberName || "").trim(),
          division: String(d.division || "").trim() || inferDivisionFromDistrict(dist),
          district: dist,
          upazila: String(d.upazila || d.area || "").trim(),
          address: String(d.chamberAddress || d.address || "").trim(),
          googleMapsUrl: safeHttpUrl(d.googleMapsUrl || d.mapUrl),
          appointmentPhone: String(d.appointmentPhone || d.serialPhone || d.contactNumber || d.phone || "").trim(),
          consultationFee: String(d.consultationFee || d.fee || "").trim(),
          visitingDays: String(d.visitingDays || "").trim(),
          visitingHours: String(d.visitingHours || d.visitingSchedule || "").trim(),
          instructions: String(d.instructions || "").trim(),
          schedules: scheduleMaps.byDoctor.get(id) || []
        }
      ];
    }

    const specKey = String(d.mainSpecialtyId || d.specialtyId || d.specialty || "").trim();
    const matchedSpec =
      specMap.byId.get(specKey) ||
      specMap.byId.get(specKey.toLowerCase()) ||
      null;

    const specialtyId = matchedSpec ? matchedSpec.id : specKey;
    const specialtyEn = matchedSpec ? matchedSpec.nameEn : String(d.specialtyEn || d.specialty || specKey || "").trim();
    const specialtyBn = matchedSpec ? matchedSpec.nameBn : String(d.specialtyBn || d.specialty || specialtyEn || "").trim();

    const districts = Array.from(
      new Set([d.district, ...chambers.map((c) => c.district)].map((v) => String(v || "").trim()).filter(Boolean))
    );
    const upazilas = Array.from(
      new Set([d.upazila, ...chambers.map((c) => c.upazila)].map((v) => String(v || "").trim()).filter(Boolean))
    );
    const divisions = Array.from(
      new Set(
        [d.division, ...chambers.map((c) => c.division), ...districts.map(inferDivisionFromDistrict)]
          .map((v) => String(v || "").trim())
          .filter(Boolean)
      )
    );
    const hospitals = Array.from(
      new Set(
        [d.currentHospital, d.hospitalName, ...chambers.map((c) => c.facilityName)]
          .map((v) => String(v || "").trim())
          .filter(Boolean)
      )
    );

    // Only expose BMDC registration number if explicitly provided AND verified
    const rawBmdc = String(d.bmdcNumber || d.bmdc || "").trim();
    const isBmdcVerified = d.bmdcVerified === true || String(d.bmdcStatus || "").toUpperCase() === "VERIFIED";
    const verifiedBmdcNumber = rawBmdc && isBmdcVerified ? rawBmdc : "";

    const rawExp = d.totalExperienceYears ?? d.experienceYears ?? d.experience ?? "";
    const experienceText = String(rawExp || "").trim();

    const slug = makeUniqueSlug(d.slug, nameEn || nameBn, id, usedSlugs);
    const createdAt = parseTimestamp(d.createdAt || d.updatedAt);

    doctors.push({
      id,
      slug,
      nameEn: nameEn || nameBn,
      nameBn: nameBn || nameEn,
      photoUrl: safeHttpUrl(d.photoUrl || d.photo || d.imageUrl || d.avatarUrl),
      degrees: String(d.qualification || d.degrees || d.degree || "").trim(),
      specialtyId,
      specialtyEn,
      specialtyBn,
      subSpecialty: String(d.subSpecialty || "").trim(),
      designation: String(d.currentDesignation || d.designation || "").trim(),
      currentHospital: String(d.currentHospital || d.hospitalName || chambers[0]?.facilityName || "").trim(),
      experience: experienceText,
      verifiedBmdcNumber,
      phone: String(d.appointmentPhone || d.contactNumber || d.phone || chambers[0]?.appointmentPhone || "").trim(),
      whatsapp: String(d.whatsapp || d.whatsappNumber || d.appointmentPhone || d.contactNumber || d.phone || chambers[0]?.appointmentPhone || "").trim(),
      bioBn: String(d.bioBn || d.descriptionBn || "").trim(),
      bioEn: String(d.bioEn || d.description || d.bio || "").trim(),
      isFeatured: d.isFeatured === true || d.featured === true,
      createdAt,
      chambers,
      visitingHoursSummary: summarizeVisitingHours(chambers),
      primaryDistrict: districts[0] || "",
      primaryUpazila: upazilas[0] || "",
      primaryDivision: divisions[0] || "",
      districts,
      upazilas,
      divisions,
      hospitals
    });
  }

  doctors.sort((a, b) => (b.createdAt - a.createdAt) || a.nameEn.localeCompare(b.nameEn));
  return doctors;
}

function normalizeHospitals(rawHospitals, rawDiagnostics, doctors) {
  const combined = [
    ...toEntries(rawHospitals).map(([id, h]) => ({ id, h, defaultType: "Hospital" })),
    ...toEntries(rawDiagnostics).map(([id, h]) => ({ id, h, defaultType: "Diagnostic Center" }))
  ];
  const hospitals = [];
  const seenIds = new Set();
  const usedSlugs = new Set();

  for (const { id, h, defaultType } of combined) {
    if (seenIds.has(id)) continue;
    if (!isRecordActive(h)) continue;
    const nameEn = String(h.nameEn || h.name || h.hospitalName || "").trim();
    const nameBn = String(h.nameBn || "").trim();
    if (!nameEn && !nameBn) continue;
    seenIds.add(id);

    const district = String(h.district || "").trim();
    const division = String(h.division || "").trim() || inferDivisionFromDistrict(district);
    const upazila = String(h.upazila || h.area || "").trim();
    const slug = makeUniqueSlug(h.slug, nameEn || nameBn, id, usedSlugs);

    let departments = [];
    if (Array.isArray(h.departments)) {
      departments = h.departments.map((v) => String(v || "").trim()).filter(Boolean);
    } else if (typeof h.departments === "string" && h.departments.trim()) {
      departments = h.departments.split(",").map((v) => v.trim()).filter(Boolean);
    }

    const matchTokens = [nameEn.toLowerCase(), nameBn.toLowerCase()].filter((x) => x.length >= 3);
    const associatedDoctors = doctors.filter((doc) => {
      if (doc.hospitals.some((hn) => matchTokens.includes(hn.toLowerCase()))) return true;
      return doc.chambers.some((ch) => {
        const fn = ch.facilityName.toLowerCase();
        return fn && matchTokens.some((tok) => fn === tok || fn.includes(tok) || tok.includes(fn));
      });
    });

    if (!departments.length && associatedDoctors.length) {
      departments = Array.from(
        new Set(associatedDoctors.map((doc) => doc.specialtyEn || doc.specialtyBn).filter(Boolean))
      );
    }

    hospitals.push({
      id,
      slug,
      nameEn: nameEn || nameBn,
      nameBn: nameBn || nameEn,
      imageUrl: safeHttpUrl(h.imageUrl || h.logoUrl || h.logo || h.image || h.photoUrl),
      type: String(h.type || h.hospitalType || defaultType).trim(),
      descriptionBn: String(h.descriptionBn || "").trim(),
      descriptionEn: String(h.descriptionEn || h.description || "").trim(),
      address: String(h.fullAddress || h.address || "").trim(),
      division,
      district,
      upazila,
      phone: String(h.contactNumber || h.phone || h.hotline || "").trim(),
      website: safeHttpUrl(h.website || h.websiteUrl),
      googleMapsUrl: safeHttpUrl(h.googleMapsUrl || h.mapUrl || h.mapsUrl),
      departments,
      associatedDoctors,
      isFeatured: h.isFeatured === true || h.featured === true,
      createdAt: parseTimestamp(h.createdAt || h.updatedAt)
    });
  }

  hospitals.sort((a, b) => (b.associatedDoctors.length - a.associatedDoctors.length) || (b.createdAt - a.createdAt));
  return hospitals;
}

function normalizeNotifications(rawPrimary, rawSecondary, doctors) {
  const map = new Map();
  const doctorById = new Map(doctors.map((d) => [d.id, d]));

  const ingest = (raw) => {
    for (const [id, n] of toEntries(raw)) {
      if (!n || typeof n !== "object") continue;
      const nid = String(n.notificationId || id);
      const title = String(n.title || n.titleBn || n.titleEn || "").trim();
      const message = String(n.message || n.body || n.messageBn || n.messageEn || "").trim();
      if (!title && !message) continue;
      const rawType = String(n.messageType || n.type || "GENERAL").trim().toUpperCase();
      const messageType = ["NEW_DOCTOR", "APP_UPDATE", "GENERAL"].includes(rawType) ? rawType : "GENERAL";
      const selectedDoctorId = String(n.selectedDoctorId || n.doctorId || "").trim();
      const linkedDoctor = selectedDoctorId ? doctorById.get(selectedDoctorId) || null : null;

      map.set(nid, {
        id: nid,
        title,
        titleBn: String(n.titleBn || title).trim(),
        titleEn: String(n.titleEn || title).trim(),
        message,
        messageBn: String(n.messageBn || message).trim(),
        messageEn: String(n.messageEn || message).trim(),
        messageType,
        selectedDoctorId,
        selectedDoctorSlug: linkedDoctor ? linkedDoctor.slug : "",
        selectedDoctorName: String(n.selectedDoctorName || linkedDoctor?.nameEn || "").trim(),
        imageUrl: safeHttpUrl(n.imageUrl),
        createdAt: parseTimestamp(n.createdAt || n.timestamp || n.sentAt)
      });
    }
  };

  ingest(rawSecondary);
  ingest(rawPrimary);

  return Array.from(map.values()).sort((a, b) => b.createdAt - a.createdAt);
}

function buildLocationIndex(rawLocations, doctors, hospitals) {
  const divisionToDistricts = new Map();
  const districtToUpazilas = new Map();

  const register = (division, district, upazila) => {
    const dist = String(district || "").trim();
    const upz = String(upazila || "").trim();
    const div = String(division || "").trim() || inferDivisionFromDistrict(dist);
    if (div) {
      if (!divisionToDistricts.has(div)) divisionToDistricts.set(div, new Set());
      if (dist) divisionToDistricts.get(div).add(dist);
    }
    if (dist) {
      if (!districtToUpazilas.has(dist)) districtToUpazilas.set(dist, new Set());
      if (upz) districtToUpazilas.get(dist).add(upz);
    }
  };

  for (const [, loc] of toEntries(rawLocations)) {
    register(loc.division, loc.district, loc.upazila || loc.area);
  }
  for (const d of doctors) {
    for (const c of d.chambers) register(c.division, c.district, c.upazila);
    for (const dist of d.districts) register(inferDivisionFromDistrict(dist), dist, "");
  }
  for (const h of hospitals) {
    register(h.division, h.district, h.upazila);
  }

  const divisions = Array.from(divisionToDistricts.keys()).sort();
  const districts = Array.from(districtToUpazilas.keys()).sort();
  const upazilasByDistrict = {};
  for (const [dist, set] of districtToUpazilas.entries()) {
    upazilasByDistrict[dist] = Array.from(set).sort();
  }
  const districtsByDivision = {};
  for (const [div, set] of divisionToDistricts.entries()) {
    districtsByDivision[div] = Array.from(set).sort();
  }

  return { divisions, districts, districtsByDivision, upazilasByDistrict };
}

class DirectoryStore {
  constructor() {
    this.raw = {
      dirDoctors: null,
      topDoctors: null,
      chambers: null,
      schedules: null,
      dirHospitals: null,
      topHospitals: null,
      diagnostics: null,
      dirSpecialties: null,
      topSpecialties: null,
      dirLocations: null,
      topLocations: null,
      dirSettings: null,
      topSettings: null,
      topNotifications: null,
      dirNotifications: null
    };
    this.state = {
      loading: true,
      error: null,
      doctors: [],
      hospitals: [],
      specialties: [],
      notifications: [],
      settings: {},
      locations: { divisions: [], districts: [], districtsByDivision: {}, upazilasByDistrict: {} },
      stats: { totalDoctors: 0, totalHospitals: 0, totalSpecialties: 0, totalDistricts: 0 }
    };
    this.listeners = new Set();
    this.unsubscribers = [];
    this.initialized = false;
    this.initialLoadedCount = 0;
  }

  subscribe(fn) {
    this.listeners.add(fn);
    fn(this.state);
    return () => this.listeners.delete(fn);
  }

  emit() {
    for (const fn of this.listeners) {
      try {
        fn(this.state);
      } catch (err) {
        console.error("Subscriber render error:", err);
      }
    }
  }

  rebuild() {
    const rawDoctors =
      this.raw.dirDoctors && Object.keys(this.raw.dirDoctors).length
        ? this.raw.dirDoctors
        : this.raw.topDoctors || {};
    const rawHospitals =
      this.raw.dirHospitals && Object.keys(this.raw.dirHospitals).length
        ? this.raw.dirHospitals
        : this.raw.topHospitals || {};
    const rawSpecialties =
      this.raw.dirSpecialties && Object.keys(this.raw.dirSpecialties).length
        ? this.raw.dirSpecialties
        : this.raw.topSpecialties || {};
    const rawLocations =
      this.raw.dirLocations && Object.keys(this.raw.dirLocations).length
        ? this.raw.dirLocations
        : this.raw.topLocations || {};
    const settings = {
      ...(this.raw.topSettings || {}),
      ...(this.raw.dirSettings || {})
    };

    const scheduleMaps = normalizeSchedules(this.raw.schedules);
    const chambersByDoctor = normalizeChambers(this.raw.chambers, scheduleMaps);
    const specMap = normalizeSpecialtyMap(rawSpecialties);
    const doctors = normalizeDoctors(rawDoctors, chambersByDoctor, scheduleMaps, specMap);

    // Count doctors per specialty & include any dynamic specialties present on active doctors
    const countsBySpecId = new Map();
    for (const doc of doctors) {
      if (!doc.specialtyId && !doc.specialtyEn) continue;
      let matched = specMap.byId.get(doc.specialtyId) || specMap.byId.get(doc.specialtyEn.toLowerCase());
      if (!matched && doc.specialtyEn) {
        matched = {
          id: slugify(doc.specialtyEn),
          nameEn: doc.specialtyEn,
          nameBn: doc.specialtyBn || doc.specialtyEn,
          iconKey: "",
          iconUrl: "",
          doctorCount: 0
        };
        specMap.list.push(matched);
        specMap.byId.set(matched.id, matched);
        specMap.byId.set(matched.nameEn.toLowerCase(), matched);
      }
      if (matched) {
        countsBySpecId.set(matched.id, (countsBySpecId.get(matched.id) || 0) + 1);
      }
    }
    const specialties = specMap.list
      .map((s) => ({ ...s, doctorCount: countsBySpecId.get(s.id) || 0 }))
      .sort((a, b) => (b.doctorCount - a.doctorCount) || a.nameEn.localeCompare(b.nameEn));

    const hospitals = normalizeHospitals(rawHospitals, this.raw.diagnostics, doctors);
    const notifications = normalizeNotifications(this.raw.topNotifications, this.raw.dirNotifications, doctors);
    const locations = buildLocationIndex(rawLocations, doctors, hospitals);

    this.state = {
      loading: false,
      error: null,
      doctors,
      hospitals,
      specialties,
      notifications,
      settings,
      locations,
      stats: {
        totalDoctors: doctors.length,
        totalHospitals: hospitals.length,
        totalSpecialties: specialties.length,
        totalDistricts: locations.districts.length
      }
    };
    this.emit();
  }

  start() {
    if (this.initialized) return;
    this.initialized = true;
    const db = getPublicDatabase();

    // Explicit public-only RTDB paths (never reads admin_auth, subAdmins, users, or private nodes)
    const bindings = [
      ["dirDoctors", "directory/doctors", true],
      ["chambers", "directory/chambers", true],
      ["schedules", "directory/chamber_schedules", true],
      ["dirHospitals", "directory/hospitals", true],
      ["diagnostics", "directory/diagnostic_centers", true],
      ["dirSpecialties", "directory/specialties", true],
      ["dirLocations", "directory/locations", false],
      ["dirSettings", "directory/settings", false],
      ["topNotifications", "notifications", true],
      ["dirNotifications", "directory/notifications", false],
      // Optional top-level public fallbacks
      ["topDoctors", "doctors", false],
      ["topHospitals", "hospitals", false],
      ["topSpecialties", "specialties", false],
      ["topLocations", "locations", false],
      ["topSettings", "settings", false]
    ];

    const requiredKeys = new Set(bindings.filter((b) => b[2]).map((b) => b[0]));
    const settledRequired = new Set();
    let rebuildTimer = null;

    const scheduleRebuild = () => {
      if (rebuildTimer) clearTimeout(rebuildTimer);
      rebuildTimer = setTimeout(() => this.rebuild(), 40);
    };

    for (const [key, path, required] of bindings) {
      try {
        const unsub = onValue(
          ref(db, path),
          (snap) => {
            this.raw[key] = snap.exists() ? snap.val() : null;
            if (required) settledRequired.add(key);
            if (settledRequired.size >= requiredKeys.size || !this.state.loading) {
              scheduleRebuild();
            }
          },
          (err) => {
            // Silently ignore permission errors on optional top-level fallback nodes
            if (!required) return;
            console.warn(`Public node read warning (${path}):`, err?.message || err);
            settledRequired.add(key);
            if (settledRequired.size >= requiredKeys.size) {
              scheduleRebuild();
            }
          }
        );
        this.unsubscribers.push(unsub);
      } catch (err) {
        if (required) {
          this.state = { ...this.state, loading: false, error: err?.message || "Connection error" };
          this.emit();
        }
      }
    }

    // Safety fallback so UI never hangs indefinitely if network is slow
    setTimeout(() => {
      if (this.state.loading) {
        this.rebuild();
      }
    }, 5000);
  }

  retry() {
    this.stop();
    this.initialized = false;
    this.state = { ...this.state, loading: true, error: null };
    this.emit();
    this.start();
  }

  stop() {
    for (const unsub of this.unsubscribers) {
      try {
        unsub();
      } catch {
        // ignore
      }
    }
    this.unsubscribers = [];
  }

  findDoctorBySlugOrId(slugOrId) {
    const key = decodeURIComponent(String(slugOrId || "")).trim().toLowerCase();
    if (!key) return null;
    return (
      this.state.doctors.find((d) => d.slug.toLowerCase() === key || d.id.toLowerCase() === key) || null
    );
  }

  findHospitalBySlugOrId(slugOrId) {
    const key = decodeURIComponent(String(slugOrId || "")).trim().toLowerCase();
    if (!key) return null;
    return (
      this.state.hospitals.find((h) => h.slug.toLowerCase() === key || h.id.toLowerCase() === key) || null
    );
  }

  searchUnified(query) {
    const q = String(query || "").trim().toLowerCase();
    if (q.length < 1) {
      return { doctors: [], specialties: [], hospitals: [], locations: [] };
    }

    const doctors = this.state.doctors
      .filter((d) => {
        const hay = [
          d.nameEn, d.nameBn, d.degrees, d.specialtyEn, d.specialtyBn,
          d.subSpecialty, d.currentHospital, ...d.districts, ...d.upazilas
        ].join(" ").toLowerCase();
        return hay.includes(q);
      })
      .slice(0, 5);

    const specialties = this.state.specialties
      .filter((s) => `${s.nameEn} ${s.nameBn}`.toLowerCase().includes(q))
      .slice(0, 4);

    const hospitals = this.state.hospitals
      .filter((h) => `${h.nameEn} ${h.nameBn} ${h.district} ${h.upazila} ${h.type}`.toLowerCase().includes(q))
      .slice(0, 4);

    const locations = [];
    for (const dist of this.state.locations.districts) {
      if (dist.toLowerCase().includes(q)) {
        locations.push({ type: "district", district: dist, upazila: "" });
      }
      for (const upz of this.state.locations.upazilasByDistrict[dist] || []) {
        if (upz.toLowerCase().includes(q)) {
          locations.push({ type: "upazila", district: dist, upazila: upz });
        }
      }
      if (locations.length >= 6) break;
    }

    return { doctors, specialties, hospitals, locations: locations.slice(0, 5) };
  }
}

export const directoryStore = new DirectoryStore();
