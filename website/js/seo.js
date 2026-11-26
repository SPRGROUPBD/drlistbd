// Dynamic SEO Metadata, Open Graph Tags, Canonical URL, and Schema.org JSON-LD Updater

function setMetaContent(selector, value) {
  const el = document.querySelector(selector);
  if (el && value !== undefined && value !== null) {
    el.setAttribute("content", String(value));
  }
}

function getOriginSafe() {
  try {
    if (window.location.origin && window.location.origin !== "null") {
      return window.location.origin;
    }
  } catch {
    // ignore
  }
  return "https://doctorlistbd.com";
}

export function applySeo({
  title,
  description,
  path = "/",
  ogType = "website",
  image = "",
  schema = null
}) {
  const cleanTitle = String(title || "Doctor List BD").trim();
  const cleanDesc = String(description || "").trim().slice(0, 165);
  const fullUrl = `${getOriginSafe()}${path.startsWith("/") ? path : "/" + path}`;

  document.title = cleanTitle;
  setMetaContent('meta[name="description"]', cleanDesc);
  setMetaContent('#ogTitle', cleanTitle);
  setMetaContent('#ogDescription', cleanDesc);
  setMetaContent('#ogType', ogType);
  setMetaContent('#ogUrl', fullUrl);
  if (image) {
    setMetaContent('#ogImage', image);
  }

  const canonical = document.getElementById("canonicalLink");
  if (canonical) {
    canonical.setAttribute("href", fullUrl);
  }

  const ldEl = document.getElementById("schemaJsonLd");
  if (ldEl) {
    const payload = schema || buildWebsiteSchema();
    ldEl.textContent = JSON.stringify(payload);
  }
}

export function buildWebsiteSchema() {
  const origin = getOriginSafe();
  return {
    "@context": "https://schema.org",
    "@graph": [
      {
        "@type": "WebSite",
        "name": "Doctor List BD",
        "url": origin,
        "potentialAction": {
          "@type": "SearchAction",
          "target": `${origin}/doctors?q={search_term_string}`,
          "query-input": "required name=search_term_string"
        }
      },
      {
        "@type": "MedicalOrganization",
        "name": "Doctor List BD",
        "url": origin,
        "areaServed": {
          "@type": "Country",
          "name": "Bangladesh"
        }
      }
    ]
  };
}

export function buildPhysicianSchema(doctor, path) {
  const origin = getOriginSafe();
  const primaryChamber = doctor.chambers[0];
  const schema = {
    "@context": "https://schema.org",
    "@type": "Physician",
    "name": doctor.nameEn || doctor.nameBn,
    "url": `${origin}${path}`,
    "medicalSpecialty": doctor.specialtyEn || doctor.specialtyBn || undefined,
    "description": doctor.bioEn || doctor.bioBn || doctor.degrees || undefined,
    "image": doctor.photoUrl || undefined,
    "telephone": doctor.phone || undefined
  };

  if (doctor.currentHospital) {
    schema.worksFor = {
      "@type": "Hospital",
      "name": doctor.currentHospital
    };
  }
  if (primaryChamber && (primaryChamber.address || primaryChamber.district)) {
    schema.address = {
      "@type": "PostalAddress",
      "streetAddress": primaryChamber.address || undefined,
      "addressLocality": primaryChamber.upazila || primaryChamber.district || undefined,
      "addressRegion": primaryChamber.district || undefined,
      "addressCountry": "BD"
    };
  }
  return schema;
}

export function buildHospitalSchema(hospital, path) {
  const origin = getOriginSafe();
  const isClinic = String(hospital.type || "").toLowerCase().includes("clinic");
  return {
    "@context": "https://schema.org",
    "@type": isClinic ? "MedicalClinic" : "Hospital",
    "name": hospital.nameEn || hospital.nameBn,
    "url": `${origin}${path}`,
    "description": hospital.descriptionEn || hospital.descriptionBn || undefined,
    "image": hospital.imageUrl || undefined,
    "telephone": hospital.phone || undefined,
    "sameAs": hospital.website || undefined,
    "address": {
      "@type": "PostalAddress",
      "streetAddress": hospital.address || undefined,
      "addressLocality": hospital.upazila || hospital.district || undefined,
      "addressRegion": hospital.district || undefined,
      "addressCountry": "BD"
    }
  };
}
