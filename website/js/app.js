// Main Application Controller & Clean-URL SPA Router for Doctor List BD Public Website

import { directoryStore } from "./db.js";
import { formatNumber, pickLang, t } from "./i18n.js";
import { applySeo, buildHospitalSchema, buildPhysicianSchema, buildWebsiteSchema } from "./seo.js";
import {
  renderAboutView,
  renderAutocompleteDropdown,
  renderContactView,
  renderDoctorProfileView,
  renderDoctorsView,
  renderErrorView,
  renderHomeView,
  renderHospitalDetailsView,
  renderHospitalsView,
  renderLoadingView,
  renderNotificationList,
  renderPrivacyView,
  renderSpecialtiesView,
  renderTermsView
} from "./views.js";

const STORAGE_LANG_KEY = "dlbd_visitor_lang";
const STORAGE_READ_NOTIFS_KEY = "dlbd_visitor_read_notifs";

let currentLang = localStorage.getItem(STORAGE_LANG_KEY) === "en" ? "en" : "bn";
let notifFilterType = "ALL";
let toastTimer = null;

function loadReadNotificationIds() {
  try {
    const raw = JSON.parse(localStorage.getItem(STORAGE_READ_NOTIFS_KEY) || "[]");
    return new Set(Array.isArray(raw) ? raw.map(String) : []);
  } catch {
    return new Set();
  }
}

let readNotifIds = loadReadNotificationIds();

function saveReadNotificationIds() {
  try {
    localStorage.setItem(STORAGE_READ_NOTIFS_KEY, JSON.stringify(Array.from(readNotifIds).slice(-300)));
  } catch {
    // ignore storage quota errors
  }
}

function isFileProtocol() {
  return window.location.protocol === "file:";
}

// Support restoration from 404.html (?p=/doctor/slug) on static hosts like GitHub Pages
function restoreStaticRedirectIfPresent() {
  try {
    const url = new URL(window.location.href);
    const redirected = url.searchParams.get("p");
    if (redirected && redirected.startsWith("/") && !isFileProtocol()) {
      window.history.replaceState(null, "", redirected);
    }
  } catch {
    // ignore
  }
}

function getCurrentRoute() {
  if (isFileProtocol()) {
    const rawHash = (window.location.hash || "#/").slice(1) || "/";
    const [pathPart, queryPart = ""] = rawHash.split("?");
    return { pathname: normalizePath(pathPart), params: new URLSearchParams(queryPart) };
  }
  return {
    pathname: normalizePath(window.location.pathname),
    params: new URLSearchParams(window.location.search)
  };
}

function normalizePath(p) {
  let clean = String(p || "/").trim();
  if (!clean.startsWith("/")) clean = "/" + clean;
  if (clean.endsWith("/index.html")) clean = clean.slice(0, -10) || "/";
  if (clean.length > 1 && clean.endsWith("/")) clean = clean.slice(0, -1);
  return clean || "/";
}

function navigate(targetUrl, { replace = false } = {}) {
  const dest = String(targetUrl || "/");
  if (isFileProtocol()) {
    const hashDest = "#" + (dest.startsWith("/") ? dest : "/" + dest);
    if (window.location.hash !== hashDest) {
      window.location.hash = hashDest;
    } else {
      renderCurrentRoute();
    }
  } else {
    const method = replace ? "replaceState" : "pushState";
    window.history[method](null, "", dest);
    renderCurrentRoute();
  }
  window.scrollTo({ top: 0, behavior: "smooth" });
}

function showToast(message) {
  const toastEl = document.getElementById("siteToast");
  if (!toastEl) return;
  toastEl.textContent = message;
  toastEl.classList.remove("hidden");
  if (toastTimer) clearTimeout(toastTimer);
  toastTimer = setTimeout(() => toastEl.classList.add("hidden"), 2800);
}

