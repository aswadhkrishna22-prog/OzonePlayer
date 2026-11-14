/**
 * OZONE PLAYER // AURAL ARCHITECTURE PRO
 * Synthesis of Lumora (precision loader & clock), Soda (force-field repulsion), and Laocoön (double cursor & grid)
 */

(function() {
  // 1. PRECISION INTRO LOADER (Lumora style)
  initPageLoader();

  // 2. DOUBLE-RING CURSOR (Laocoön style)
  initCustomCursor();

  // 3. AMBIENT PARTICLE FORCE-FIELD (Soda style)
  initAmbientCanvas();

  // 4. LIVE CLOCK (Lumora style)
  initLiveClock();

  // 5. INTERACTIVE DUAL PLAY CROSSFADER
  initDualPlayConsole();

  // 6. INTERACTIVE FLAC BITRATE ANALYZER
  initBitrateSwitcher();

  // 7. 3D GYRO PHONE TILT
  initPhoneTilt();

  // 8. SCREEN SHOWCASE TABS
  initShowcaseTabs();

  // 9. NAVBAR SCROLL & STATS COUNT-UP
  initScrollEffects();

  // 10. GITHUB STARS SYNC
  fetchGitHubStars();
})();

/* ==========================================================================
   1. PRECISION INTRO LOADER (Lumora style)
   ========================================================================== */
function initPageLoader() {
  const loader = document.getElementById('page-loader');
  const fill = document.getElementById('loader-fill');
  const counter = document.getElementById('loader-counter');
  if (!loader || !fill || !counter) return;

  document.body.classList.add('locked');
  const duration = 1200; // ms
  const startTime = performance.now();

  function easeInOutCubic(t) {
    return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
  }

  function tick(now) {
    const elapsed = now - startTime;
    const progress = Math.min(1, elapsed / duration);
    const eased = easeInOutCubic(progress);
    const val = Math.round(eased * 100);

    fill.style.width = `${val}%`;
    counter.textContent = String(val).padStart(3, '0');

    if (progress < 1) {
      requestAnimationFrame(tick);
    } else {
      setTimeout(() => {
        loader.classList.add('done');
        document.body.classList.remove('locked');
      }, 200);
    }
  }

  requestAnimationFrame(tick);
}

/* ==========================================================================
   2. CUSTOM DOUBLE-RING CURSOR (Laocoön style)
   ========================================================================== */
function initCustomCursor() {
  const inner = document.getElementById('cursor-inner');
  const outer = document.getElementById('cursor-outer');
  if (!inner || !outer || !window.matchMedia('(hover: hover)').matches) return;

  let mouseX = window.innerWidth / 2;
  let mouseY = window.innerHeight / 2;
  let outerX = mouseX;
  let outerY = mouseY;

  window.addEventListener('mousemove', (e) => {
    mouseX = e.clientX;
    mouseY = e.clientY;
    inner.style.left = `${mouseX}px`;
    inner.style.top = `${mouseY}px`;
  }, { passive: true });

  // Hover state for interactive elements
  const interactives = document.querySelectorAll('a, button, input, .card, .phone');
  interactives.forEach(el => {
    el.addEventListener('mouseenter', () => outer.classList.add('hovering'));
    el.addEventListener('mouseleave', () => outer.classList.remove('hovering'));
  });

  function renderCursor() {
    outerX += (mouseX - outerX) * 0.18;
    outerY += (mouseY - outerY) * 0.18;
    outer.style.left = `${outerX}px`;
    outer.style.top = `${outerY}px`;
    requestAnimationFrame(renderCursor);
  }
  requestAnimationFrame(renderCursor);
}

/* ==========================================================================
   3. AMBIENT PARTICLE FORCE-FIELD (Soda style)
   ========================================================================== */
