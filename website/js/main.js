/**
 * NOTAS SHOWCASE — MODERN JAVASCRIPT
 * Strictly compliant with Modern Web Guidance:
 * - Native <dialog> with showModal(), closedby="any" and click-outside handling
 * - Accessible site navigation with scrollspy via IntersectionObserver
 * - Interactive architecture explorer and live device mockup switcher
 */

document.addEventListener('DOMContentLoaded', () => {
  initNavigation();
  initScrollspy();
  initDeviceSwitcher();
  initArchitectureExplorer();
  initGalleryFilter();
  initLightboxDialog();
  initThemeAccentSwitcher();
});

/* --------------------------------------------------------------------------
   1. Responsive Site Navigation & Mobile Menu
   -------------------------------------------------------------------------- */
function initNavigation() {
  const header = document.querySelector('.site-header');
  const menuBtn = document.querySelector('.menu-button');
  const menu = document.querySelector('#site-menu');

  if (!header || !menu) return;

  // Measure inline row fit & toggle is-wide class
  const checkWidth = () => {
    const isWide = window.innerWidth > 992;
    if (isWide) {
      header.classList.add('is-wide');
      menu.removeAttribute('popover');
    } else {
      header.classList.remove('is-wide');
      menu.setAttribute('popover', 'auto');
    }
  };

  checkWidth();
  window.addEventListener('resize', checkWidth);

  // Close mobile popover when a link is clicked
  menu.querySelectorAll('a').forEach(link => {
    link.addEventListener('click', () => {
      if (menu.matches(':popover-open')) {
        menu.hidePopover();
      }
    });
  });
}

/* --------------------------------------------------------------------------
   2. Scrollspy with IntersectionObserver
   -------------------------------------------------------------------------- */
function initScrollspy() {
  const sections = document.querySelectorAll('section[id]');
  const navLinks = document.querySelectorAll('.site-menu a');

  if (!sections.length || !navLinks.length) return;

  const observer = new IntersectionObserver(
    (entries) => {
      entries.forEach((entry) => {
        if (entry.isIntersecting) {
          const id = entry.target.getAttribute('id');
          navLinks.forEach((link) => {
            const href = link.getAttribute('href');
            if (href === `#${id}`) {
              link.setAttribute('aria-current', 'page');
              link.classList.add('active');
            } else {
              link.removeAttribute('aria-current');
              link.classList.remove('active');
            }
          });
        }
      });
    },
    {
      rootMargin: '-20% 0px -70% 0px',
      threshold: 0,
    }
  );

  sections.forEach((section) => observer.observe(section));
}

/* --------------------------------------------------------------------------
   3. Hero Device Mockup Interactive Switcher
   -------------------------------------------------------------------------- */
function initDeviceSwitcher() {
  const switchBtns = document.querySelectorAll('.device-switch-btn');
  const phoneImg = document.querySelector('#hero-phone-img');

  if (!switchBtns.length || !phoneImg) return;

  const views = {
    notes: 'assets/screenshots/notes_populated.png',
    calendar: 'assets/screenshots/calendar_year.png',
    search: 'assets/screenshots/search_filters.png',
  };

  switchBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const view = btn.dataset.view;
      if (!views[view]) return;

      switchBtns.forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');

      phoneImg.style.opacity = '0.3';
      phoneImg.style.transform = 'scale(0.97)';

      setTimeout(() => {
        phoneImg.src = views[view];
        phoneImg.style.opacity = '1';
        phoneImg.style.transform = 'scale(1)';
      }, 150);
    });
  });
}

/* --------------------------------------------------------------------------
   4. Interactive Architecture Explorer
   -------------------------------------------------------------------------- */
function initArchitectureExplorer() {
  // Layer cards
  const layers = document.querySelectorAll('.arch-layer');
  const layerDetails = document.querySelector('#arch-layer-details');

  const layerData = {
    presentation: {
      title: 'Presentation Layer (Android Client)',
      desc: 'Built 100% in Jetpack Compose following MVI / MVVM and Unidirectional Data Flow (UDF). ViewModels expose immutable StateFlow to composables while UI events flow backwards through type-safe sealed interfaces. Implements custom theme tokens, window insets, and adaptive layouts for phones, foldables, and tablets.',
      specs: ['Jetpack Compose & Material 3', 'StateFlow & SharedFlow', 'Dagger Hilt ViewModels', 'Multi-window Inset Handling'],
    },
    domain: {
      title: 'Domain & Business Logic Layer',
      desc: 'Pure Kotlin business logic independent of Android framework APIs. Enforces recurrence rules engines, hierarchical checklist auto-completion, Markdown AST transformations, and conflict resolution policies using Hybrid Logical Clocks (HLC).',
      specs: ['Deterministic Recurrence Engine', 'Checklist Hierarchical Validator', 'HLC Conflict Merging Logic', 'Media Manifest Builder'],
    },
    data: {
      title: 'Data & Offline-First Persistence Layer',
      desc: 'Single source of truth via local Room database (SQLite) with automatic schema migrations (v1 through v8). Uses Android Keystore for AES-256-GCM hardware encryption of locked notes and DataStore for thread-safe typed preferences.',
      specs: ['Room Database SQLite', 'Android Keystore AES-256', 'DataStore Preferences', 'FileProvider Private Storage'],
    },
    sync: {
      title: 'Transport & Synchronization Engine',
      desc: 'Tracks local mutations in a pending change journal. Communicates via authenticated delta batches over HTTPS with revision watermarking. Includes distributed rate-limiting and token revocation detection.',
      specs: ['Incremental Delta Sync Protocol', 'Firebase Token Bearer Auth', 'Sliding Window Rate Limiter', 'Structured Error Hierarchy'],
    },
    cloud: {
      title: 'Serverless Cloud Backend (Vercel & PostgreSQL)',
      desc: 'Serverless REST API deployed on Vercel Node.js runtime. Uses Prisma ORM to interact with managed PostgreSQL with full Room schema parity. Connects to AWS S3 / Cloudflare R2 for encrypted media and ZIP backup storage.',
      specs: ['Vercel Serverless Functions', 'Prisma ORM 5.22', 'PostgreSQL with PgBouncer', 'AWS S3 / Cloudflare R2'],
    },
  };

  layers.forEach((layer) => {
    layer.addEventListener('click', () => {
      layers.forEach((l) => l.classList.remove('active'));
      layer.classList.add('active');

      const key = layer.dataset.layer;
      const data = layerData[key];
      if (!data || !layerDetails) return;

      layerDetails.innerHTML = `
        <div class="card" style="animation: fadeIn 250ms ease-out forwards;">
          <div class="card-meta">SELECTED ARCHITECTURAL LAYER</div>
          <h4 class="card-title">${data.title}</h4>
          <p style="margin-bottom: var(--space-4);">${data.desc}</p>
          <div style="display: flex; gap: var(--space-2); flex-wrap: wrap;">
            ${data.specs.map(spec => `<span class="badge"><span class="badge-dot"></span>${spec}</span>`).join('')}
          </div>
        </div>
      `;
    });
  });

  // Deep Dive Tabs
  const deepDiveBtns = document.querySelectorAll('.arch-tab-btn');
  const deepDivePanes = document.querySelectorAll('.arch-tab-pane');

  deepDiveBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const targetId = btn.dataset.target;
      deepDiveBtns.forEach((b) => b.classList.remove('active'));
      deepDivePanes.forEach((p) => p.classList.remove('active'));

      btn.classList.add('active');
      const targetPane = document.getElementById(targetId);
      if (targetPane) targetPane.classList.add('active');
    });
  });
}

