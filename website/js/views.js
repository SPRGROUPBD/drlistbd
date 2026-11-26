// Safe DOM View Renderers for Doctor List BD Public Website
// Zero untrusted innerHTML; all Firebase strings are rendered via textContent and validated URLs.

import {
  BD_DIVISIONS,
  buildGoogleMapsHref,
  buildTelHref,
  buildWhatsAppHref,
  el,
  formatDateTime,
  formatNumber,
  getDivisionLabel,
  pickLang,
  svgIcon,
  t
} from "./i18n.js";

const ICONS = {
  doctor: ["M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z", "M12 14a7 7 0 0 0-7 7h14a7 7 0 0 0-7-7Z"],
  hospital: ["M3 21h18", "M5 21V7l7-4 7 4v14", "M12 9v6", "M9 12h6"],
  specialty: ["M4.8 2.3A.3.3 0 1 0 5 2H4a2 2 0 0 0-2 2v5a6 6 0 0 0 6 6v0a6 6 0 0 0 6-6V4a2 2 0 0 0-2-2h-1a.2.2 0 1 0 .3.3", "M8 15v1a6 6 0 0 0 6 6v0a6 6 0 0 0 6-6v-4", "M20 10a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"],
  location: ["M12 21s-6-5.3-6-10a6 6 0 1 1 12 0c0 4.7-6 10-6 10Z", "M12 11a2 2 0 1 0 0-4 2 2 0 0 0 0 4Z"],
  clock: ["M12 6v6l4 2", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z"],
  phone: ["M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.2a2 2 0 0 1 2.1-.5c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z"],
  share: ["M4 12v8a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2v-8", "M16 6l-4-4-4 4", "M12 2v13"],
  map: ["M9 20l-5.4-2.7A1 1 0 0 1 3 16.4V5.6a1 1 0 0 1 1.4-.9L9 7m0 13 6-3m-6 3V7m6 10 4.6 2.3a1 1 0 0 0 1.4-.9V7.6a1 1 0 0 0-.6-.9L15 4m0 13V4m0 0L9 7"],
  verified: ["M9 12l2 2 4-4", "M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10Z"],
  globe: ["M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z", "M2 12h20", "M12 2a15.3 15.3 0 0 1 4 10 15.3 15.3 0 0 1-4 10 15.3 15.3 0 0 1-4-10 15.3 15.3 0 0 1 4-10Z"],
  fee: ["M12 2v20", "M17 5H9.5a3.5 3.5 0 0 0 0 7h5a3.5 3.5 0 0 1 0 7H6"]
};

const SPECIALTY_ICON_KEYS = {
  cardiology: ["M19 14c1.5-1.5 3-3.2 3-5.5A5.5 5.5 0 0 0 16.5 3c-1.8 0-3 .5-4.5 2-1.5-1.5-2.7-2-4.5-2A5.5 5.5 0 0 0 2 8.5c0 2.3 1.5 4 3 5.5l7 7Z"],
  neurology: ["M12 5a3 3 0 1 0-5.997.125 4 4 0 0 0-2.526 5.77 4 4 0 0 0 .556 6.588A4 4 0 1 0 12 18Z", "M12 5a3 3 0 1 1 5.997.125 4 4 0 0 1 2.526 5.77 4 4 0 0 1-.556 6.588A4 4 0 1 1 12 18Z"],
  ophthalmology: ["M2 12s3-7 10-7 10 7 10 7-3 7-10 7-10-7-10-7Z", "M12 15a3 3 0 1 0 0-6 3 3 0 0 0 0 6Z"],
  pediatrics: ["M9 12h.01", "M15 12h.01", "M10 16c.5.3 1.2.5 2 .5s1.5-.2 2-.5", "M12 22a10 10 0 1 0 0-20 10 10 0 0 0 0 20Z"],
  orthopedics: ["M17 10c.7-.7 1.7-1 2.5-1a2.5 2.5 0 0 1 0 5c-.7 0-1.5.5-2 1l-4 4c-.5.5-1 1.3-1 2a2.5 2.5 0 0 1-5 0c0-.8.3-1.8 1-2.5l4-4c.7-.7 1-1.7 1-2.5a2.5 2.5 0 0 1 5 0c0 .7-.5 1.5-1 2"],
  medicine: ICONS.specialty
};

function getSpecialtySvg(spec) {
  const key = `${spec.iconKey || ""} ${spec.nameEn || ""}`.toLowerCase();
  if (key.includes("cardio") || key.includes("heart")) return svgIcon(SPECIALTY_ICON_KEYS.cardiology, 24);
  if (key.includes("neuro") || key.includes("brain") || key.includes("psych")) return svgIcon(SPECIALTY_ICON_KEYS.neurology, 24);
  if (key.includes("eye") || key.includes("ophthalm")) return svgIcon(SPECIALTY_ICON_KEYS.ophthalmology, 24);
  if (key.includes("pediatr") || key.includes("child")) return svgIcon(SPECIALTY_ICON_KEYS.pediatrics, 24);
  if (key.includes("ortho") || key.includes("bone")) return svgIcon(SPECIALTY_ICON_KEYS.orthopedics, 24);
  return svgIcon(ICONS.specialty, 24);
}

function createAvatar(photoUrl, name, large = false) {
  const initial = String(name || "D").replace(/^(dr\.?|prof\.?|ডাঃ|ডা\.|অধ্যাপক)\s*/i, "").trim().charAt(0).toUpperCase() || "D";
  const fallbackClass = large ? "profile-photo-lg-fallback" : "doc-avatar-fallback";
  const imgClass = large ? "profile-photo-lg" : "doc-avatar";

  if (!photoUrl) {
    return el("div", { className: fallbackClass, "aria-hidden": "true", text: initial });
  }
  const wrap = el("div", { style: "flex-shrink:0;" });
  const img = el("img", {
    src: photoUrl,
    alt: name,
    className: imgClass,
    loading: "lazy",
    decoding: "async"
  });
  img.addEventListener("error", () => {
    wrap.replaceChildren(el("div", { className: fallbackClass, "aria-hidden": "true", text: initial }));
  });
  wrap.appendChild(img);
  return wrap;
}

function createHospitalBanner(imageUrl, name) {
  if (!imageUrl) {
    return el("div", { className: "hosp-banner-fallback", "aria-hidden": "true" }, [svgIcon(ICONS.hospital, 44)]);
  }
  const wrap = el("div");
  const img = el("img", {
    src: imageUrl,
    alt: name,
    className: "hosp-banner",
    loading: "lazy",
    decoding: "async"
  });
  img.addEventListener("error", () => {
    wrap.replaceChildren(el("div", { className: "hosp-banner-fallback", "aria-hidden": "true" }, [svgIcon(ICONS.hospital, 44)]));
  });
  wrap.appendChild(img);
  return wrap;
}

function createBreadcrumb(items, navigate) {
  const nav = el("nav", { className: "breadcrumb", "aria-label": "Breadcrumb" });
  items.forEach((item, idx) => {
    if (idx > 0) nav.appendChild(el("span", { text: "/" }));
    if (item.href) {
      const a = el("a", { href: item.href, text: item.label });
      a.addEventListener("click", (e) => {
        e.preventDefault();
        navigate(item.href);
      });
      nav.appendChild(a);
    } else {
      nav.appendChild(el("span", { text: item.label }));
    }
  });
  return nav;
}

function createStateCard(title, desc, actionLabel, onAction) {
  const children = [
    el("h3", { text: title }),
    el("p", { text: desc, style: "max-width:480px;font-size:14px;" })
  ];
  if (actionLabel && typeof onAction === "function") {
    children.push(el("button", { type: "button", className: "btn btn-primary", text: actionLabel, onClick: onAction }));
  }
  return el("div", { className: "state-card" }, children);
}

export function renderLoadingView(lang) {
  return el("div", { className: "container loading-screen" }, [
    el("div", { className: "spinner-lg" }),
    el("p", { text: t(lang, "loading_directory") })
  ]);
}

export function renderErrorView(lang, onRetry) {
  return el("div", { className: "container section" }, [
    createStateCard(t(lang, "error_title"), t(lang, "error_desc"), t(lang, "btn_retry"), onRetry)
  ]);
}

function createDoctorCard(doc, ctx, showVisitingHours = false) {
  const { lang, navigate } = ctx;
  const name = pickLang(lang, doc.nameBn, doc.nameEn);
  const spec = pickLang(lang, doc.specialtyBn, doc.specialtyEn);
  const profileUrl = `/doctor/${encodeURIComponent(doc.slug)}`;

  const metaRows = [];
  if (doc.currentHospital) {
    metaRows.push(el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.hospital, 16), el("span", { text: doc.currentHospital })]));
  }
  if (doc.primaryDistrict) {
    const locStr = [doc.primaryUpazila, doc.primaryDistrict].filter(Boolean).join(", ");
    metaRows.push(el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.location, 16), el("span", { text: locStr })]));
  }
  if (showVisitingHours && doc.visitingHoursSummary) {
    metaRows.push(el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.clock, 16), el("span", { text: doc.visitingHoursSummary })]));
  }

  const profileBtn = el("a", {
    href: profileUrl,
    className: "btn btn-primary btn-sm",
    text: showVisitingHours ? t(lang, "btn_view_details") : t(lang, "btn_view_profile")
  });
  profileBtn.addEventListener("click", (e) => {
    e.preventDefault();
    navigate(profileUrl);
  });

  const pills = [];
  if (spec) pills.push(el("span", { className: "pill pill-blue", text: spec }));
  if (doc.verifiedBmdcNumber) pills.push(el("span", { className: "pill pill-green", text: t(lang, "lbl_bmdc_verified") }));

  return el("article", { className: "card" }, [
    el("div", { className: "doc-card-body" }, [
      el("div", { className: "doc-card-top" }, [
        createAvatar(doc.photoUrl, name, false),
        el("div", { style: "min-width:0;flex:1;" }, [
          el("h3", { className: "doc-name", text: name }),
          doc.degrees ? el("p", { className: "doc-degree", text: doc.degrees }) : null,
          pills.length ? el("div", { style: "display:flex;flex-wrap:wrap;gap:6px;margin-top:8px;" }, pills) : null
        ])
      ]),
      metaRows.length ? el("div", { className: "doc-meta-list" }, metaRows) : null
    ]),
    el("div", { className: "doc-card-foot" }, [
      el("span", {
        style: "font-size:12px;color:#64748b;font-weight:600;",
        text: doc.chambers.length > 1
          ? `${formatNumber(doc.chambers.length, lang)} ${t(lang, "lbl_chamber_count")}`
          : (doc.primaryDistrict || t(lang, "country_bangladesh"))
      }),
      profileBtn
    ])
  ]);
}