async function shareProfile(title, text) {
  const url = window.location.href;
  try {
    if (navigator.share) {
      await navigator.share({ title, text, url });
      return;
    }
    if (navigator.clipboard && navigator.clipboard.writeText) {
      await navigator.clipboard.writeText(url);
      showToast(t(currentLang, "toast_link_copied"));
      return;
    }
    showToast(t(currentLang, "toast_share_failed"));
  } catch (err) {
    if (err && err.name === "AbortError") return;
    showToast(t(currentLang, "toast_share_failed"));
  }
}

function applyStaticChromeTranslations() {
  document.documentElement.lang = currentLang;
  document.querySelectorAll("[data-i18n]").forEach((node) => {
    const key = node.getAttribute("data-i18n");
    if (key) node.textContent = t(currentLang, key);
  });

  const searchInput = document.getElementById("globalSearchInput");
  if (searchInput) {
    searchInput.placeholder = t(currentLang, "search_placeholder_global");
  }

  const headerTagline = document.getElementById("headerTagline");
  if (headerTagline) {
    const settings = directoryStore.state.settings || {};
    headerTagline.textContent =
      pickLang(currentLang, settings.taglineBn, settings.taglineEn) || t(currentLang, "header_tagline");
  }

  document.querySelectorAll(".lang-opt").forEach((btn) => {
    btn.classList.toggle("active", btn.dataset.lang === currentLang);
  });

  const footerYear = document.getElementById("footerYear");
  if (footerYear) footerYear.textContent = String(new Date().getFullYear());

  const settings = directoryStore.state.settings || {};
  const footerPhone = document.getElementById("footerPhone");
  const footerEmail = document.getElementById("footerEmail");
  if (footerPhone) {
    const phone = String(settings.contactPhone || settings.supportPhone || "").trim();
    footerPhone.textContent = phone ? `${t(currentLang, "lbl_phone")}: ${phone}` : "";
    footerPhone.classList.toggle("hidden", !phone);
  }
  if (footerEmail) {
    const email = String(settings.contactEmail || settings.supportEmail || "").trim();
    footerEmail.textContent = email ? `Email: ${email}` : "";
    footerEmail.classList.toggle("hidden", !email);
  }
}

function updateActiveNavLinks(pathname) {
  let activeKey = "/";
  if (pathname.startsWith("/doctors") || pathname.startsWith("/doctor/")) activeKey = "/doctors";
  else if (pathname.startsWith("/hospitals") || pathname.startsWith("/hospital/")) activeKey = "/hospitals";
  else if (pathname.startsWith("/specialties")) activeKey = "/specialties";
  else if (pathname.startsWith("/about")) activeKey = "/about";
  else if (pathname.startsWith("/contact")) activeKey = "/contact";

  document.querySelectorAll("[data-nav]").forEach((a) => {
    a.classList.toggle("active", a.getAttribute("data-nav") === activeKey);
  });
}

function refreshNotificationsUI() {
  const notifications = directoryStore.state.notifications || [];
  const unreadCount = notifications.filter((n) => !readNotifIds.has(n.id)).length;

  const badge = document.getElementById("notifUnreadBadge");
  if (badge) {
    badge.textContent = unreadCount > 99 ? "99+" : String(unreadCount);
    badge.classList.toggle("hidden", unreadCount === 0);
  }

  const pill = document.getElementById("notifCountPill");
  if (pill) pill.textContent = formatNumber(notifications.length, currentLang);

  const listEl = document.getElementById("notifListContainer");
  if (listEl) {
    renderNotificationList(listEl, notifications, {
      lang: currentLang,
      filterType: notifFilterType,
      readIds: readNotifIds,
      navigate,
      onMarkOneRead: (id) => {
        readNotifIds.add(id);
        saveReadNotificationIds();
        refreshNotificationsUI();
      },
      onCloseDrawer: closeAllDrawers
    });
  }
}

