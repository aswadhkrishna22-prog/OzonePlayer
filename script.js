/**
 * OZONE PLAYER // AURAL ARCHITECTURE INTERACTION ENGINE
 * Zero-dependency, 60fps canvas wave simulation, 3D tilt, and audio consoles
 */

document.addEventListener('DOMContentLoaded', () => {
  initAudioMatrixCanvas();
  initDevice3DTilt();
  initDualPlayCrossfader();
  initLosslessSpectrumAnalyzer();
  initShowcaseTabs();
  initBentoSpotlight();
  initNavbarScroll();
  fetchGitHubStars();
});

/* ==========================================================================
   1. INTERACTIVE HARMONIC SOUNDWAVE CANVAS (BACKGROUND)
   ========================================================================== */
function initAudioMatrixCanvas() {
  const canvas = document.getElementById('canvas-matrix');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  let width = (canvas.width = window.innerWidth);
  let height = (canvas.height = window.innerHeight);

  let mouseX = width * 0.5;
  let mouseY = height * 0.3;
  let targetMouseX = mouseX;
  let targetMouseY = mouseY;

  window.addEventListener('resize', () => {
    width = canvas.width = window.innerWidth;
    height = canvas.height = window.innerHeight;
  }, { passive: true });

  window.addEventListener('mousemove', (e) => {
    targetMouseX = e.clientX;
    targetMouseY = e.clientY;
  }, { passive: true });

  window.addEventListener('touchmove', (e) => {
    if (e.touches.length > 0) {
      targetMouseX = e.touches[0].clientX;
      targetMouseY = e.touches[0].clientY;
    }
  }, { passive: true });

  let time = 0;
  const waves = [
    { freq: 0.002, speed: 0.015, amp: 45, color: 'rgba(0, 245, 255, 0.06)' },
    { freq: 0.003, speed: 0.022, amp: 30, color: 'rgba(99, 102, 241, 0.05)' },
    { freq: 0.0015, speed: 0.012, amp: 60, color: 'rgba(0, 245, 255, 0.04)' }
  ];

  function render() {
    time += 1;
    // Smooth mouse lerp
    mouseX += (targetMouseX - mouseX) * 0.05;
    mouseY += (targetMouseY - mouseY) * 0.05;

    ctx.clearRect(0, 0, width, height);

    // Draw harmonic audio waves
    waves.forEach((w) => {
      ctx.beginPath();
      ctx.moveTo(0, height * 0.5);

      for (let x = 0; x <= width; x += 15) {
        // Distance influence from mouse pointer
        const dx = x - mouseX;
        const dy = height * 0.5 - mouseY;
        const dist = Math.sqrt(dx * dx + dy * dy);
        const mouseWave = Math.sin(dist * 0.01 - time * 0.05) * Math.max(0, 1 - dist / 600) * 40;

        const y =
          height * 0.5 +
          Math.sin(x * w.freq + time * w.speed) * w.amp +
          Math.cos(x * w.freq * 0.5 - time * w.speed * 0.8) * (w.amp * 0.5) +
          mouseWave;

        ctx.lineTo(x, y);
      }

      ctx.lineTo(width, height);
      ctx.lineTo(0, height);
      ctx.closePath();

      ctx.fillStyle = w.color;
      ctx.fill();
    });

    requestAnimationFrame(render);
  }

  requestAnimationFrame(render);
}

/* ==========================================================================
   2. 3D GYROSCOPIC TILT FOR PHONE RIG
   ========================================================================== */