function createHospitalCard(hosp, ctx) {
  const { lang, navigate } = ctx;
  const name = pickLang(lang, hosp.nameBn, hosp.nameEn);
  const detailsUrl = `/hospital/${encodeURIComponent(hosp.slug)}`;

  const detailsBtn = el("a", {
    href: detailsUrl,
    className: "btn btn-green btn-sm",
    text: t(lang, "btn_view_details")
  });
  detailsBtn.addEventListener("click", (e) => {
    e.preventDefault();
    navigate(detailsUrl);
  });

  const metaRows = [];
  if (hosp.address || hosp.district) {
    const addr = [hosp.address, hosp.upazila, hosp.district].filter(Boolean).join(", ");
    metaRows.push(el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.location, 16), el("span", { text: addr })]));
  }
  if (hosp.phone) {
    metaRows.push(el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.phone, 16), el("span", { text: hosp.phone })]));
  }

  return el("article", { className: "card" }, [
    createHospitalBanner(hosp.imageUrl, name),
    el("div", { className: "hosp-body" }, [
      el("div", { style: "display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;" }, [
        el("span", { className: "pill pill-green", text: hosp.type || "Hospital" }),
        hosp.district ? el("span", { className: "pill pill-blue", text: hosp.district }) : null
      ]),
      el("h3", { className: "doc-name", text: name }),
      metaRows.length ? el("div", { className: "doc-meta-list", style: "margin-top:4px;" }, metaRows) : null
    ]),
    el("div", { className: "doc-card-foot" }, [
      el("span", {
        style: "font-size:12px;color:#64748b;font-weight:600;",
        text: `${formatNumber(hosp.associatedDoctors.length, lang)} ${t(lang, "doctors_unit")}`
      }),
      detailsBtn
    ])
  ]);
}

function createSpecialtyCard(spec, ctx) {
  const { lang, navigate } = ctx;
  const label = pickLang(lang, spec.nameBn, spec.nameEn);
  const url = `/doctors?specialty=${encodeURIComponent(spec.id)}`;

  const iconBox = el("div", { className: "spec-icon-circle" });
  if (spec.iconUrl) {
    const img = el("img", { src: spec.iconUrl, alt: label, loading: "lazy" });
    img.addEventListener("error", () => iconBox.replaceChildren(getSpecialtySvg(spec)));
    iconBox.appendChild(img);
  } else {
    iconBox.appendChild(getSpecialtySvg(spec));
  }

  const card = el("a", { href: url, className: "spec-card" }, [
    iconBox,
    el("span", { className: "spec-name", text: label }),
    el("span", { className: "spec-count", text: `${formatNumber(spec.doctorCount, lang)} ${t(lang, "doctors_unit")}` })
  ]);
  card.addEventListener("click", (e) => {
    e.preventDefault();
    navigate(url);
  });
  return card;
}

function createPagination(currentPage, totalPages, lang, onPageChange) {
  if (totalPages <= 1) return null;
  const wrap = el("nav", { className: "pagination", "aria-label": "Pagination" });

  const prev = el("button", {
    type: "button",
    className: "page-btn",
    disabled: currentPage <= 1,
    text: t(lang, "prev_page"),
    onClick: () => onPageChange(currentPage - 1)
  });
  wrap.appendChild(prev);

  const start = Math.max(1, currentPage - 2);
  const end = Math.min(totalPages, currentPage + 2);
  for (let p = start; p <= end; p++) {
    wrap.appendChild(
      el("button", {
        type: "button",
        className: p === currentPage ? "page-btn active" : "page-btn",
        text: formatNumber(p, lang),
        onClick: () => onPageChange(p)
      })
    );
  }

  const next = el("button", {
    type: "button",
    className: "page-btn",
    disabled: currentPage >= totalPages,
    text: t(lang, "next_page"),
    onClick: () => onPageChange(currentPage + 1)
  });
  wrap.appendChild(next);
  return wrap;
}