function renderCurrentRoute() {
  const mainEl = document.getElementById("appMain");
  if (!mainEl) return;

  const state = directoryStore.state;
  const { pathname, params } = getCurrentRoute();
  updateActiveNavLinks(pathname);
  applyStaticChromeTranslations();
  refreshNotificationsUI();

  if (state.loading) {
    mainEl.replaceChildren(renderLoadingView(currentLang));
    return;
  }

  if (state.error) {
    mainEl.replaceChildren(renderErrorView(currentLang, () => directoryStore.retry()));
    return;
  }

  const ctx = { lang: currentLang, navigate, shareProfile };

  // Route: /doctor/{slug}
  if (pathname.startsWith("/doctor/")) {
    const slug = pathname.slice("/doctor/".length);
    const doctor = directoryStore.findDoctorBySlugOrId(slug);
    mainEl.replaceChildren(renderDoctorProfileView(doctor, ctx));
    if (doctor) {
      const name = pickLang(currentLang, doctor.nameBn, doctor.nameEn);
      const spec = pickLang(currentLang, doctor.specialtyBn, doctor.specialtyEn);
      applySeo({
        title: `${name}${spec ? " — " + spec : ""} | Doctor List BD`,
        description: [name, doctor.degrees, spec, doctor.currentHospital, doctor.primaryDistrict].filter(Boolean).join(", "),
        path: `/doctor/${encodeURIComponent(doctor.slug)}`,
        ogType: "profile",
        image: doctor.photoUrl || "",
        schema: buildPhysicianSchema(doctor, `/doctor/${encodeURIComponent(doctor.slug)}`)
      });
    } else {
      applySeo({
        title: `${t(currentLang, "not_found_title")} | Doctor List BD`,
        description: t(currentLang, "not_found_doctor"),
        path: pathname
      });
    }
    return;
  }

  // Route: /hospital/{slug}
  if (pathname.startsWith("/hospital/")) {
    const slug = pathname.slice("/hospital/".length);
    const hospital = directoryStore.findHospitalBySlugOrId(slug);
    mainEl.replaceChildren(renderHospitalDetailsView(hospital, ctx));
    if (hospital) {
      const name = pickLang(currentLang, hospital.nameBn, hospital.nameEn);
      applySeo({
        title: `${name} | Doctor List BD`,
        description: [name, hospital.type, hospital.address, hospital.district].filter(Boolean).join(", "),
        path: `/hospital/${encodeURIComponent(hospital.slug)}`,
        ogType: "website",
        image: hospital.imageUrl || "",
        schema: buildHospitalSchema(hospital, `/hospital/${encodeURIComponent(hospital.slug)}`)
      });
    } else {
      applySeo({
        title: `${t(currentLang, "not_found_title")} | Doctor List BD`,
        description: t(currentLang, "not_found_hospital"),
        path: pathname
      });
    }
    return;
  }

  // Route: /doctors
  if (pathname === "/doctors") {
    mainEl.replaceChildren(renderDoctorsView(state, ctx, params));
    applySeo({
      title: `${t(currentLang, "doctors_page_title")} | Doctor List BD`,
      description: t(currentLang, "doctors_page_sub"),
      path: "/doctors",
      schema: buildWebsiteSchema()
    });
    return;
  }

  // Route: /hospitals
  if (pathname === "/hospitals") {
    mainEl.replaceChildren(renderHospitalsView(state, ctx, params));
    applySeo({
      title: `${t(currentLang, "hospitals_page_title")} | Doctor List BD`,
      description: t(currentLang, "hospitals_page_sub"),
      path: "/hospitals",
      schema: buildWebsiteSchema()
    });
    return;
  }

  // Route: /specialties
  if (pathname === "/specialties") {
    mainEl.replaceChildren(renderSpecialtiesView(state, ctx, params));
    applySeo({
      title: `${t(currentLang, "specialties_page_title")} | Doctor List BD`,
      description: t(currentLang, "specialties_page_sub"),
      path: "/specialties",
      schema: buildWebsiteSchema()
    });
    return;
  }

  // Route: /about
  if (pathname === "/about") {
    mainEl.replaceChildren(renderAboutView(state, ctx));
    applySeo({
      title: `${t(currentLang, "about_title")} | Doctor List BD`,
      description: t(currentLang, "about_sub"),
      path: "/about"
    });
    return;
  }

  // Route: /contact
  if (pathname === "/contact") {
    mainEl.replaceChildren(renderContactView(state, ctx));
    applySeo({
      title: `${t(currentLang, "contact_title")} | Doctor List BD`,
      description: t(currentLang, "contact_sub"),
      path: "/contact"
    });
    return;
  }

  // Route: /privacy-policy
  if (pathname === "/privacy-policy") {
    mainEl.replaceChildren(renderPrivacyView(ctx));
    applySeo({
      title: `${t(currentLang, "privacy_title")} | Doctor List BD`,
      description: t(currentLang, "privacy_sub"),
      path: "/privacy-policy"
    });
    return;
  }

  // Route: /terms
  if (pathname === "/terms") {
    mainEl.replaceChildren(renderTermsView(ctx));
    applySeo({
      title: `${t(currentLang, "terms_title")} | Doctor List BD`,
      description: t(currentLang, "terms_sub"),
      path: "/terms"
    });
    return;
  }

  // Default Route: Home (/)
  mainEl.replaceChildren(renderHomeView(state, ctx));
  applySeo({
    title: `Doctor List BD – ${t(currentLang, "hero_heading")}`,
    description: t(currentLang, "hero_subtitle"),
    path: "/",
    schema: buildWebsiteSchema()
  });
}