function initDevice3DTilt() {
  const rig = document.getElementById('device-rig');
  const hud = document.getElementById('hud-card');
  if (!rig) return;

  let currentX = 0;
  let currentY = 0;
  let targetX = 0;
  let targetY = 0;

  window.addEventListener('mousemove', (e) => {
    const { innerWidth, innerHeight } = window;
    const normX = (e.clientX / innerWidth - 0.5) * 2;
    const normY = (e.clientY / innerHeight - 0.5) * 2;

    targetX = -normY * 12; // tilt degrees
    targetY = normX * 14;
  }, { passive: true });

  function updateTilt() {
    currentX += (targetX - currentX) * 0.08;
    currentY += (targetY - currentY) * 0.08;

    rig.style.transform = `rotateX(${currentX}deg) rotateY(${currentY}deg)`;
    if (hud) {
      hud.style.transform = `translateZ(45px) translateX(${currentY * 0.6}px)`;
    }

    requestAnimationFrame(updateTilt);
  }

  requestAnimationFrame(updateTilt);
}

/* ==========================================================================
   3. INTERACTIVE DUAL PLAY SPLIT CROSSFADER
   ========================================================================== */
function initDualPlayCrossfader() {
  const slider = document.getElementById('crossfader-input');
  const readout = document.getElementById('crossfader-readout');
  const deckACard = document.getElementById('deck-a-card');
  const deckBCard = document.getElementById('deck-b-card');

  if (!slider || !readout) return;

  const meterA = deckACard ? deckACard.querySelector('.deck-meter-bar') : null;
  const meterB = deckBCard ? deckBCard.querySelector('.deck-meter-bar') : null;

  slider.addEventListener('input', (e) => {
    const val = parseInt(e.target.value, 10); // 0 to 100
    const pctA = 100 - val;
    const pctB = val;

    readout.textContent = `DECK A: ${pctA}% | DECK B: ${pctB}%`;

    if (meterA) meterA.style.width = `${Math.max(8, pctA)}%`;
    if (meterB) meterB.style.width = `${Math.max(8, pctB)}%`;

    if (val < 40) {
      deckACard?.classList.add('active');
      deckBCard?.classList.remove('active');
    } else if (val > 60) {
      deckBCard?.classList.add('active');
      deckACard?.classList.remove('active');
    } else {
      deckACard?.classList.add('active');
      deckBCard?.classList.add('active');
    }
  });
}

/* ==========================================================================
   4. BIT-PERFECT LOSSLESS FREQUENCY ANALYZER
   ========================================================================== */
function initLosslessSpectrumAnalyzer() {
  const canvas = document.getElementById('spectrum-canvas');
  const tabs = document.querySelectorAll('.analyzer-tab');
  const sampleEl = document.getElementById('spec-sample');
  const freqEl = document.getElementById('spec-freq');
  const rangeEl = document.getElementById('spec-range');

  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  if (!ctx) return;

  let currentMode = '1411';
  let animFrame = 0;

  const modeConfigs = {
    '128': {
      sample: '44,100 Hz (16-bit)',
      freq: '20 Hz – 15,800 Hz (Choked)',
      range: '78 dB (Lossy MP3 Compression)',
      cutoffRatio: 0.48,
      accentColor: '#ef4444'
    },
    '320': {
      sample: '44,100 Hz (16-bit)',
      freq: '20 Hz – 19,200 Hz (Standard)',
      range: '96 dB (Compressed High Quality)',
      cutoffRatio: 0.68,
      accentColor: '#f59e0b'
    },
    '1411': {
      sample: '96,000 Hz (24-bit Studio Master)',
      freq: '20 Hz – 48,000 Hz (Full Uncapped)',
      range: '> 120 dB (Pure Dynamic Range)',
      cutoffRatio: 1.0,
      accentColor: '#00f5ff'
    }
  };

  tabs.forEach(tab => {
    tab.addEventListener('click', () => {
      tabs.forEach(t => t.classList.remove('active'));
      tab.classList.add('active');

      currentMode = tab.getAttribute('data-mode') || '1411';
      const config = modeConfigs[currentMode];
      if (config && sampleEl && freqEl && rangeEl) {
        sampleEl.textContent = config.sample;
        freqEl.textContent = config.freq;
        rangeEl.textContent = config.range;
      }
    });
  });

  function drawSpectrum() {
    animFrame += 1;
    const width = canvas.width;
    const height = canvas.height;
    ctx.clearRect(0, 0, width, height);

    const config = modeConfigs[currentMode];
    const bars = 56;
    const barWidth = (width / bars) - 3;
    const cutoffBar = Math.floor(bars * config.cutoffRatio);

    for (let i = 0; i < bars; i++) {
      const x = i * (barWidth + 3);
      let barHeight;

      if (i > cutoffBar) {
        // Frequency cutoff zone for lossy formats
        barHeight = Math.random() * 4 + 2;
        ctx.fillStyle = 'rgba(255, 255, 255, 0.05)';
      } else {
        // Active harmonic frequency oscillation
        const base = Math.sin((i / bars) * Math.PI + animFrame * 0.08) * 0.5 + 0.5;
        const jitter = Math.sin(i * 1.5 + animFrame * 0.12) * 20;
        barHeight = Math.max(10, base * (height - 40) + jitter);

        if (currentMode === '1411') {
          const grad = ctx.createLinearGradient(0, height, 0, 0);
          grad.addColorStop(0, '#6366f1');
          grad.addColorStop(1, '#00f5ff');
          ctx.fillStyle = grad;
        } else {
          ctx.fillStyle = config.accentColor;
        }
      }

      ctx.fillRect(x, height - barHeight, barWidth, barHeight);
    }

    // Draw baseline
    ctx.strokeStyle = 'rgba(255, 255, 255, 0.1)';
    ctx.beginPath();
    ctx.moveTo(0, height - 1);
    ctx.lineTo(width, height - 1);
    ctx.stroke();

    requestAnimationFrame(drawSpectrum);
  }

  requestAnimationFrame(drawSpectrum);
}