// ============================================================================
// 1. HOMEPAGE VIEW (/)
// ============================================================================
export function renderHomeView(state, ctx) {
  const { lang, navigate } = ctx;
  const frag = document.createDocumentFragment();

  // --- HERO SECTION ---
  let activeMode = "doctor";
  const heroInput = el("input", {
    type: "search",
    className: "input",
    placeholder: t(lang, "hero_input_doctor"),
    "aria-label": t(lang, "hero_input_doctor")
  });

  const specSelect = el("select", { className: "select", "aria-label": t(lang, "all_specialties") }, [
    el("option", { value: "", text: t(lang, "all_specialties") }),
    ...state.specialties.map((s) => el("option", { value: s.id, text: pickLang(lang, s.nameBn, s.nameEn) }))
  ]);

  const distSelect = el("select", { className: "select", "aria-label": t(lang, "all_districts") }, [
    el("option", { value: "", text: t(lang, "all_districts") }),
    ...state.locations.districts.map((d) => el("option", { value: d, text: d }))
  ]);

  const upzSelect = el("select", { className: "select", "aria-label": t(lang, "all_upazilas") }, [
    el("option", { value: "", text: t(lang, "all_upazilas") })
  ]);

  distSelect.addEventListener("change", () => {
    const upzs = state.locations.upazilasByDistrict[distSelect.value] || [];
    upzSelect.replaceChildren(
      el("option", { value: "", text: t(lang, "all_upazilas") }),
      ...upzs.map((u) => el("option", { value: u, text: u }))
    );
  });

  const tabSpecs = [
    ["doctor", "hero_tab_doctor", "hero_input_doctor"],
    ["hospital", "hero_tab_hospital", "hero_input_hospital"],
    ["specialty", "hero_tab_specialty", "hero_input_specialty"],
    ["location", "hero_tab_location", "hero_input_location"]
  ];

  const tabButtons = [];
  const tabsRow = el("div", { className: "hero-tabs" });
  tabSpecs.forEach(([mode, labelKey, phKey]) => {
    const btn = el("button", {
      type: "button",
      className: mode === activeMode ? "hero-tab active" : "hero-tab",
      text: t(lang, labelKey),
      onClick: () => {
        activeMode = mode;
        tabButtons.forEach((b) => b.classList.toggle("active", b.dataset.mode === mode));
        heroInput.placeholder = t(lang, phKey);
        heroInput.setAttribute("aria-label", t(lang, phKey));
      }
    });
    btn.dataset.mode = mode;
    tabButtons.push(btn);
    tabsRow.appendChild(btn);
  });

  const handleHeroSubmit = (e) => {
    e.preventDefault();
    const q = heroInput.value.trim();
    const params = new URLSearchParams();
    if (q) params.set("q", q);
    if (distSelect.value) params.set("district", distSelect.value);
    if (upzSelect.value) params.set("upazila", upzSelect.value);

    if (activeMode === "hospital") {
      navigate(params.toString() ? `/hospitals?${params.toString()}` : "/hospitals");
      return;
    }
    if (activeMode === "specialty") {
      if (specSelect.value) {
        params.set("specialty", specSelect.value);
        navigate(`/doctors?${params.toString()}`);
      } else {
        navigate(q ? `/specialties?q=${encodeURIComponent(q)}` : "/specialties");
      }
      return;
    }
    if (specSelect.value) params.set("specialty", specSelect.value);
    navigate(params.toString() ? `/doctors?${params.toString()}` : "/doctors");
  };

  const heroForm = el("form", { className: "hero-form-grid", onSubmit: handleHeroSubmit }, [
    heroInput,
    specSelect,
    distSelect,
    upzSelect,
    el("button", { type: "submit", className: "btn btn-primary", text: t(lang, "btn_search") })
  ]);

  frag.appendChild(
    el("section", { className: "hero" }, [
      el("div", { className: "container hero-inner" }, [
        el("div", { className: "hero-badge", text: t(lang, "hero_badge") }),
        el("h1", { text: t(lang, "hero_heading") }),
        el("p", { className: "hero-sub", text: t(lang, "hero_subtitle") }),
        el("div", { className: "hero-search-card" }, [tabsRow, heroForm])
      ])
    ])
  );

  // --- DYNAMIC STATISTICS ---
  const statItems = [
    { icon: ICONS.doctor, tone: "blue", val: state.stats.totalDoctors, lbl: t(lang, "stat_doctors") },
    { icon: ICONS.hospital, tone: "green", val: state.stats.totalHospitals, lbl: t(lang, "stat_hospitals") },
    { icon: ICONS.specialty, tone: "purple", val: state.stats.totalSpecialties, lbl: t(lang, "stat_specialties") },
    { icon: ICONS.location, tone: "amber", val: state.stats.totalDistricts, lbl: t(lang, "stat_districts") }
  ];

  frag.appendChild(
    el("section", { className: "stats-strip" }, [
      el("div", { className: "container" }, [
        el(
          "div",
          { className: "stats-grid" },
          statItems.map((st) =>
            el("div", { className: "stat-box" }, [
              el("div", { className: `stat-icon ${st.tone}` }, [svgIcon(st.icon, 22)]),
              el("div", {}, [
                el("div", { className: "stat-num", text: formatNumber(st.val, lang) }),
                el("div", { className: "stat-lbl", text: st.lbl })
              ])
            ])
          )
        )
      ])
    ])
  );

  // --- FEATURED DOCTORS ---
  const featuredDocs = [
    ...state.doctors.filter((d) => d.isFeatured),
    ...state.doctors.filter((d) => !d.isFeatured)
  ].slice(0, 6);

  const viewAllDocsLink = el("a", { href: "/doctors", className: "btn btn-outline btn-sm", text: t(lang, "btn_view_all") });
  viewAllDocsLink.addEventListener("click", (e) => {
    e.preventDefault();
    navigate("/doctors");
  });

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("div", { className: "section-head" }, [
          el("div", {}, [
            el("h2", { className: "section-title", text: t(lang, "sec_featured_doctors") }),
            el("p", { className: "section-sub", text: t(lang, "sec_featured_doctors_sub") })
          ]),
          viewAllDocsLink
        ]),
        featuredDocs.length
          ? el("div", { className: "card-grid" }, featuredDocs.map((d) => createDoctorCard(d, ctx, false)))
          : createStateCard(t(lang, "empty_doctors_title"), t(lang, "empty_doctors_desc"))
      ])
    ])
  );

  // --- POPULAR SPECIALTIES ---
  const viewAllSpecsLink = el("a", { href: "/specialties", className: "btn btn-outline btn-sm", text: t(lang, "btn_view_all") });
  viewAllSpecsLink.addEventListener("click", (e) => {
    e.preventDefault();
    navigate("/specialties");
  });

  frag.appendChild(
    el("section", { className: "section section-alt" }, [
      el("div", { className: "container" }, [
        el("div", { className: "section-head" }, [
          el("div", {}, [
            el("h2", { className: "section-title", text: t(lang, "sec_popular_specialties") }),
            el("p", { className: "section-sub", text: t(lang, "sec_popular_specialties_sub") })
          ]),
          viewAllSpecsLink
        ]),
        state.specialties.length
          ? el("div", { className: "spec-grid" }, state.specialties.slice(0, 12).map((s) => createSpecialtyCard(s, ctx)))
          : createStateCard(t(lang, "empty_specialties_title"), t(lang, "empty_specialties_desc"))
      ])
    ])
  );

  // --- FEATURED HOSPITALS ---
  const featuredHospitals = [
    ...state.hospitals.filter((h) => h.isFeatured),
    ...state.hospitals.filter((h) => !h.isFeatured)
  ].slice(0, 6);

  const viewAllHospLink = el("a", { href: "/hospitals", className: "btn btn-outline btn-sm", text: t(lang, "btn_view_all") });
  viewAllHospLink.addEventListener("click", (e) => {
    e.preventDefault();
    navigate("/hospitals");
  });

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("div", { className: "section-head" }, [
          el("div", {}, [
            el("h2", { className: "section-title", text: t(lang, "sec_featured_hospitals") }),
            el("p", { className: "section-sub", text: t(lang, "sec_featured_hospitals_sub") })
          ]),
          viewAllHospLink
        ]),
        featuredHospitals.length
          ? el("div", { className: "card-grid" }, featuredHospitals.map((h) => createHospitalCard(h, ctx)))
          : createStateCard(t(lang, "empty_hospitals_title"), t(lang, "empty_hospitals_desc"))
      ])
    ])
  );

  // --- LATEST DOCTORS ---
  const latestDocs = [...state.doctors]
    .sort((a, b) => b.createdAt - a.createdAt)
    .slice(0, 6);

  if (latestDocs.length) {
    frag.appendChild(
      el("section", { className: "section section-alt" }, [
        el("div", { className: "container" }, [
          el("div", { className: "section-head" }, [
            el("div", {}, [
              el("h2", { className: "section-title", text: t(lang, "sec_latest_doctors") }),
              el("p", { className: "section-sub", text: t(lang, "sec_latest_doctors_sub") })
            ])
          ]),
          el("div", { className: "card-grid" }, latestDocs.map((d) => createDoctorCard(d, ctx, true)))
        ])
      ])
    );
  }

  // --- BANGLADESH LOCATION HIERARCHY EXPLORER ---
  frag.appendChild(createLocationExplorerSection(state, ctx));

  return frag;
}

function createLocationExplorerSection(state, ctx) {
  const { lang, navigate } = ctx;
  let selectedDivision = "";
  let selectedDistrict = "";

  const divChipsWrap = el("div", { className: "division-chips" });
  const distWrap = el("div", { className: "division-chips" });
  const upzWrap = el("div", { className: "division-chips" });
  const actionsWrap = el("div", { style: "display:flex;gap:10px;flex-wrap:wrap;margin-top:12px;" });

  const renderExplorer = () => {
    divChipsWrap.replaceChildren(
      el("button", {
        type: "button",
        className: selectedDivision === "" ? "chip active" : "chip",
        text: `${t(lang, "country_bangladesh")} — ${t(lang, "all_divisions")}`,
        onClick: () => {
          selectedDivision = "";
          selectedDistrict = "";
          renderExplorer();
        }
      }),
      ...BD_DIVISIONS.map((div) =>
        el("button", {
          type: "button",
          className: selectedDivision === div.en ? "chip active" : "chip",
          text: lang === "bn" ? div.bn : div.en,
          onClick: () => {
            selectedDivision = div.en;
            selectedDistrict = "";
            renderExplorer();
          }
        })
      )
    );

    const availableDistricts = selectedDivision
      ? state.locations.districtsByDivision[selectedDivision] || []
      : state.locations.districts.slice(0, 16);

    distWrap.replaceChildren(
      ...availableDistricts.map((dist) =>
        el("button", {
          type: "button",
          className: selectedDistrict === dist ? "chip active" : "chip",
          text: dist,
          onClick: () => {
            selectedDistrict = selectedDistrict === dist ? "" : dist;
            renderExplorer();
          }
        })
      )
    );

    const upazilas = selectedDistrict ? state.locations.upazilasByDistrict[selectedDistrict] || [] : [];
    upzWrap.replaceChildren(
      ...upazilas.map((upz) =>
        el("button", {
          type: "button",
          className: "chip",
          text: upz,
          onClick: () => {
            const p = new URLSearchParams();
            if (selectedDivision) p.set("division", selectedDivision);
            if (selectedDistrict) p.set("district", selectedDistrict);
            p.set("upazila", upz);
            navigate(`/doctors?${p.toString()}`);
          }
        })
      )
    );

    const buildQuery = () => {
      const p = new URLSearchParams();
      if (selectedDivision) p.set("division", selectedDivision);
      if (selectedDistrict) p.set("district", selectedDistrict);
      return p.toString();
    };

    const docBtn = el("button", {
      type: "button",
      className: "btn btn-primary btn-sm",
      text: t(lang, "nav_doctors"),
      onClick: () => {
        const qs = buildQuery();
        navigate(qs ? `/doctors?${qs}` : "/doctors");
      }
    });
    const hospBtn = el("button", {
      type: "button",
      className: "btn btn-green btn-sm",
      text: t(lang, "nav_hospitals"),
      onClick: () => {
        const qs = buildQuery();
        navigate(qs ? `/hospitals?${qs}` : "/hospitals");
      }
    });
    actionsWrap.replaceChildren(docBtn, hospBtn);
  };

  renderExplorer();

  return el("section", { className: "section" }, [
    el("div", { className: "container" }, [
      el("div", { className: "location-box" }, [
        el("h2", { className: "section-title", text: t(lang, "sec_location_explorer") }),
        el("p", { className: "section-sub", text: t(lang, "sec_location_explorer_sub") }),
        divChipsWrap,
        distWrap,
        upzWrap,
        actionsWrap
      ])
    ])
  ]);
}