// ================= DRAWER & GLOBAL CONTROLS =================
function closeAllDrawers() {
  document.getElementById("mobileDrawer")?.classList.remove("open");
  document.getElementById("mobileDrawerBackdrop")?.classList.add("hidden");
  document.getElementById("mobileMenuBtn")?.setAttribute("aria-expanded", "false");

  document.getElementById("notifDrawer")?.classList.remove("open");
  document.getElementById("notifBackdrop")?.classList.add("hidden");
  document.getElementById("notifToggleBtn")?.setAttribute("aria-expanded", "false");
}

function wireChromeEvents() {
  // SPA link delegation for static header/drawer/footer links
  document.addEventListener("click", (e) => {
    const link = e.target.closest("a[data-nav]");
    if (!link) return;
    if (e.metaKey || e.ctrlKey || e.shiftKey || e.altKey) return;
    e.preventDefault();
    closeAllDrawers();
    navigate(link.getAttribute("data-nav") || "/");
  });

  // Language switcher
  document.querySelectorAll(".lang-opt").forEach((btn) => {
    btn.addEventListener("click", () => {
      const nextLang = btn.dataset.lang === "en" ? "en" : "bn";
      if (nextLang === currentLang) return;
      currentLang = nextLang;
      try {
        localStorage.setItem(STORAGE_LANG_KEY, currentLang);
      } catch {
        // ignore
      }
      renderCurrentRoute();
    });
  });

  // Mobile drawer
  const mobileBtn = document.getElementById("mobileMenuBtn");
  const mobileDrawer = document.getElementById("mobileDrawer");
  const mobileBackdrop = document.getElementById("mobileDrawerBackdrop");
  const mobileClose = document.getElementById("mobileDrawerClose");

  const openMobile = () => {
    closeAllDrawers();
    mobileDrawer?.classList.add("open");
    mobileBackdrop?.classList.remove("hidden");
    mobileBtn?.setAttribute("aria-expanded", "true");
  };
  mobileBtn?.addEventListener("click", openMobile);
  mobileClose?.addEventListener("click", closeAllDrawers);
  mobileBackdrop?.addEventListener("click", closeAllDrawers);

  // Notification drawer
  const notifBtn = document.getElementById("notifToggleBtn");
  const notifDrawer = document.getElementById("notifDrawer");
  const notifBackdrop = document.getElementById("notifBackdrop");
  const notifClose = document.getElementById("notifDrawerClose");
  const markAllBtn = document.getElementById("markAllReadBtn");

  const openNotif = () => {
    closeAllDrawers();
    refreshNotificationsUI();
    notifDrawer?.classList.add("open");
    notifBackdrop?.classList.remove("hidden");
    notifBtn?.setAttribute("aria-expanded", "true");
  };
  notifBtn?.addEventListener("click", openNotif);
  notifClose?.addEventListener("click", closeAllDrawers);
  notifBackdrop?.addEventListener("click", closeAllDrawers);

  markAllBtn?.addEventListener("click", () => {
    for (const n of directoryStore.state.notifications || []) {
      readNotifIds.add(n.id);
    }
    saveReadNotificationIds();
    refreshNotificationsUI();
    showToast(t(currentLang, "toast_marked_read"));
  });

  document.getElementById("notifFilterTabs")?.addEventListener("click", (e) => {
    const chip = e.target.closest("button[data-ntype]");
    if (!chip) return;
    notifFilterType = chip.dataset.ntype || "ALL";
    document.querySelectorAll("#notifFilterTabs .chip").forEach((c) => {
      c.classList.toggle("active", c === chip);
    });
    refreshNotificationsUI();
  });

  // Unified Header Search Bar + Live Autocomplete
  const searchInput = document.getElementById("globalSearchInput");
  const clearBtn = document.getElementById("globalSearchClear");
  const goBtn = document.getElementById("globalSearchSubmit");
  const dropdown = document.getElementById("globalSearchDropdown");
  const searchWrap = document.getElementById("headerSearchWrap");

  const hideDropdown = () => dropdown?.classList.add("hidden");

  const runAutocomplete = () => {
    const q = (searchInput?.value || "").trim();
    clearBtn?.classList.toggle("hidden", !q);
    if (!q || !dropdown) {
      hideDropdown();
      return;
    }
    const results = directoryStore.searchUnified(q);
    renderAutocompleteDropdown(dropdown, results, {
      lang: currentLang,
      navigate,
      onSelect: () => {
        hideDropdown();
        if (searchInput) searchInput.value = "";
        clearBtn?.classList.add("hidden");
      }
    });
    dropdown.classList.remove("hidden");
  };

  const submitHeaderSearch = () => {
    const q = (searchInput?.value || "").trim();
    hideDropdown();
    if (!q) {
      navigate("/doctors");
      return;
    }
    navigate(`/doctors?q=${encodeURIComponent(q)}`);
  };

  searchInput?.addEventListener("input", runAutocomplete);
  searchInput?.addEventListener("focus", () => {
    if ((searchInput.value || "").trim()) runAutocomplete();
  });
  searchInput?.addEventListener("keydown", (e) => {
    if (e.key === "Enter") {
      e.preventDefault();
      submitHeaderSearch();
    } else if (e.key === "Escape") {
      hideDropdown();
    }
  });
  clearBtn?.addEventListener("click", () => {
    if (searchInput) {
      searchInput.value = "";
      searchInput.focus();
    }
    clearBtn.classList.add("hidden");
    hideDropdown();
  });
  goBtn?.addEventListener("click", submitHeaderSearch);

  document.addEventListener("click", (e) => {
    if (searchWrap && !searchWrap.contains(e.target)) {
      hideDropdown();
    }
  });

  document.addEventListener("keydown", (e) => {
    if (e.key === "Escape") {
      closeAllDrawers();
      hideDropdown();
    }
  });

  window.addEventListener("popstate", renderCurrentRoute);
  window.addEventListener("hashchange", () => {
    if (isFileProtocol()) renderCurrentRoute();
  });
}

// ================= BOOTSTRAP =================
function boot() {
  restoreStaticRedirectIfPresent();
  wireChromeEvents();
  applyStaticChromeTranslations();
  directoryStore.subscribe(() => {
    renderCurrentRoute();
  });
  directoryStore.start();
}

boot();
