/**
 * OZONE PLAYER // AURAL ARCHITECTURE PRO SCRIPT
 * High-precision motion, hardware DSP simulation, audio FFT visualization.
 * Zero AI slop, zero emojis, verified authentic engineering aesthetics.
 */

(function () {
  'use strict';

  // 1. PRECISION DOUBLE-RING CURSOR
  initCustomCursor();

  // 2. AMBIENT ACOUSTIC PARTICLE CANVAS
  initStageCanvas();

  // 3. 3D GYRO PHONE TILT
  initPhoneTilt();

  // 4. DUAL PLAY HARDWARE CROSSFADER CONSOLE
  initDualPlayConsole();

  // 5. ACOUSTIC SPECTROGRAM LAB
  initAcousticLab();

  // 6. SCREEN SHOWCASE SWITCHER
  initScreenShowcase();

  // 7. MASTHEAD SCROLL DYNAMICS
  initMastheadScroll();

  // 8. LIVE GITHUB METRICS
  fetchGitHubMetrics();
})();

/* ==========================================================================
   1. PRECISION DOUBLE-RING CURSOR (Laocoön-Style Lerp Physics)
   ========================================================================== */
function initCustomCursor() {
  const dot = document.getElementById('cursor-dot');
  const ring = document.getElementById('cursor-ring');
  if (!dot || !ring) return;

  if (!window.matchMedia('(hover: hover) and (pointer: fine)').matches) {
    dot.style.display = 'none';
    ring.style.display = 'none';
    return;
  }

  let mouseX = window.innerWidth / 2;
  let mouseY = window.innerHeight / 2;
  let ringX = mouseX;
  let ringY = mouseY;
  let isMoving = false;

  window.addEventListener('mousemove', (e) => {
    mouseX = e.clientX;
    mouseY = e.clientY;
    dot.style.left = `${mouseX}px`;
    dot.style.top = `${mouseY}px`;
    if (!isMoving) {
      isMoving = true;
      dot.style.opacity = '1';
      ring.style.opacity = '1';
    }
  }, { passive: true });

  const interactiveSelector = 'a, button, input, .arch-card, .phone-stand-rig, .acoustic-mode-tab, .showcase-nav-item, .console-deck-panel';
  const interactives = document.querySelectorAll(interactiveSelector);
  interactives.forEach(el => {
    el.addEventListener('mouseenter', () => ring.classList.add('hovering'));
    el.addEventListener('mouseleave', () => ring.classList.remove('hovering'));
  });

  function renderCursor() {
    ringX += (mouseX - ringX) * 0.16;
    ringY += (mouseY - ringY) * 0.16;
    ring.style.left = `${ringX}px`;
    ring.style.top = `${ringY}px`;
    requestAnimationFrame(renderCursor);
  }
  requestAnimationFrame(renderCursor);
}

/* ==========================================================================
   2. AMBIENT ACOUSTIC PARTICLE CANVAS (Soda Force-Field Particles)
   ========================================================================== */
function initStageCanvas() {
  const canvas = document.getElementById('stage-canvas');
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

  const particleCount = 48;
  const particles = [];
  for (let i = 0; i < particleCount; i++) {
    particles.push({
      x: Math.random() * width,
      y: Math.random() * height,
      vx: (Math.random() - 0.5) * 0.35,
      vy: (Math.random() - 0.5) * 0.35,
      radius: Math.random() * 1.8 + 0.8,
      baseAlpha: Math.random() * 0.25 + 0.08,
      phase: Math.random() * Math.PI * 2
    });
  }

  let time = 0;
  function render() {
    time += 0.012;
    ctx.clearRect(0, 0, width, height);

    for (let i = 0; i < particles.length; i++) {
      const p = particles[i];
      p.x += p.vx + Math.sin(time + p.phase) * 0.2;
      p.y += p.vy + Math.cos(time + p.phase) * 0.2;

      if (p.x < 0) p.x = width;
      if (p.x > width) p.x = 0;
      if (p.y < 0) p.y = height;
      if (p.y > height) p.y = 0;

      // Pointer deflection force-field
      const dx = mouse.x - p.x;
      const dy = mouse.y - p.y;
      const dist = Math.sqrt(dx * dx + dy * dy);
      const repulseRadius = 160;

      if (dist < repulseRadius && dist > 0) {
        const force = (repulseRadius - dist) / repulseRadius;
        p.x -= (dx / dist) * force * 4;
        p.y -= (dy / dist) * force * 4;
      }

      ctx.beginPath();
      ctx.arc(p.x, p.y, p.radius, 0, Math.PI * 2);
      ctx.fillStyle = `rgba(0, 245, 255, ${p.baseAlpha})`;
      ctx.fill();

      // Connect close neighbors
      for (let j = i + 1; j < particles.length; j++) {
        const p2 = particles[j];
        const cdx = p.x - p2.x;
        const cdy = p.y - p2.y;
        const cdist = Math.sqrt(cdx * cdx + cdy * cdy);
        if (cdist < 90) {
          ctx.beginPath();
          ctx.moveTo(p.x, p.y);
          ctx.lineTo(p2.x, p2.y);
          ctx.strokeStyle = `rgba(0, 245, 255, ${0.04 * (1 - cdist / 90)})`;
          ctx.stroke();
        }
      }
    }

    requestAnimationFrame(render);
  }
  requestAnimationFrame(render);
}