// ============================================================================
// 2. DOCTOR DIRECTORY VIEW (/doctors)
// ============================================================================
export function renderDoctorsView(state, ctx, queryParams) {
  const { lang, navigate } = ctx;
  const pageSize = 12;

  const filters = {
    q: queryParams.get("q") || "",
    specialty: queryParams.get("specialty") || "",
    division: queryParams.get("division") || "",
    district: queryParams.get("district") || "",
    upazila: queryParams.get("upazila") || "",
    hospital: queryParams.get("hospital") || "",
    sort: queryParams.get("sort") || "recent",
    page: Math.max(1, Number(queryParams.get("page") || 1) || 1)
  };

  const applyFilterNavigation = (patch, resetPage = true) => {
    const next = { ...filters, ...patch };
    if (resetPage) next.page = 1;
    const p = new URLSearchParams();
    if (next.q.trim()) p.set("q", next.q.trim());
    if (next.specialty) p.set("specialty", next.specialty);
    if (next.division) p.set("division", next.division);
    if (next.district) p.set("district", next.district);
    if (next.upazila) p.set("upazila", next.upazila);
    if (next.hospital) p.set("hospital", next.hospital);
    if (next.sort && next.sort !== "recent") p.set("sort", next.sort);
    if (next.page > 1) p.set("page", String(next.page));
    navigate(p.toString() ? `/doctors?${p.toString()}` : "/doctors");
  };

  // Filter active doctors
  const qLower = filters.q.trim().toLowerCase();
  let list = state.doctors.filter((doc) => {
    if (qLower) {
      const hay = [
        doc.nameEn, doc.nameBn, doc.degrees, doc.specialtyEn, doc.specialtyBn,
        doc.subSpecialty, doc.currentHospital, ...doc.hospitals, ...doc.districts, ...doc.upazilas
      ].join(" ").toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    if (filters.specialty) {
      const spTarget = filters.specialty.toLowerCase();
      const matchSpec =
        doc.specialtyId.toLowerCase() === spTarget ||
        doc.specialtyEn.toLowerCase() === spTarget ||
        doc.specialtyBn.toLowerCase() === spTarget;
      if (!matchSpec) return false;
    }
    if (filters.division) {
      if (!doc.divisions.some((d) => d.toLowerCase() === filters.division.toLowerCase())) return false;
    }
    if (filters.district) {
      if (!doc.districts.some((d) => d.toLowerCase() === filters.district.toLowerCase())) return false;
    }
    if (filters.upazila) {
      if (!doc.upazilas.some((u) => u.toLowerCase() === filters.upazila.toLowerCase())) return false;
    }
    if (filters.hospital) {
      const hTarget = filters.hospital.toLowerCase();
      if (!doc.hospitals.some((h) => h.toLowerCase().includes(hTarget))) return false;
    }
    return true;
  });

  if (filters.sort === "name_asc") {
    list = [...list].sort((a, b) => pickLang(lang, a.nameBn, a.nameEn).localeCompare(pickLang(lang, b.nameBn, b.nameEn)));
  } else if (filters.sort === "name_desc") {
    list = [...list].sort((a, b) => pickLang(lang, b.nameBn, b.nameEn).localeCompare(pickLang(lang, a.nameBn, a.nameEn)));
  } else {
    list = [...list].sort((a, b) => b.createdAt - a.createdAt);
  }

  const totalPages = Math.max(1, Math.ceil(list.length / pageSize));
  const safePage = Math.min(filters.page, totalPages);
  const pageItems = list.slice((safePage - 1) * pageSize, safePage * pageSize);

  // Filter controls
  const qInput = el("input", {
    type: "search",
    className: "input",
    value: filters.q,
    placeholder: t(lang, "search_doctor_placeholder"),
    "aria-label": t(lang, "search_doctor_placeholder")
  });
  qInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      applyFilterNavigation({ q: qInput.value });
    }
  });

  const specSelect = el("select", { className: "select", "aria-label": t(lang, "all_specialties") }, [
    el("option", { value: "", text: t(lang, "all_specialties") }),
    ...state.specialties.map((s) =>
      el("option", { value: s.id, text: `${pickLang(lang, s.nameBn, s.nameEn)} (${formatNumber(s.doctorCount, lang)})`, selected: filters.specialty === s.id })
    )
  ]);
  specSelect.addEventListener("change", () => applyFilterNavigation({ specialty: specSelect.value }));

  const divSelect = el("select", { className: "select", "aria-label": t(lang, "all_divisions") }, [
    el("option", { value: "", text: t(lang, "all_divisions") }),
    ...BD_DIVISIONS.map((d) =>
      el("option", { value: d.en, text: lang === "bn" ? d.bn : d.en, selected: filters.division.toLowerCase() === d.en.toLowerCase() })
    )
  ]);
  divSelect.addEventListener("change", () => applyFilterNavigation({ division: divSelect.value, district: "", upazila: "" }));

  const distOptions = filters.division
    ? state.locations.districtsByDivision[filters.division] || []
    : state.locations.districts;

  const distSelect = el("select", { className: "select", "aria-label": t(lang, "all_districts") }, [
    el("option", { value: "", text: t(lang, "all_districts") }),
    ...distOptions.map((d) => el("option", { value: d, text: d, selected: filters.district === d }))
  ]);
  distSelect.addEventListener("change", () => applyFilterNavigation({ district: distSelect.value, upazila: "" }));

  const upzOptions = filters.district ? state.locations.upazilasByDistrict[filters.district] || [] : [];
  const upzSelect = el("select", { className: "select", "aria-label": t(lang, "all_upazilas") }, [
    el("option", { value: "", text: t(lang, "all_upazilas") }),
    ...upzOptions.map((u) => el("option", { value: u, text: u, selected: filters.upazila === u }))
  ]);
  upzSelect.addEventListener("change", () => applyFilterNavigation({ upazila: upzSelect.value }));

  const hospitalNames = Array.from(
    new Set([...state.hospitals.map((h) => h.nameEn), ...state.doctors.flatMap((d) => d.hospitals)].filter(Boolean))
  ).sort();

  const hospSelect = el("select", { className: "select", "aria-label": t(lang, "all_hospitals") }, [
    el("option", { value: "", text: t(lang, "all_hospitals") }),
    ...hospitalNames.map((h) => el("option", { value: h, text: h, selected: filters.hospital === h }))
  ]);
  hospSelect.addEventListener("change", () => applyFilterNavigation({ hospital: hospSelect.value }));

  const sortSelect = el("select", { className: "select", style: "width:auto;min-width:170px;", "aria-label": "Sort" }, [
    el("option", { value: "recent", text: t(lang, "sort_recent"), selected: filters.sort === "recent" }),
    el("option", { value: "name_asc", text: t(lang, "sort_name_asc"), selected: filters.sort === "name_asc" }),
    el("option", { value: "name_desc", text: t(lang, "sort_name_desc"), selected: filters.sort === "name_desc" })
  ]);
  sortSelect.addEventListener("change", () => applyFilterNavigation({ sort: sortSelect.value }));

  const filterForm = el("div", { className: "filter-panel" }, [
    el("div", { className: "filter-grid" }, [
      qInput,
      specSelect,
      divSelect,
      distSelect,
      upzSelect,
      el("button", {
        type: "button",
        className: "btn btn-primary",
        text: t(lang, "btn_search"),
        onClick: () => applyFilterNavigation({ q: qInput.value })
      })
    ]),
    el("div", { style: "display:flex;gap:10px;flex-wrap:wrap;margin-top:10px;align-items:center;" }, [
      el("div", { style: "flex:1;min-width:200px;max-width:340px;" }, [hospSelect]),
      el("button", {
        type: "button",
        className: "btn btn-outline btn-sm",
        text: t(lang, "btn_reset"),
        onClick: () => navigate("/doctors")
      })
    ])
  ]);

  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_doctors") }], navigate),
        el("h1", { text: t(lang, "doctors_page_title") }),
        el("p", { text: t(lang, "doctors_page_sub") })
      ])
    ])
  );

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        filterForm,
        el("div", { className: "results-bar" }, [
          el("span", { text: `${t(lang, "showing_results")}: ${formatNumber(list.length, lang)} ${t(lang, "doctors_unit")}` }),
          sortSelect
        ]),
        pageItems.length
          ? el("div", { className: "card-grid" }, pageItems.map((d) => createDoctorCard(d, ctx, true)))
          : createStateCard(t(lang, "empty_doctors_title"), t(lang, "empty_doctors_desc"), t(lang, "btn_reset"), () => navigate("/doctors")),
        createPagination(safePage, totalPages, lang, (p) => applyFilterNavigation({ page: p }, false))
      ])
    ])
  );

  return frag;
}