/* --------------------------------------------------------------------------
   5. Gallery Category Filter
   -------------------------------------------------------------------------- */
function initGalleryFilter() {
  const filterBtns = document.querySelectorAll('.gallery-filter-btn');
  const galleryItems = document.querySelectorAll('.gallery-card');

  filterBtns.forEach((btn) => {
    btn.addEventListener('click', () => {
      const category = btn.dataset.category;
      filterBtns.forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');

      galleryItems.forEach((item) => {
        if (category === 'all' || item.dataset.category === category) {
          item.style.display = 'block';
        } else {
          item.style.display = 'none';
        }
      });
    });
  });
}

/* --------------------------------------------------------------------------
   6. Modern Accessible Lightbox Dialog (<dialog> + Modern Web Guidance)
   -------------------------------------------------------------------------- */
function initLightboxDialog() {
  const dialog = document.querySelector('#lightbox-dialog');
  const lightboxImg = document.querySelector('#lightbox-img');
  const lightboxTitle = document.querySelector('#lightbox-title');
  const lightboxCategory = document.querySelector('#lightbox-category');
  const lightboxDesc = document.querySelector('#lightbox-desc');
  const lightboxSpecs = document.querySelector('#lightbox-specs');
  const closeBtn = document.querySelector('#lightbox-close');

  if (!dialog) return;

  // Click outside to close (Modern Web Guidance fallback for closedby="any")
  if (!('closedBy' in HTMLDialogElement.prototype)) {
    const outside = (e, d) => {
      const r = d.getBoundingClientRect();
      return e.clientX < r.left || e.clientX > r.right || e.clientY < r.top || e.clientY > r.bottom;
    };
    let pressedOutside = false;
    dialog.addEventListener('pointerdown', (e) => {
      pressedOutside = outside(e, dialog);
    });
    dialog.addEventListener('pointerup', (e) => {
      if (pressedOutside && outside(e, dialog)) dialog.close();
    });
  }

  if (closeBtn) {
    closeBtn.addEventListener('click', () => dialog.close());
  }

  // Open lightbox on clicking any gallery card or preview image
  document.querySelectorAll('[data-lightbox-src]').forEach((trigger) => {
    trigger.addEventListener('click', () => {
      const src = trigger.dataset.lightboxSrc;
      const title = trigger.dataset.lightboxTitle || 'Application Screenshot';
      const cat = trigger.dataset.lightboxCategory || 'Notas UI';
      const desc = trigger.dataset.lightboxDesc || 'High-fidelity mobile screenshot captured from the active Notas build.';
      const tech = trigger.dataset.lightboxTech || 'Jetpack Compose • Room • M3';

      if (lightboxImg) lightboxImg.src = src;
      if (lightboxTitle) lightboxTitle.textContent = title;
      if (lightboxCategory) lightboxCategory.textContent = cat;
      if (lightboxDesc) lightboxDesc.textContent = desc;
      if (lightboxSpecs) lightboxSpecs.textContent = tech;

      dialog.showModal();
    });
  });
}

/* --------------------------------------------------------------------------
   7. Live Accent Theme Switcher
   -------------------------------------------------------------------------- */
function initThemeAccentSwitcher() {
  const accentButtons = document.querySelectorAll('.accent-dot-btn');
  if (!accentButtons.length) return;

  accentButtons.forEach((btn) => {
    btn.addEventListener('click', () => {
      const color = btn.dataset.accentColor;
      const glow = btn.dataset.accentGlow;
      if (!color) return;

      accentButtons.forEach((b) => b.classList.remove('active'));
      btn.classList.add('active');

      document.documentElement.style.setProperty('--accent-primary', color);
      document.documentElement.style.setProperty('--accent-primary-hover', color);
      if (glow) {
        document.documentElement.style.setProperty('--accent-primary-glow', glow);
      }
    });
  });
}