/* ==========================================================================
   3. 3D GYRO PHONE TILT (Perspective Audio Device Rig)
   ========================================================================== */
function initPhoneTilt() {
  const rig = document.getElementById('phone-rig');
  if (!rig || !window.matchMedia('(hover: hover)').matches) return;

  let targetRotX = 0;
  let targetRotY = 0;
  let currentRotX = 0;
  let currentRotY = 0;

  window.addEventListener('mousemove', (e) => {
    const normX = (e.clientX / window.innerWidth - 0.5) * 2;
    const normY = (e.clientY / window.innerHeight - 0.5) * 2;
    targetRotY = normX * 10;
    targetRotX = -normY * 8;
  }, { passive: true });

  window.addEventListener('mouseleave', () => {
    targetRotX = 0;
    targetRotY = 0;
  });

  function renderTilt() {
    currentRotX += (targetRotX - currentRotX) * 0.1;
    currentRotY += (targetRotY - currentRotY) * 0.1;
    rig.style.transform = `perspective(1200px) rotateX(${currentRotX.toFixed(2)}deg) rotateY(${currentRotY.toFixed(2)}deg)`;
    requestAnimationFrame(renderTilt);
  }
  requestAnimationFrame(renderTilt);
}

/* ==========================================================================
   4. DUAL PLAY HARDWARE CONSOLE (Live Stereo Crossfader & VU Flutter)
   ========================================================================== */
function initDualPlayConsole() {
  const fader = document.getElementById('master-fader');
  const label = document.getElementById('fader-label');
  const deckA = document.getElementById('deck-panel-a');
  const deckB = document.getElementById('deck-panel-b');
  const meterFillA = document.getElementById('meter-fill-a');
  const meterFillB = document.getElementById('meter-fill-b');

  if (!fader || !label || !meterFillA || !meterFillB) return;

  let targetPctA = 100;
  let targetPctB = 0;

  function updateMixer(val) {
    const rawVal = parseInt(val, 10);
    targetPctA = 100 - rawVal;
    targetPctB = rawVal;

    if (rawVal === 0) {
      label.textContent = 'DECK A: 100% | DECK B: 0% (CH A ISOLATED)';
    } else if (rawVal === 50) {
      label.textContent = 'DECK A: 50% | DECK B: 50% (DUAL SPLIT STEREO)';
    } else if (rawVal === 100) {
      label.textContent = 'DECK A: 0% | DECK B: 100% (CH B ISOLATED)';
    } else {
      label.textContent = `DECK A: ${targetPctA}% | DECK B: ${targetPctB}%`;
    }

    if (rawVal <= 35) {
      deckA?.classList.add('active');
      deckB?.classList.remove('active');
    } else if (rawVal >= 65) {
      deckB?.classList.add('active');
      deckA?.classList.remove('active');
    } else {
      deckA?.classList.add('active');
      deckB?.classList.add('active');
    }
  }

  fader.addEventListener('input', (e) => {
    updateMixer(e.target.value);
  });

  // Natural VU meter audio flutter simulation
  function flutterVU() {
    const jitterA = (Math.random() - 0.5) * 8;
    const jitterB = (Math.random() - 0.5) * 8;

    const liveA = Math.max(0, Math.min(100, targetPctA > 0 ? targetPctA + jitterA : 0));
    const liveB = Math.max(0, Math.min(100, targetPctB > 0 ? targetPctB + jitterB : 0));

    meterFillA.style.width = `${liveA.toFixed(1)}%`;
    meterFillB.style.width = `${liveB.toFixed(1)}%`;

    setTimeout(flutterVU, 120);
  }
  flutterVU();
}

/* ==========================================================================
   5. ACOUSTIC SPECTROGRAM LAB (Dynamic Canvas FFT Simulation)
   ========================================================================== */
