(function () {
  const PLAY_STORE =
    "https://play.google.com/store/apps/details?id=fr.geoking.gaston";

  const screenshots = [
    { src: "assets/screenshots/screenshot-1-map.png", alt: "Carte des stations" },
    { src: "assets/screenshots/screenshot-2-fuel-prices.png", alt: "Prix carburant" },
    { src: "assets/screenshots/screenshot-3-ev-charging.png", alt: "Recharge EV" },
    { src: "assets/screenshots/screenshot-4-filters.png", alt: "Filtres" },
    { src: "assets/screenshots/screenshot-5-android-auto.png", alt: "Android Auto" },
  ];

  const i18n = {
    fr: {
      "nav.features": "Fonctionnalités",
      "nav.showcase": "Aperçu",
      "nav.auto": "Android Auto",
      "nav.download": "Télécharger",
      "hero.badge": "Android & Android Auto",
      "hero.title":
        "Ne restez plus à sec. Trouvez le meilleur <span class='highlight-fuel'>carburant</span> et la bonne <span class='highlight-ev'>recharge</span>.",
      "hero.lead":
        "Gaston est votre copilote routier : prix en temps réel, bornes IRVE, itinéraires intelligents — sur votre téléphone et au volant.",
      "hero.cta.primary": "Google Play",
      "hero.cta.secondary": "Découvrir",
      "stat.brands": "marques",
      "stat.countries": "pays couverts",
      "stat.auto": "Android Auto",
      "features.label": "Fonctionnalités",
      "features.title": "Tout pour la route, rien de superflu",
      "features.desc":
        "Données ouvertes et publiques, filtres puissants, interface pensée pour conduire en toute sécurité.",
      "f1.title": "Prix carburant en direct",
      "f1.desc":
        "SP95, E10, SP98, diesel, GPL… Comparez Shell, TotalEnergies, Leclerc, Intermarché et des dizaines d’enseignes.",
      "f2.title": "Recharge électrique",
      "f2.desc":
        "Bornes IRVE, CCS2, CHAdeMO, Type 2, Tesla. Filtrez par puissance (22 kW → 350 kW) et disponibilité.",
      "f3.title": "Le long de votre trajet",
      "f3.desc":
        "Stations le long de l’itinéraire, pas seulement autour de vous. Carburant et recharge dans une seule recherche.",
      "f4.title": "Aires & services",
      "f4.desc":
        "Toilettes, aires de repos, pique-nique, camping-cars, parkings — pour tous les passagers.",
      "f5.title": "Android Auto natif",
      "f5.desc":
        "Cartes lisibles, prix en un coup d’œil, navigation en un tap. Les yeux sur la route.",
      "f6.title": "Filtres qui persistent",
      "f6.desc":
        "Énergie, marque, connecteur, puissance, services. Réglez une fois, roulez toujours.",
      "showcase.label": "Aperçu",
      "showcase.title": "Conçu pour être lu en un regard",
      "showcase.desc": "Carte, détails station, filtres et mode voiture.",
      "cap.map": "Carte & stations proches",
      "cap.fuel": "Liste des prix",
      "cap.ev": "Détail recharge",
      "cap.filters": "Filtres avancés",
      "cap.auto": "Liste Android Auto",
      "auto.label": "Au volant",
      "auto.title": "Android Auto, pensé pour la sécurité",
      "auto.desc":
        "Templates Car App Library : grandes cartes, zéro distraction. Un tap pour lancer la navigation.",
      "auto.li1": "Cartes lisibles avec nom, distance et prix",
      "auto.li2": "Filtres énergie et connecteurs depuis l’écran voiture",
      "auto.li3": "Ouverture directe dans votre app de navigation",
      "cta.title": "Prêt à prendre la route ?",
      "cta.desc":
        "Téléchargez Gaston gratuitement sur Google Play. Votre prochain plein ou recharge n’est qu’à quelques taps.",
      "cta.btn": "Installer sur Google Play",
      "footer.tagline": "Stations carburant & recharge — Android & Android Auto.",
      "footer.privacy": "Confidentialité",
      "footer.terms": "Conditions",
      "footer.by": "par",
    },
    en: {
      "nav.features": "Features",
      "nav.showcase": "Preview",
      "nav.auto": "Android Auto",
      "nav.download": "Download",
      "hero.badge": "Android & Android Auto",
      "hero.title":
        "Never run dry. Find the best <span class='highlight-fuel'>fuel</span> and the right <span class='highlight-ev'>charge</span>.",
      "hero.lead":
        "Gaston is your road-trip co-pilot: live prices, public charging, smart routes — on your phone and behind the wheel.",
      "hero.cta.primary": "Google Play",
      "hero.cta.secondary": "Explore",
      "stat.brands": "brands",
      "stat.countries": "countries",
      "stat.auto": "Android Auto",
      "features.label": "Features",
      "features.title": "Everything for the road, nothing extra",
      "features.desc":
        "Open public data, powerful filters, and an interface built for safe driving.",
      "f1.title": "Live fuel prices",
      "f1.desc":
        "SP95, E10, SP98, diesel, LPG… Compare Shell, TotalEnergies, Leclerc, Intermarché and dozens more.",
      "f2.title": "EV charging",
      "f2.desc":
        "Public chargers, CCS2, CHAdeMO, Type 2, Tesla. Filter by power (22 kW → 350 kW) and availability.",
      "f3.title": "Along your route",
      "f3.desc":
        "Stations on your path, not just around you. Fuel and charging in one search.",
      "f4.title": "Rest stops & services",
      "f4.desc":
        "Toilets, rest areas, picnic spots, camper services, parking — for every passenger.",
      "f5.title": "Native Android Auto",
      "f5.desc":
        "Readable cards, prices at a glance, one-tap navigation. Eyes on the road.",
      "f6.title": "Filters that stick",
      "f6.desc":
        "Energy, brand, connector, power, services. Set once, drive always.",
      "showcase.label": "Preview",
      "showcase.title": "Designed to read in a glance",
      "showcase.desc": "Map, station details, filters, and in-car mode.",
      "cap.map": "Map & nearby stations",
      "cap.fuel": "Full price list",
      "cap.ev": "Charging details",
      "cap.filters": "Advanced filters",
      "cap.auto": "Android Auto list",
      "auto.label": "Behind the wheel",
      "auto.title": "Android Auto built for safety",
      "auto.desc":
        "Car App Library templates: big cards, zero clutter. One tap to open navigation.",
      "auto.li1": "Readable cards with name, distance and price",
      "auto.li2": "Energy and connector filters from the car screen",
      "auto.li3": "Launch your preferred maps app in one tap",
      "cta.title": "Ready to hit the road?",
      "cta.desc":
        "Download Gaston free on Google Play. Your next fill-up or charge is just a few taps away.",
      "cta.btn": "Get it on Google Play",
      "footer.tagline": "Fuel & EV stations — Android & Android Auto.",
      "footer.privacy": "Privacy",
      "footer.terms": "Terms",
      "footer.by": "by",
    },
  };

  let lang = localStorage.getItem("gaston-lang") || "fr";
  let slideIndex = 0;
  let slideTimer;

  function t(key) {
    return (i18n[lang] && i18n[lang][key]) || i18n.fr[key] || key;
  }

  function applyI18n() {
    document.documentElement.lang = lang;
    document.querySelectorAll("[data-i18n]").forEach((el) => {
      const key = el.getAttribute("data-i18n");
      const html = t(key);
      if (html.includes("<")) el.innerHTML = html;
      else el.textContent = html;
    });
    const toggle = document.querySelector(".lang-toggle");
    if (toggle) toggle.textContent = lang === "fr" ? "EN" : "FR";
    localStorage.setItem("gaston-lang", lang);
  }

  function setSlide(index) {
    slideIndex = (index + screenshots.length) % screenshots.length;
    const img = document.getElementById("hero-screenshot");
    const dots = document.querySelectorAll(".phone-dots button");
    if (!img) return;
    const s = screenshots[slideIndex];
    img.src = s.src;
    img.alt = s.alt;
    dots.forEach((d, i) => d.classList.toggle("is-active", i === slideIndex));
  }

  function startSlideshow() {
    clearInterval(slideTimer);
    slideTimer = setInterval(() => setSlide(slideIndex + 1), 4500);
  }

  function initHeader() {
    const header = document.querySelector(".site-header");
    const menuBtn = document.querySelector(".menu-btn");
    const navMobile = document.querySelector(".nav-mobile");

    const onScroll = () => {
      header.classList.toggle("is-scrolled", window.scrollY > 24);
    };
    window.addEventListener("scroll", onScroll, { passive: true });
    onScroll();

    if (menuBtn && navMobile) {
      menuBtn.addEventListener("click", () => {
        const open = menuBtn.getAttribute("aria-expanded") === "true";
        menuBtn.setAttribute("aria-expanded", String(!open));
        navMobile.classList.toggle("is-open", !open);
        document.body.style.overflow = !open ? "hidden" : "";
      });

      navMobile.querySelectorAll("a").forEach((a) => {
        a.addEventListener("click", () => {
          menuBtn.setAttribute("aria-expanded", "false");
          navMobile.classList.remove("is-open");
          document.body.style.overflow = "";
        });
      });
    }
  }

  function initReveal() {
    const els = document.querySelectorAll(".reveal");
    if (!("IntersectionObserver" in window)) {
      els.forEach((el) => el.classList.add("is-visible"));
      return;
    }
    const io = new IntersectionObserver(
      (entries) => {
        entries.forEach((e) => {
          if (e.isIntersecting) {
            e.target.classList.add("is-visible");
            io.unobserve(e.target);
          }
        });
      },
      { threshold: 0.12, rootMargin: "0px 0px -40px 0px" }
    );
    els.forEach((el) => io.observe(el));
  }

  function initLang() {
    document.querySelector(".lang-toggle")?.addEventListener("click", () => {
      lang = lang === "fr" ? "en" : "fr";
      applyI18n();
    });
  }

  function initPhone() {
    const dotsWrap = document.querySelector(".phone-dots");
    if (!dotsWrap) return;
    screenshots.forEach((_, i) => {
      const btn = document.createElement("button");
      btn.type = "button";
      btn.setAttribute("aria-label", `Slide ${i + 1}`);
      if (i === 0) btn.classList.add("is-active");
      btn.addEventListener("click", () => {
        setSlide(i);
        startSlideshow();
      });
      dotsWrap.appendChild(btn);
    });
    setSlide(0);
    startSlideshow();
  }

  function initPlayLinks() {
    document.querySelectorAll("[data-play-store]").forEach((a) => {
      a.href = PLAY_STORE;
      a.setAttribute("rel", "noopener noreferrer");
      a.setAttribute("target", "_blank");
    });
  }

  document.addEventListener("DOMContentLoaded", () => {
    applyI18n();
    initHeader();
    initReveal();
    initLang();
    initPhone();
    initPlayLinks();
  });
})();