// ============================================================================
// 3. DOCTOR PROFILE VIEW (/doctor/{slug})
// ============================================================================
export function renderDoctorProfileView(doctor, ctx) {
  const { lang, navigate, shareProfile } = ctx;
  const frag = document.createDocumentFragment();

  if (!doctor) {
    frag.appendChild(
      el("div", { className: "container section" }, [
        createStateCard(t(lang, "not_found_title"), t(lang, "not_found_doctor"), t(lang, "nav_doctors"), () => navigate("/doctors"))
      ])
    );
    return frag;
  }

  const name = pickLang(lang, doctor.nameBn, doctor.nameEn);
  const spec = pickLang(lang, doctor.specialtyBn, doctor.specialtyEn);
  const bio = pickLang(lang, doctor.bioBn, doctor.bioEn);
  const primaryChamber = doctor.chambers[0] || null;
  const telHref = buildTelHref(doctor.phone || primaryChamber?.appointmentPhone);
  const waHref = buildWhatsAppHref(
    doctor.whatsapp || doctor.phone || primaryChamber?.appointmentPhone,
    `Hello, I would like to inquire about an appointment with ${doctor.nameEn || doctor.nameBn}.`
  );
  const mapHref = buildGoogleMapsHref(
    primaryChamber?.googleMapsUrl,
    [primaryChamber?.facilityName || doctor.currentHospital, primaryChamber?.address, primaryChamber?.district, "Bangladesh"]
      .filter(Boolean)
      .join(", ")
  );

  const actionBtns = [];
  if (telHref) {
    actionBtns.push(
      el("a", { href: telHref, className: "btn btn-primary" }, [svgIcon(ICONS.phone, 17), el("span", { text: t(lang, "btn_call_now") })])
    );
  }
  if (waHref) {
    actionBtns.push(
      el("a", { href: waHref, target: "_blank", rel: "noopener noreferrer", className: "btn btn-whatsapp" }, [
        el("span", { text: t(lang, "btn_whatsapp") })
      ])
    );
  }
  if (mapHref) {
    actionBtns.push(
      el("a", { href: mapHref, target: "_blank", rel: "noopener noreferrer", className: "btn btn-outline" }, [
        svgIcon(ICONS.map, 17),
        el("span", { text: t(lang, "btn_directions") })
      ])
    );
  }
  actionBtns.push(
    el("button", {
      type: "button",
      className: "btn btn-outline",
      onClick: () => shareProfile(name, `${name} — ${spec || "Doctor List BD"}`)
    }, [svgIcon(ICONS.share, 17), el("span", { text: t(lang, "btn_share") })])
  );

  const badgeRow = [];
  if (spec) badgeRow.push(el("span", { className: "pill pill-blue", text: spec }));
  if (doctor.subSpecialty) badgeRow.push(el("span", { className: "pill pill-purple", text: doctor.subSpecialty }));
  if (doctor.verifiedBmdcNumber) {
    badgeRow.push(
      el("span", { className: "pill pill-green" }, [
        svgIcon(ICONS.verified, 14),
        el("span", { text: `${t(lang, "lbl_bmdc_verified")}: ${doctor.verifiedBmdcNumber}` })
      ])
    );
  }
  if (doctor.experience) {
    const expStr = /^\d+$/.test(doctor.experience)
      ? `${formatNumber(doctor.experience, lang)} ${t(lang, "lbl_years")}`
      : doctor.experience;
    badgeRow.push(el("span", { className: "pill pill-amber", text: `${t(lang, "lbl_experience")}: ${expStr}` }));
  }

  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb(
          [
            { label: t(lang, "nav_home"), href: "/" },
            { label: t(lang, "nav_doctors"), href: "/doctors" },
            { label: name }
          ],
          navigate
        ),
        el("h1", { text: name }),
        doctor.degrees ? el("p", { text: doctor.degrees }) : null
      ])
    ])
  );

  // Hero card
  const heroCard = el("div", { className: "profile-hero-card" }, [
    createAvatar(doctor.photoUrl, name, true),
    el("div", {}, [
      el("div", { style: "display:flex;flex-wrap:wrap;gap:8px;margin-bottom:8px;" }, badgeRow),
      el("h2", { style: "font-size:23px;font-weight:800;color:#0a2e6e;", text: name }),
      doctor.degrees ? el("p", { style: "font-size:14px;color:#334155;font-weight:600;margin-top:4px;", text: doctor.degrees }) : null,
      doctor.designation ? el("p", { style: "font-size:13.5px;color:#475569;margin-top:4px;", text: doctor.designation }) : null,
      doctor.currentHospital
        ? el("p", { style: "font-size:13.5px;color:#047857;font-weight:700;margin-top:4px;", text: doctor.currentHospital })
        : null,
      el("div", { className: "profile-actions" }, actionBtns)
    ])
  ]);

  // Chambers list (supports multiple chambers per doctor)
  const chamberCards = doctor.chambers.map((ch, idx) => {
    const chTel = buildTelHref(ch.appointmentPhone || doctor.phone);
    const chWa = buildWhatsAppHref(ch.appointmentPhone || doctor.phone);
    const chMap = buildGoogleMapsHref(
      ch.googleMapsUrl,
      [ch.facilityName, ch.address, ch.upazila, ch.district, "Bangladesh"].filter(Boolean).join(", ")
    );

    const scheduleSection = [];
    if (ch.schedules && ch.schedules.length) {
      scheduleSection.push(
        el("div", {}, [
          el("div", { style: "font-size:12.5px;font-weight:700;color:#0a2e6e;margin-bottom:4px;", text: t(lang, "lbl_visiting_schedule") }),
          el(
            "div",
            { className: "schedule-grid" },
            ch.schedules.map((sc) => {
              const slotText = sc.slotLabel || [sc.openTime, sc.closeTime].filter(Boolean).join(" – ");
              return el("div", { className: "schedule-pill" }, [
                el("strong", { text: sc.dayOfWeek || "-" }),
                el("span", { text: slotText || "-" })
              ]);
            })
          )
        ])
      );
    } else if (ch.visitingDays || ch.visitingHours) {
      const text = [ch.visitingDays, ch.visitingHours].filter(Boolean).join(" • ");
      scheduleSection.push(
        el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.clock, 16), el("span", { text: `${t(lang, "lbl_visiting_schedule")}: ${text}` })])
      );
    }

    const chActions = [];
    if (chTel) {
      chActions.push(
        el("a", { href: chTel, className: "btn btn-primary btn-sm" }, [
          svgIcon(ICONS.phone, 15),
          el("span", { text: ch.appointmentPhone || t(lang, "btn_call_now") })
        ])
      );
    }
    if (chWa) {
      chActions.push(
        el("a", { href: chWa, target: "_blank", rel: "noopener noreferrer", className: "btn btn-whatsapp btn-sm", text: t(lang, "btn_whatsapp") })
      );
    }
    if (chMap) {
      chActions.push(
        el("a", { href: chMap, target: "_blank", rel: "noopener noreferrer", className: "btn btn-outline btn-sm" }, [
          svgIcon(ICONS.map, 15),
          el("span", { text: t(lang, "btn_directions") })
        ])
      );
    }

    const fullLoc = [ch.address, ch.upazila, ch.district].filter(Boolean).join(", ");

    return el("div", { className: "chamber-card" }, [
      el("div", { style: "display:flex;align-items:center;justify-content:space-between;gap:8px;flex-wrap:wrap;" }, [
        el("strong", { style: "font-size:15.5px;color:#0a2e6e;", text: `${idx + 1}. ${ch.facilityName || ch.chamberName || doctor.currentHospital}` }),
        ch.district ? el("span", { className: "pill pill-blue", text: ch.district }) : null
      ]),
      fullLoc ? el("div", { className: "doc-meta-row" }, [svgIcon(ICONS.location, 16), el("span", { text: fullLoc })]) : null,
      ch.consultationFee
        ? el("div", { className: "doc-meta-row" }, [
            svgIcon(ICONS.fee, 16),
            el("span", { text: `${t(lang, "lbl_consultation_fee")}: ${ch.consultationFee} ${/^\d+$/.test(ch.consultationFee) ? t(lang, "lbl_taka") : ""}`.trim() })
          ])
        : null,
      ...scheduleSection,
      ch.instructions
        ? el("p", { style: "font-size:12.5px;color:#475569;background:#fff;border:1px solid #e2e8f0;padding:8px 10px;border-radius:9px;", text: `${t(lang, "lbl_instructions")}: ${ch.instructions}` })
        : null,
      chActions.length ? el("div", { style: "display:flex;flex-wrap:wrap;gap:8px;margin-top:4px;" }, chActions) : null
    ]);
  });

  const leftCol = el("div", { style: "display:flex;flex-direction:column;gap:20px;" }, [
    el("div", { className: "info-card" }, [
      el("h2", { text: t(lang, "lbl_chambers") }),
      chamberCards.length
        ? el("div", {}, chamberCards)
        : el("p", { style: "font-size:14px;color:#64748b;", text: t(lang, "lbl_not_specified") })
    ]),
    bio
      ? el("div", { className: "info-card" }, [
          el("h2", { text: t(lang, "lbl_about_doctor") }),
          el("p", { style: "font-size:14.5px;color:#334155;white-space:pre-line;", text: bio })
        ])
      : null
  ]);

  const quickRows = [
    [t(lang, "lbl_specialty"), spec],
    [t(lang, "lbl_designation"), doctor.designation],
    [t(lang, "lbl_hospital"), doctor.currentHospital],
    [t(lang, "lbl_bmdc_verified"), doctor.verifiedBmdcNumber],
    [t(lang, "lbl_experience"), doctor.experience ? (/^\d+$/.test(doctor.experience) ? `${formatNumber(doctor.experience, lang)} ${t(lang, "lbl_years")}` : doctor.experience) : ""],
    [t(lang, "lbl_appointment_contact"), doctor.phone],
    [t(lang, "lbl_district"), doctor.districts.join(", ")],
    [t(lang, "lbl_chamber_count"), formatNumber(doctor.chambers.length, lang)]
  ].filter(([, v]) => Boolean(v));

  const rightCol = el("aside", { className: "info-card" }, [
    el("h2", { text: t(lang, "lbl_quick_info") }),
    el(
      "div",
      { style: "display:flex;flex-direction:column;gap:12px;" },
      quickRows.map(([k, v]) =>
        el("div", { style: "border-bottom:1px solid #f1f5f9;padding-bottom:8px;" }, [
          el("div", { style: "font-size:12px;color:#64748b;font-weight:700;", text: k }),
          el("div", { style: "font-size:14px;color:#0f172a;font-weight:600;margin-top:2px;", text: v })
        ])
      )
    )
  ]);

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        heroCard,
        el("div", { className: "detail-grid" }, [leftCol, rightCol])
      ])
    ])
  );

  return frag;
}