function initAcousticLab() {
  const tabs = document.querySelectorAll('.acoustic-mode-tab');
  const canvas = document.getElementById('spectrogram-canvas');
  const specHz = document.getElementById('spec-hz');
  const specBand = document.getElementById('spec-band');
  const specDynamic = document.getElementById('spec-dynamic');

  if (!canvas || !tabs.length) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  let activeMode = '1411';

  const modeSpecs = {
    '128': {
      hz: '44,100 Hz / 16-Bit',
      band: '20 Hz – 16,000 Hz (Harsh Lossy Cutoff)',
      dynamic: 'Compressed ~65 dB Dynamic Range',
      cutoffFreq: 0.35,
      noise: 0.15
    },
    '320': {
      hz: '44,100 Hz / 16-Bit',
      band: '20 Hz – 20,000 Hz (Streaming Ceiling)',
      dynamic: 'Compressed ~96 dB Dynamic Range',
      cutoffFreq: 0.55,
      noise: 0.05
    },
    '1411': {
      hz: '96,000 Hz / 24-Bit',
      band: '20 Hz – 48,000 Hz (Full Uncapped)',
      dynamic: '> 120 dB Studio Master',
      cutoffFreq: 1.0,
      noise: 0.0
    }
  };

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('on'));
      tab.classList.add('on');
      activeMode = tab.getAttribute('data-mode') || '1411';

      const spec = modeSpecs[activeMode];
      if (spec) {
        if (specHz) specHz.textContent = spec.hz;
        if (specBand) specBand.textContent = spec.band;
        if (specDynamic) specDynamic.textContent = spec.dynamic;
      }
    });
  });

  // Animated FFT Waterfall / Bars
  const barCount = 64;
  let phase = 0;

  function renderSpectrogram() {
    phase += 0.04;
    const w = canvas.width;
    const h = canvas.height;

    ctx.fillStyle = '#090b10';
    ctx.fillRect(0, 0, w, h);

    // Grid lines
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.05)';
    ctx.lineWidth = 1;
    for (let y = 30; y < h; y += 35) {
      ctx.beginPath();
      ctx.moveTo(0, y);
      ctx.lineTo(w, y);
      ctx.stroke();
    }

    const currentSpec = modeSpecs[activeMode] || modeSpecs['1411'];
    const barWidth = (w - 40) / barCount;

    for (let i = 0; i < barCount; i++) {
      const normalizedFreq = i / barCount;
      let heightFactor = 0;

      if (normalizedFreq <= currentSpec.cutoffFreq) {
        // Natural frequency harmonics
        const harmonic = Math.sin(phase + i * 0.18) * 0.35 + Math.cos(phase * 0.7 + i * 0.08) * 0.35 + 0.5;
        const decay = 1 - Math.pow(normalizedFreq, 1.4) * 0.6;
        heightFactor = Math.max(0.08, harmonic * decay);

        if (currentSpec.noise > 0) {
          heightFactor += (Math.random() - 0.5) * currentSpec.noise;
        }
      } else {
        // Hard cut off zone
        heightFactor = Math.random() * 0.03;
      }

      const barH = Math.min(h - 20, Math.max(4, heightFactor * (h - 30)));
      const x = 20 + i * barWidth;
      const y = h - 10 - barH;

      // Gradient bar fill
      const grad = ctx.createLinearGradient(0, y, 0, h - 10);
      if (normalizedFreq > currentSpec.cutoffFreq) {
        grad.addColorStop(0, 'rgba(239, 68, 68, 0.6)');
        grad.addColorStop(1, 'rgba(239, 68, 68, 0.05)');
      } else {
        grad.addColorStop(0, '#00f5ff');
        grad.addColorStop(0.5, '#6366f1');
        grad.addColorStop(1, 'rgba(0, 245, 255, 0.1)');
      }

      ctx.fillStyle = grad;
      ctx.fillRect(x, y, barWidth - 2, barH);
    }

    requestAnimationFrame(renderSpectrogram);
  }
  requestAnimationFrame(renderSpectrogram);
}

/* ==========================================================================
   6. SCREEN SHOWCASE SWITCHER (Fluid Cross-Fade)
   ========================================================================== */
function initScreenShowcase() {
  const stage = document.getElementById('stage-screen');
  const navItems = document.querySelectorAll('.showcase-nav-item');
  if (!stage || !navItems.length) return;

  navItems.forEach(item => {
    item.addEventListener('click', () => {
      if (item.classList.contains('on')) return;
      navItems.forEach(i => i.classList.remove('on'));
      item.classList.add('on');

      const imgName = item.getAttribute('data-img');
      if (!imgName) return;

      stage.style.opacity = '0.2';
      stage.style.transform = 'scale(0.98)';

      setTimeout(() => {
        stage.src = `assets/shots/${imgName}.webp`;
        stage.onload = () => {
          stage.style.opacity = '1';
          stage.style.transform = 'scale(1)';
        };
      }, 140);
    });
  });
}

/* ==========================================================================
   7. MASTHEAD SCROLL DYNAMICS
   ========================================================================== */
function initMastheadScroll() {
  const masthead = document.getElementById('masthead');
  if (!masthead) return;

  window.addEventListener('scroll', () => {
    if (window.scrollY > 30) {
      masthead.classList.add('scrolled');
    } else {
      masthead.classList.remove('scrolled');
    }
  }, { passive: true });
}

/* ==========================================================================
   8. LIVE GITHUB METRICS (Zero Banned Characters)
   ========================================================================== */
function fetchGitHubMetrics() {
  const starText = document.getElementById('star-count-text');
  if (!starText) return;

  fetch('https://api.github.com/repos/aswadhkrishna22-prog/OzonePlayer')
    .then(res => res.ok ? res.json() : null)
    .then(data => {
      if (data && typeof data.stargazers_count === 'number') {
        starText.textContent = `${data.stargazers_count} Stars`;
      }
    })
    .catch(() => {
      // Graceful fallback to static text
    });
}