/* ==========================================================================
   5. SHOWCASE SCREEN TAB SWITCHER
   ========================================================================== */
function initShowcaseTabs() {
  const buttons = document.querySelectorAll('.showcase-item-btn');
  const img = document.getElementById('showcase-img');
  if (!buttons.length || !img) return;

  buttons.forEach(btn => {
    btn.addEventListener('click', () => {
      if (btn.classList.contains('active')) return;

      buttons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const newSrc = btn.getAttribute('data-img');
      if (!newSrc) return;

      img.style.opacity = '0.2';
      img.style.transform = 'scale(0.98)';

      setTimeout(() => {
        img.src = newSrc;
        img.onload = () => {
          img.style.opacity = '1';
          img.style.transform = 'scale(1)';
        };
      }, 150);
    });
  });
}

/* ==========================================================================
   6. BENTO CARDS SPOTLIGHT EFFECT
   ========================================================================== */
function initBentoSpotlight() {
  const cells = document.querySelectorAll('[data-spotlight]');
  cells.forEach(cell => {
    cell.addEventListener('mousemove', (e) => {
      const rect = cell.getBoundingClientRect();
      const x = e.clientX - rect.left;
      const y = e.clientY - rect.top;
      cell.style.setProperty('--mouse-x', `${x}px`);
      cell.style.setProperty('--mouse-y', `${y}px`);
    });
  });
}

/* ==========================================================================
   7. NAVBAR SCROLL GLASS BEHAVIOR
   ========================================================================== */
function initNavbarScroll() {
  const masthead = document.querySelector('.masthead');
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
   8. REAL-TIME GITHUB STARS
   ========================================================================== */
async function fetchGitHubStars() {
  const starCountEl = document.getElementById('github-star-count');
  if (!starCountEl) return;

  try {
    const res = await fetch('https://api.github.com/repos/aswadhkrishna22-prog/OzonePlayer');
    if (res.ok) {
      const data = await res.json();
      if (typeof data.stargazers_count === 'number') {
        starCountEl.textContent = `★ ${data.stargazers_count}`;
      }
    }
  } catch (err) {
    console.debug('GitHub stars offline:', err);
  }
}