// ============================================================================
// 4. HOSPITAL DIRECTORY VIEW (/hospitals)
// ============================================================================
export function renderHospitalsView(state, ctx, queryParams) {
  const { lang, navigate } = ctx;
  const pageSize = 12;

  const filters = {
    q: queryParams.get("q") || "",
    division: queryParams.get("division") || "",
    district: queryParams.get("district") || "",
    upazila: queryParams.get("upazila") || "",
    type: queryParams.get("type") || "",
    page: Math.max(1, Number(queryParams.get("page") || 1) || 1)
  };

  const applyFilterNavigation = (patch, resetPage = true) => {
    const next = { ...filters, ...patch };
    if (resetPage) next.page = 1;
    const p = new URLSearchParams();
    if (next.q.trim()) p.set("q", next.q.trim());
    if (next.division) p.set("division", next.division);
    if (next.district) p.set("district", next.district);
    if (next.upazila) p.set("upazila", next.upazila);
    if (next.type) p.set("type", next.type);
    if (next.page > 1) p.set("page", String(next.page));
    navigate(p.toString() ? `/hospitals?${p.toString()}` : "/hospitals");
  };

  const qLower = filters.q.trim().toLowerCase();
  const list = state.hospitals.filter((h) => {
    if (qLower) {
      const hay = `${h.nameEn} ${h.nameBn} ${h.address} ${h.district} ${h.upazila} ${h.type}`.toLowerCase();
      if (!hay.includes(qLower)) return false;
    }
    if (filters.division && h.division.toLowerCase() !== filters.division.toLowerCase()) return false;
    if (filters.district && h.district.toLowerCase() !== filters.district.toLowerCase()) return false;
    if (filters.upazila && h.upazila.toLowerCase() !== filters.upazila.toLowerCase()) return false;
    if (filters.type && h.type.toLowerCase() !== filters.type.toLowerCase()) return false;
    return true;
  });

  const totalPages = Math.max(1, Math.ceil(list.length / pageSize));
  const safePage = Math.min(filters.page, totalPages);
  const pageItems = list.slice((safePage - 1) * pageSize, safePage * pageSize);

  const hospitalTypes = Array.from(new Set(state.hospitals.map((h) => h.type).filter(Boolean))).sort();

  const qInput = el("input", {
    type: "search",
    className: "input",
    value: filters.q,
    placeholder: t(lang, "search_hospital_placeholder"),
    "aria-label": t(lang, "search_hospital_placeholder")
  });
  qInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      applyFilterNavigation({ q: qInput.value });
    }
  });

  const divSelect = el("select", { className: "select", "aria-label": t(lang, "all_divisions") }, [
    el("option", { value: "", text: t(lang, "all_divisions") }),
    ...BD_DIVISIONS.map((d) =>
      el("option", { value: d.en, text: lang === "bn" ? d.bn : d.en, selected: filters.division.toLowerCase() === d.en.toLowerCase() })
    )
  ]);
  divSelect.addEventListener("change", () => applyFilterNavigation({ division: divSelect.value, district: "", upazila: "" }));

  const distOptions = filters.division
    ? state.locations.districtsByDivision[filters.division] || []
    : state.locations.districts;
  const distSelect = el("select", { className: "select", "aria-label": t(lang, "all_districts") }, [
    el("option", { value: "", text: t(lang, "all_districts") }),
    ...distOptions.map((d) => el("option", { value: d, text: d, selected: filters.district === d }))
  ]);
  distSelect.addEventListener("change", () => applyFilterNavigation({ district: distSelect.value, upazila: "" }));

  const upzOptions = filters.district ? state.locations.upazilasByDistrict[filters.district] || [] : [];
  const upzSelect = el("select", { className: "select", "aria-label": t(lang, "all_upazilas") }, [
    el("option", { value: "", text: t(lang, "all_upazilas") }),
    ...upzOptions.map((u) => el("option", { value: u, text: u, selected: filters.upazila === u }))
  ]);
  upzSelect.addEventListener("change", () => applyFilterNavigation({ upazila: upzSelect.value }));

  const typeSelect = el("select", { className: "select", "aria-label": t(lang, "all_types") }, [
    el("option", { value: "", text: t(lang, "all_types") }),
    ...hospitalTypes.map((tp) => el("option", { value: tp, text: tp, selected: filters.type === tp }))
  ]);
  typeSelect.addEventListener("change", () => applyFilterNavigation({ type: typeSelect.value }));

  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_hospitals") }], navigate),
        el("h1", { text: t(lang, "hospitals_page_title") }),
        el("p", { text: t(lang, "hospitals_page_sub") })
      ])
    ])
  );

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("div", { className: "filter-panel" }, [
          el("div", { className: "filter-grid" }, [
            qInput,
            divSelect,
            distSelect,
            upzSelect,
            typeSelect,
            el("button", {
              type: "button",
              className: "btn btn-green",
              text: t(lang, "btn_search"),
              onClick: () => applyFilterNavigation({ q: qInput.value })
            })
          ])
        ]),
        el("div", { className: "results-bar" }, [
          el("span", { text: `${t(lang, "showing_results")}: ${formatNumber(list.length, lang)} ${t(lang, "hospitals_unit")}` }),
          el("button", { type: "button", className: "btn btn-outline btn-sm", text: t(lang, "btn_reset"), onClick: () => navigate("/hospitals") })
        ]),
        pageItems.length
          ? el("div", { className: "card-grid" }, pageItems.map((h) => createHospitalCard(h, ctx)))
          : createStateCard(t(lang, "empty_hospitals_title"), t(lang, "empty_hospitals_desc"), t(lang, "btn_reset"), () => navigate("/hospitals")),
        createPagination(safePage, totalPages, lang, (p) => applyFilterNavigation({ page: p }, false))
      ])
    ])
  );

  return frag;
}

// ============================================================================
// 5. HOSPITAL DETAILS VIEW (/hospital/{slug})
// ============================================================================
export function renderHospitalDetailsView(hospital, ctx) {
  const { lang, navigate, shareProfile } = ctx;
  const frag = document.createDocumentFragment();

  if (!hospital) {
    frag.appendChild(
      el("div", { className: "container section" }, [
        createStateCard(t(lang, "not_found_title"), t(lang, "not_found_hospital"), t(lang, "nav_hospitals"), () => navigate("/hospitals"))
      ])
    );
    return frag;
  }

  const name = pickLang(lang, hospital.nameBn, hospital.nameEn);
  const desc = pickLang(lang, hospital.descriptionBn, hospital.descriptionEn);
  const telHref = buildTelHref(hospital.phone);
  const mapHref = buildGoogleMapsHref(
    hospital.googleMapsUrl,
    [hospital.nameEn || hospital.nameBn, hospital.address, hospital.upazila, hospital.district, "Bangladesh"].filter(Boolean).join(", ")
  );

  const actions = [];
  if (telHref) {
    actions.push(el("a", { href: telHref, className: "btn btn-primary" }, [svgIcon(ICONS.phone, 17), el("span", { text: t(lang, "btn_call_now") })]));
  }
  if (mapHref) {
    actions.push(
      el("a", { href: mapHref, target: "_blank", rel: "noopener noreferrer", className: "btn btn-green" }, [
        svgIcon(ICONS.map, 17),
        el("span", { text: t(lang, "btn_directions") })
      ])
    );
  }
  if (hospital.website) {
    actions.push(
      el("a", { href: hospital.website, target: "_blank", rel: "noopener noreferrer", className: "btn btn-outline" }, [
        svgIcon(ICONS.globe, 17),
        el("span", { text: t(lang, "btn_visit_website") })
      ])
    );
  }
  actions.push(
    el("button", {
      type: "button",
      className: "btn btn-outline",
      onClick: () => shareProfile(name, `${name} — ${hospital.district || "Doctor List BD"}`)
    }, [svgIcon(ICONS.share, 17), el("span", { text: t(lang, "btn_share") })])
  );

  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb(
          [
            { label: t(lang, "nav_home"), href: "/" },
            { label: t(lang, "nav_hospitals"), href: "/hospitals" },
            { label: name }
          ],
          navigate
        ),
        el("h1", { text: name }),
        hospital.district ? el("p", { text: [hospital.upazila, hospital.district].filter(Boolean).join(", ") }) : null
      ])
    ])
  );

  const infoRows = [
    [t(lang, "lbl_hospital_type"), hospital.type],
    [t(lang, "lbl_full_address"), hospital.address],
    [t(lang, "lbl_division"), hospital.division ? getDivisionLabel(hospital.division, lang) : ""],
    [t(lang, "lbl_district"), hospital.district],
    [t(lang, "lbl_upazila"), hospital.upazila],
    [t(lang, "lbl_phone"), hospital.phone],
    [t(lang, "lbl_website"), hospital.website]
  ].filter(([, v]) => Boolean(v));

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("div", { className: "profile-hero-card" }, [
          createAvatar(hospital.imageUrl, name, true),
          el("div", {}, [
            el("span", { className: "pill pill-green", text: hospital.type || "Hospital" }),
            el("h2", { style: "font-size:23px;font-weight:800;color:#0a2e6e;margin-top:8px;", text: name }),
            hospital.address ? el("p", { style: "font-size:14px;color:#475569;margin-top:4px;", text: hospital.address }) : null,
            el("div", { className: "profile-actions" }, actions)
          ])
        ]),
        el("div", { className: "detail-grid" }, [
          el("div", { style: "display:flex;flex-direction:column;gap:20px;" }, [
            desc
              ? el("div", { className: "info-card" }, [
                  el("h2", { text: t(lang, "lbl_about_hospital") }),
                  el("p", { style: "font-size:14.5px;color:#334155;white-space:pre-line;", text: desc })
                ])
              : null,
            hospital.departments.length
              ? el("div", { className: "info-card" }, [
                  el("h2", { text: t(lang, "lbl_departments") }),
                  el(
                    "div",
                    { style: "display:flex;flex-wrap:wrap;gap:8px;" },
                    hospital.departments.map((dep) => el("span", { className: "pill pill-blue", text: dep }))
                  )
                ])
              : null,
            el("div", { className: "info-card" }, [
              el("h2", { text: `${t(lang, "lbl_associated_doctors")} (${formatNumber(hospital.associatedDoctors.length, lang)})` }),
              hospital.associatedDoctors.length
                ? el("div", { className: "card-grid" }, hospital.associatedDoctors.map((d) => createDoctorCard(d, ctx, true)))
                : el("p", { style: "font-size:14px;color:#64748b;", text: t(lang, "empty_doctors_title") })
            ])
          ]),
          el("aside", { className: "info-card" }, [
            el("h2", { text: t(lang, "lbl_quick_info") }),
            el(
              "div",
              { style: "display:flex;flex-direction:column;gap:12px;" },
              infoRows.map(([k, v]) =>
                el("div", { style: "border-bottom:1px solid #f1f5f9;padding-bottom:8px;" }, [
                  el("div", { style: "font-size:12px;color:#64748b;font-weight:700;", text: k }),
                  k === t(lang, "lbl_website")
                    ? el("a", {
                        href: v,
                        target: "_blank",
                        rel: "noopener noreferrer",
                        style: "font-size:13.5px;color:#0b5ed7;font-weight:600;word-break:break-all;",
                        text: v
                      })
                    : el("div", { style: "font-size:14px;color:#0f172a;font-weight:600;margin-top:2px;", text: v })
                ])
              )
            )
          ])
        ])
      ])
    ])
  );

  return frag;
}