function initAmbientCanvas() {
  const canvas = document.getElementById('ambient-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  let width = (canvas.width = window.innerWidth);
  let height = (canvas.height = window.innerHeight);

  window.addEventListener('resize', () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  }, { passive: true });

  let mouse = { x: -1000, y: -1000 };
  window.addEventListener('mousemove', (e) => {
    mouse.x = e.clientX;
    mouse.y = e.clientY;
  }, { passive: true });

  // Particle cloud with force-field deflection
  const count = 42;
  const particles = [];
  for (let i = 0; i < count; i++) {
    particles.push({
      x: Math.random() * width,
      y: Math.random() * height,
      baseX: Math.random() * width,
      baseY: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.4,
      vy: (Math.random() - 0.5) * 0.4,
      radius: Math.random() * 2 + 1,
      alpha: Math.random() * 0.35 + 0.1,
      phase: Math.random() * Math.PI * 2
    });
  }

  let time = 0;
  function renderParticles() {
    time += 0.015;
    ctx.clearRect(0, 0, width, height);

    particles.forEach(p => {
      // Gentle natural drift
      p.x += p.vx + Math.sin(time + p.phase) * 0.25;
      p.y += p.vy + Math.cos(time + p.phase) * 0.25;

      // Wrap around bounds
      if (p.x < 0) p.x = width;
      if (p.x > width) p.x = 0;
      if (p.y < 0) p.y = height;
      if (p.y > height) p.y = 0;

      // Soda-style pointer repulsion force-field
      const dx = mouse.x - p.x;
      const dy = mouse.y - p.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const repulseRadius = 180;

      if (dist < repulseRadius && dist > 0) {
        const force = (repulseRadius - dist) / repulseRadius;
        p.x -= (dx / dist) * force * 5;
        p.y -= (dy / dist) * force * 5;
      }

      // Draw particle
      ctx.beginPath();
      ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(0, 245, 255, ${p.alpha})`;
      ctx.fill();
    });

    requestAnimationFrame(renderParticles);
  }
  requestAnimationFrame(renderParticles);
}

/* ==========================================================================
   4. LIVE LOCAL CLOCK (Lumora style)
   ========================================================================== */
function initLiveClock() {
  const clockVal = document.getElementById('clock-val');
  if (!clockVal) return;

  function updateClock() {
    const now = new Date();
    let hours = now.getHours();
    const minutes = String(now.getMinutes()).padStart(2, '0');
    const meridiem = hours >= 12 ? 'pm' : 'am';
    hours = hours % 12 || 12;
    clockVal.textContent = `${hours}:${minutes}${meridiem}`;
  }

  updateClock();
  setInterval(updateClock, 1000);
}

/* ==========================================================================
   5. INTERACTIVE DUAL PLAY SPLIT CROSSFADER
   ========================================================================== */
function initDualPlayConsole() {
  const slider = document.getElementById('crossfade-slider');
  const readout = document.getElementById('fader-readout');
  const deckA = document.getElementById('deck-a');
  const deckB = document.getElementById('deck-b');
  const meterA = document.getElementById('meter-a');
  const meterB = document.getElementById('meter-b');

  if (!slider || !readout) return;

  slider.addEventListener('input', (e) => {
    const val = parseInt(e.target.value, 10);
    const pctA = 100 - val;
    const pctB = val;

    readout.textContent = `CHANNEL A: ${pctA}% | CHANNEL B: ${pctB}%`;
    if (meterA) meterA.style.width = `${Math.max(6, pctA)}%`;
    if (meterB) meterB.style.width = `${Math.max(6, pctB)}%`;

    if (val < 40) {
      deckA?.classList.add('active');
      deckB?.classList.remove('active');
    } else if (val > 60) {
      deckB?.classList.add('active');
      deckA?.classList.remove('active');
    } else {
      deckA?.classList.add('active');
      deckB?.classList.add('active');
    }
  });
}

/* ==========================================================================
   6. INTERACTIVE BITRATE ANALYZER
   ========================================================================== */
function initBitrateSwitcher() {
  const buttons = document.querySelectorAll('.bitrate-btn');
  const infoEl = document.getElementById('bitrate-info');
  if (!buttons.length || !infoEl) return;

  const configs = {
    '128': {
      sample: 'SAMPLING: 44,100 Hz / 16-BIT (CHOKED 16kHz)',
      range: 'DYNAMIC RANGE: LOSSY MP3 COMPRESSION ARTIFACTS'
    },
    '320': {
      sample: 'SAMPLING: 44,100 Hz / 16-BIT (STANDARD AAC)',
      range: 'DYNAMIC RANGE: COMPRESSED STREAMING CEILING'
    },
    '1411': {
      sample: 'SAMPLING: 96,000 Hz / 24-BIT (STUDIO MASTER)',
      range: 'DYNAMIC RANGE: FULL HARMONIC FREQUENCY (> 120 dB)'
    }
  };

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      buttons.forEach(b => b.classList.remove('on'));
      btn.classList.add('on');

      const rate = btn.getAttribute('data-rate') || '1411';
      const c = configs[rate];
      if (c) {
        infoEl.innerHTML = `<span>${c.sample}</span><strong>${c.range}</strong>`;
      }
    });
  });
}

/* ==========================================================================
   7. 3D GYROSCOPIC PHONE TILT
   ========================================================================== */
function initPhoneTilt() {
  const tilt = document.getElementById('tilt');
  if (!tilt || !window.matchMedia('(hover: hover)').matches) return;

  window.addEventListener('mousemove', (e) => {
    const x = (e.clientX / window.innerWidth - 0.5) * 2;
    const y = (e.clientY / window.innerHeight - 0.5) * 2;
    tilt.style.transform = `perspective(1000px) rotateY(${x * 10}deg) rotateX(${-y * 8}deg)`;
  }, { passive: true });
}

/* ==========================================================================
   8. SCREEN SHOWCASE TABS
   ========================================================================== */
function initShowcaseTabs() {
  const stage = document.getElementById('stage');
  const tabs = document.querySelectorAll('.tab');
  if (!stage || !tabs.length) return;

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      if (tab.classList.contains('on')) return;
      tabs.forEach(t => t.classList.remove('on'));
      tab.classList.add('on');

      stage.style.opacity = '0.2';
      stage.style.transform = 'scale(0.98)';
      setTimeout(() => {
        stage.src = `assets/shots/${tab.dataset.img}.webp`;
        stage.onload = () => {
          stage.style.opacity = '1';
          stage.style.transform = 'scale(1)';
        };
      }, 150);
    });
  });
}

/* ==========================================================================
   9. SCROLL EFFECTS & STATS COUNT-UP
   ========================================================================== */
function initScrollEffects() {
  const nav = document.getElementById('nav');
  const dots = document.querySelectorAll('.grid-dot');

  window.addEventListener('scroll', () => {
    const scrollY = window.scrollY;
    if (nav) nav.classList.toggle('s', scrollY > 20);

    // Laocoön style drifting grid dots
    dots.forEach((dot, i) => {
      const offset = (scrollY * (0.05 + i * 0.02)) % 80;
      dot.style.top = `${20 + offset}%`;
    });
  }, { passive: true });

  // Scroll Reveal Observer
  const io = new IntersectionObserver((entries) => {
    entries.forEach(e => {
      if (e.isIntersecting) {
        e.target.classList.add('in');

        // Lumora scroll count-up animation
        const counters = e.target.querySelectorAll('.counter');
        counters.forEach(c => {
          const target = parseInt(c.getAttribute('data-target'), 10);
          if (isNaN(target) || c.classList.contains('counted')) return;
          c.classList.add('counted');

          let current = 0;
          const step = Math.max(1, Math.floor(target / 40));
          const timer = setInterval(() => {
            current += step;
            if (current >= target) {
              c.textContent = target;
              clearInterval(timer);
            } else {
              c.textContent = current;
            }
          }, 25);
        });

        io.unobserve(e.target);
      }
    });
  }, { threshold: 0.12 });

  document.querySelectorAll('.rv').forEach((el, i) => {
    el.style.transitionDelay = `${(i % 3) * 60}ms`;
    io.observe(el);
  });
}

/* ==========================================================================
   10. GITHUB STARS SYNC
   ========================================================================== */
function fetchGitHubStars() {
  const starsEl = document.getElementById('stars');
  if (!starsEl) return;

  fetch('https://api.github.com/repos/aswadhkrishna22-prog/OzonePlayer')
    .then(res => res.ok ? res.json() : null)
    .then(data => {
      if (data && typeof data.stargazers_count === 'number') {
        starsEl.textContent = `★ ${data.stargazers_count}`;
      }
    })
    .catch(() => {});
}
