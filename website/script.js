/**
 * Ozone Player Official Website Scripts
 * Fast, lightweight vanilla JS for interactive UI and GitHub API integration
 */

document.addEventListener('DOMContentLoaded', () => {
  // 1. Fetch GitHub Stars
  fetchGitHubStars();

  // 2. Screenshot Showcase Tab Switcher
  initShowcaseTabs();

  // 3. FAQ Accordion
  initFaqAccordion();

  // 4. Navbar Scroll Glass Effect
  initNavbarScroll();
});

/**
 * Fetches real-time GitHub stars for aswadhkrishna22-prog/OzonePlayer
 */
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
    console.debug('GitHub API rate limited or offline:', err);
    // Keep default star character
  }
}

/**
 * Tab switching logic for the interactive device showcase
 */
function initShowcaseTabs() {
  const tabButtons = document.querySelectorAll('.tab-btn');
  const showcaseImg = document.getElementById('showcase-screen');

  if (!tabButtons.length || !showcaseImg) return;

  tabButtons.forEach(btn => {
    btn.addEventListener('click', () => {
      if (btn.classList.contains('active')) return;

      // Update button active states
      tabButtons.forEach(b => b.classList.remove('active'));
      btn.classList.add('active');

      const newSrc = btn.getAttribute('data-img');
      if (!newSrc) return;

      // Smooth fade transition
      showcaseImg.style.opacity = '0.3';
      showcaseImg.style.transform = 'scale(0.98)';

      setTimeout(() => {
        showcaseImg.src = newSrc;
        showcaseImg.onload = () => {
          showcaseImg.style.opacity = '1';
          showcaseImg.style.transform = 'scale(1)';
        };
      }, 150);
    });
  });
}

/**
 * Interactive FAQ Accordion
 */
function initFaqAccordion() {
  const faqQuestions = document.querySelectorAll('.faq-question');

  faqQuestions.forEach(btn => {
    btn.addEventListener('click', () => {
      const item = btn.closest('.faq-item');
      if (!item) return;

      const isActive = item.classList.contains('active');

      // Close all other items
      document.querySelectorAll('.faq-item').forEach(other => {
        if (other !== item) {
          other.classList.remove('active');
        }
      });

      // Toggle clicked item
      item.classList.toggle('active', !isActive);
    });
  });
}

/**
 * Navbar blur & border on scroll
 */
function initNavbarScroll() {
  const navbar = document.querySelector('.navbar');
  if (!navbar) return;

  window.addEventListener('scroll', () => {
    if (window.scrollY > 30) {
      navbar.classList.add('scrolled');
    } else {
      navbar.classList.remove('scrolled');
    }
  }, { passive: true });
}