// ============================================================================
// 6. SPECIALTY DIRECTORY VIEW (/specialties)
// ============================================================================
export function renderSpecialtiesView(state, ctx, queryParams) {
  const { lang, navigate } = ctx;
  const q = (queryParams.get("q") || "").trim();
  const qLower = q.toLowerCase();

  const filtered = state.specialties.filter((s) =>
    !qLower || `${s.nameEn} ${s.nameBn}`.toLowerCase().includes(qLower)
  );

  const searchInput = el("input", {
    type: "search",
    className: "input",
    value: q,
    placeholder: t(lang, "search_specialty_placeholder"),
    "aria-label": t(lang, "search_specialty_placeholder")
  });
  searchInput.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      const val = searchInput.value.trim();
      navigate(val ? `/specialties?q=${encodeURIComponent(val)}` : "/specialties");
    }
  });

  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_specialties") }], navigate),
        el("h1", { text: t(lang, "specialties_page_title") }),
        el("p", { text: t(lang, "specialties_page_sub") })
      ])
    ])
  );

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("div", { className: "filter-panel", style: "display:flex;gap:10px;flex-wrap:wrap;" }, [
          el("div", { style: "flex:1;min-width:220px;" }, [searchInput]),
          el("button", {
            type: "button",
            className: "btn btn-primary",
            text: t(lang, "btn_search"),
            onClick: () => {
              const val = searchInput.value.trim();
              navigate(val ? `/specialties?q=${encodeURIComponent(val)}` : "/specialties");
            }
          })
        ]),
        el("div", { className: "results-bar" }, [
          el("span", { text: `${t(lang, "showing_results")}: ${formatNumber(filtered.length, lang)} ${t(lang, "specialties_unit")}` })
        ]),
        filtered.length
          ? el("div", { className: "spec-grid" }, filtered.map((s) => createSpecialtyCard(s, ctx)))
          : createStateCard(t(lang, "empty_specialties_title"), t(lang, "empty_specialties_desc"))
      ])
    ])
  );

  return frag;
}

// ============================================================================
// 7. STATIC INFORMATION PAGES (About, Contact, Privacy Policy, Terms)
// ============================================================================
export function renderAboutView(state, ctx) {
  const { lang, navigate } = ctx;
  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_about") }], navigate),
        el("h1", { text: t(lang, "about_title") }),
        el("p", { text: t(lang, "about_sub") })
      ])
    ])
  );

  const paragraphs = lang === "bn"
    ? [
        "Doctor List BD বাংলাদেশের ৬৪ জেলার বিশেষজ্ঞ ডাক্তার, চেম্বারের সময়সূচী, হাসপাতাল ও ডায়াগনস্টিক সেন্টারের তথ্য এক জায়গায় উপস্থাপনকারী একটি আধুনিক ডিজিটাল স্বাস্থ্যসেবা ডিরেক্টরি।",
        "আমাদের লক্ষ্য হলো রোগী ও তাদের পরিবারের জন্য সঠিক বিশেষজ্ঞ চিকিৎসক, চেম্বারের ঠিকানা এবং সিরিয়ালের যোগাযোগ নম্বর দ্রুত ও নির্ভুলভাবে খুঁজে পাওয়া সহজ করা।",
        "এই ওয়েবসাইটে প্রদর্শিত সকল ডাক্তার, হাসপাতাল ও বিশেষজ্ঞতার তথ্য সরাসরি আমাদের কেন্দ্রীয় ডাটাবেস থেকে রিয়েল-টাইমে হালনাগাদ হয়।"
      ]
    : [
        "Doctor List BD is a nationwide digital healthcare directory for Bangladesh bringing together active specialist doctors, chamber schedules, hospitals, and diagnostic centers in one accessible platform.",
        "Our mission is to help patients and families quickly find reliable specialist information, verified chamber timings, and appointment contact details across all 8 divisions and 64 districts.",
        "All published listings are synchronized in real time from our central directory database so visitors always see up-to-date schedules and locations."
      ];

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("article", { className: "prose-card" }, [
          ...paragraphs.map((p) => el("p", { text: p })),
          el("h2", { text: lang === "bn" ? "ডিরেক্টরির বর্তমান পরিসংখ্যান" : "Live Directory Coverage" }),
          el("ul", {}, [
            el("li", { text: `${t(lang, "stat_doctors")}: ${formatNumber(state.stats.totalDoctors, lang)}` }),
            el("li", { text: `${t(lang, "stat_hospitals")}: ${formatNumber(state.stats.totalHospitals, lang)}` }),
            el("li", { text: `${t(lang, "stat_specialties")}: ${formatNumber(state.stats.totalSpecialties, lang)}` }),
            el("li", { text: `${t(lang, "stat_districts")}: ${formatNumber(state.stats.totalDistricts, lang)}` })
          ])
        ])
      ])
    ])
  );
  return frag;
}

export function renderContactView(state, ctx) {
  const { lang, navigate } = ctx;
  const email = String(state.settings?.contactEmail || state.settings?.supportEmail || "").trim();
  const phone = String(state.settings?.contactPhone || state.settings?.supportPhone || "").trim();

  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_contact") }], navigate),
        el("h1", { text: t(lang, "contact_title") }),
        el("p", { text: t(lang, "contact_sub") })
      ])
    ])
  );

  const contactItems = [];
  if (phone) {
    const href = buildTelHref(phone);
    contactItems.push(
      el("li", {}, [
        el("strong", { text: `${t(lang, "lbl_phone")}: ` }),
        href ? el("a", { href, style: "color:#0b5ed7;font-weight:700;", text: phone }) : el("span", { text: phone })
      ])
    );
  }
  if (email) {
    contactItems.push(
      el("li", {}, [
        el("strong", { text: "Email: " }),
        el("a", { href: `mailto:${encodeURIComponent(email)}`, style: "color:#0b5ed7;font-weight:700;", text: email })
      ])
    );
  }
  contactItems.push(el("li", { text: `${t(lang, "lbl_address")}: ${t(lang, "footer_country")}` }));

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("article", { className: "prose-card" }, [
          el("h2", { text: lang === "bn" ? "যোগাযোগের মাধ্যম" : "Contact Channels" }),
          el("p", {
            text: lang === "bn"
              ? "ডাক্তার বা হাসপাতালের কোনো তথ্য সংশোধন, নতুন প্রোফাইল যুক্তকরণ অথবা সাধারণ অনুসন্ধানের জন্য আমাদের সাথে যোগাযোগ করুন।"
              : "Contact the Doctor List BD editorial team for listing corrections, new doctor/hospital submissions, or general inquiries."
          }),
          el("ul", {}, contactItems),
          el("p", {
            style: "color:#047857;font-weight:600;",
            text: t(lang, "footer_disclaimer")
          })
        ])
      ])
    ])
  );
  return frag;
}

export function renderPrivacyView(ctx) {
  const { lang, navigate } = ctx;
  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_privacy") }], navigate),
        el("h1", { text: t(lang, "privacy_title") }),
        el("p", { text: t(lang, "privacy_sub") })
      ])
    ])
  );

  const items = lang === "bn"
    ? [
        "Doctor List BD পাবলিক ওয়েবসাইট ভিজিট করার জন্য কোনো অ্যাকাউন্ট বা লগইনের প্রয়োজন নেই।",
        "আমরা শুধুমাত্র আপনার নির্বাচিত ভাষা (বাংলা/ইংরেজি) এবং নোটিফিকেশন পঠিত স্ট্যাটাস আপনার ব্রাউজারের লোকাল স্টোরেজে সংরক্ষণ করি।",
        "ভিজিটরদের কোনো ব্যক্তিগত স্বাস্থ্য তথ্য বা সংবেদনশীল পরিচয় তথ্য এই ওয়েবসাইট সংগ্রহ করে না।",
        "ডাক্তার ও হাসপাতালের প্রকাশিত তথ্য শুধুমাত্র সাধারণ জনগণের স্বাস্থ্যসেবা অনুসন্ধানের সুবিধার্থে প্রদর্শিত হয়।"
      ]
    : [
        "No user account, sign-in, or registration is required to browse the Doctor List BD public directory.",
        "Only your language preference (Bangla/English) and notification read status are stored locally in your browser.",
        "This public directory does not collect personal medical records or private visitor credentials.",
        "Published doctor and hospital details are displayed strictly for public healthcare discovery."
      ];

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("article", { className: "prose-card" }, [
          el("h2", { text: t(lang, "privacy_title") }),
          el("ul", {}, items.map((line) => el("li", { text: line })))
        ])
      ])
    ])
  );
  return frag;
}

export function renderTermsView(ctx) {
  const { lang, navigate } = ctx;
  const frag = document.createDocumentFragment();
  frag.appendChild(
    el("section", { className: "page-banner" }, [
      el("div", { className: "container" }, [
        createBreadcrumb([{ label: t(lang, "nav_home"), href: "/" }, { label: t(lang, "nav_terms") }], navigate),
        el("h1", { text: t(lang, "terms_title") }),
        el("p", { text: t(lang, "terms_sub") })
      ])
    ])
  );

  const items = lang === "bn"
    ? [
        "Doctor List BD একটি তথ্যমূলক স্বাস্থ্যসেবা ডিরেক্টরি; এটি সরাসরি চিকিৎসাসেবা বা জরুরি অ্যাম্বুলেন্স সেবা প্রদান করে না।",
        "চেম্বারের সময়সূচী ও পরামর্শ ফি যেকোনো সময় সংশ্লিষ্ট ডাক্তার বা হাসপাতাল কর্তৃপক্ষ পরিবর্তন করতে পারে—ভিজিটের আগে ফোনে নিশ্চিত হয়ে নিন।",
        "জরুরি স্বাস্থ্য সমস্যার ক্ষেত্রে অবিলম্বে নিকটস্থ হাসপাতালের জরুরি বিভাগে যোগাযোগ করুন।",
        "ওয়েবসাইটের তথ্য স্বয়ংক্রিয়ভাবে স্ক্র্যাপিং বা অপব্যবহার করা নিষিদ্ধ।"
      ]
    : [
        "Doctor List BD is an informational healthcare directory and does not provide direct medical advice, diagnosis, or emergency services.",
        "Chamber schedules and consultation fees may change at the discretion of the respective physician or hospital; please confirm by phone before visiting.",
        "In a medical emergency, contact your nearest hospital emergency department immediately.",
        "Automated scraping or misuse of published directory contact details is strictly prohibited."
      ];

  frag.appendChild(
    el("section", { className: "section" }, [
      el("div", { className: "container" }, [
        el("article", { className: "prose-card" }, [
          el("h2", { text: t(lang, "terms_title") }),
          el("ul", {}, items.map((line) => el("li", { text: line })))
        ])
      ])
    ])
  );
  return frag;
}

// ============================================================================
// 8. NOTIFICATION INBOX DRAWER RENDERER
// ============================================================================
export function renderNotificationList(container, notifications, { lang, filterType, readIds, navigate, onMarkOneRead, onCloseDrawer }) {
  const filtered = filterType === "ALL"
    ? notifications
    : notifications.filter((n) => n.messageType === filterType);

  if (!filtered.length) {
    container.replaceChildren(
      el("div", { className: "state-card", style: "padding:28px 16px;" }, [
        el("p", { style: "font-size:13.5px;", text: t(lang, "notif_empty") })
      ])
    );
    return;
  }

  const typeBadge = (mt) => {
    if (mt === "NEW_DOCTOR") return el("span", { className: "pill pill-green", text: t(lang, "type_new_doctor") });
    if (mt === "APP_UPDATE") return el("span", { className: "pill pill-blue", text: t(lang, "type_app_update") });
    return el("span", { className: "pill pill-purple", text: t(lang, "type_general") });
  };

  const nodes = filtered.map((n) => {
    const isUnread = !readIds.has(n.id);
    const title = pickLang(lang, n.titleBn, n.titleEn);
    const message = pickLang(lang, n.messageBn, n.messageEn);

    const cardChildren = [
      el("div", { className: "notif-card-top" }, [
        typeBadge(n.messageType),
        el("span", { className: "notif-card-time", text: formatDateTime(n.createdAt, lang) })
      ]),
      el("div", { className: "notif-card-title", text: title }),
      el("div", { className: "notif-card-msg", text: message })
    ];

    if (n.imageUrl) {
      const img = el("img", { src: n.imageUrl, alt: title, className: "notif-card-img", loading: "lazy" });
      img.addEventListener("error", () => img.remove());
      cardChildren.push(img);
    }

    if (n.messageType === "NEW_DOCTOR" && n.selectedDoctorSlug) {
      const docUrl = `/doctor/${encodeURIComponent(n.selectedDoctorSlug)}`;
      const link = el("a", { href: docUrl, className: "btn btn-primary btn-sm", style: "align-self:flex-start;margin-top:4px;", text: t(lang, "notif_view_doctor") });
      link.addEventListener("click", (e) => {
        e.preventDefault();
        onMarkOneRead(n.id);
        onCloseDrawer();
        navigate(docUrl);
      });
      cardChildren.push(link);
    }

    const card = el("article", { className: isUnread ? "notif-card unread" : "notif-card" }, cardChildren);
    card.addEventListener("click", () => {
      if (isUnread) onMarkOneRead(n.id);
    });
    return card;
  });

  container.replaceChildren(...nodes);
}

// ============================================================================
// 9. UNIFIED HEADER SEARCH AUTOCOMPLETE RENDERER
// ============================================================================
export function renderAutocompleteDropdown(container, results, { lang, navigate, onSelect }) {
  const { doctors, specialties, hospitals, locations } = results;
  const total = doctors.length + specialties.length + hospitals.length + locations.length;

  if (!total) {
    container.replaceChildren(
      el("div", { style: "padding:14px;font-size:13px;color:#64748b;text-align:center;", text: t(lang, "auto_no_match") })
    );
    return;
  }

  const frag = document.createDocumentFragment();

  const addGroup = (titleKey, items, renderItem) => {
    if (!items.length) return;
    frag.appendChild(el("div", { className: "auto-group-title", text: t(lang, titleKey) }));
    for (const item of items) frag.appendChild(renderItem(item));
  };

  addGroup("auto_group_doctors", doctors, (d) =>
    el("button", {
      type: "button",
      className: "auto-item",
      onClick: () => {
        onSelect();
        navigate(`/doctor/${encodeURIComponent(d.slug)}`);
      }
    }, [
      el("div", {}, [
        el("div", { className: "auto-item-title", text: pickLang(lang, d.nameBn, d.nameEn) }),
        el("div", { className: "auto-item-sub", text: [pickLang(lang, d.specialtyBn, d.specialtyEn), d.primaryDistrict].filter(Boolean).join(" • ") })
      ]),
      el("span", { className: "pill pill-blue", text: t(lang, "auto_group_doctors") })
    ])
  );

  addGroup("auto_group_specialties", specialties, (s) =>
    el("button", {
      type: "button",
      className: "auto-item",
      onClick: () => {
        onSelect();
        navigate(`/doctors?specialty=${encodeURIComponent(s.id)}`);
      }
    }, [
      el("div", {}, [
        el("div", { className: "auto-item-title", text: pickLang(lang, s.nameBn, s.nameEn) }),
        el("div", { className: "auto-item-sub", text: `${formatNumber(s.doctorCount, lang)} ${t(lang, "doctors_unit")}` })
      ]),
      el("span", { className: "pill pill-purple", text: t(lang, "auto_group_specialties") })
    ])
  );

  addGroup("auto_group_hospitals", hospitals, (h) =>
    el("button", {
      type: "button",
      className: "auto-item",
      onClick: () => {
        onSelect();
        navigate(`/hospital/${encodeURIComponent(h.slug)}`);
      }
    }, [
      el("div", {}, [
        el("div", { className: "auto-item-title", text: pickLang(lang, h.nameBn, h.nameEn) }),
        el("div", { className: "auto-item-sub", text: [h.type, h.district].filter(Boolean).join(" • ") })
      ]),
      el("span", { className: "pill pill-green", text: t(lang, "auto_group_hospitals") })
    ])
  );

  addGroup("auto_group_locations", locations, (loc) =>
    el("button", {
      type: "button",
      className: "auto-item",
      onClick: () => {
        onSelect();
        const p = new URLSearchParams();
        if (loc.district) p.set("district", loc.district);
        if (loc.upazila) p.set("upazila", loc.upazila);
        navigate(`/doctors?${p.toString()}`);
      }
    }, [
      el("div", {}, [
        el("div", { className: "auto-item-title", text: loc.upazila ? `${loc.upazila}, ${loc.district}` : loc.district }),
        el("div", { className: "auto-item-sub", text: t(lang, "country_bangladesh") })
      ]),
      el("span", { className: "pill pill-amber", text: t(lang, "auto_group_locations") })
    ])
  );

  container.replaceChildren(frag);
}
