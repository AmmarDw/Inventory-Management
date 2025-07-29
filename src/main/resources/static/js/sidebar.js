// sidebar.js

window.addEventListener('DOMContentLoaded', () => {
  const sidebar = document.querySelector('.sidebar');
  const toggleBtn = document.querySelector('.sidebar-toggle');
  const overlay = document.querySelector('.sidebar-overlay');
  const mainContent = document.querySelector('.main-content');

  // Toggle sidebar collapsed state
  if (toggleBtn) {
    toggleBtn.addEventListener('click', () => {
      const isMobile = window.innerWidth <= 767;

      if (sidebar) sidebar.classList.toggle('collapsed');
      if (isMobile) {
        if (overlay) overlay.classList.toggle('active');
      } else {
        const mainContent = document.querySelector('.main-content');
        if (mainContent) mainContent.classList.toggle('collapsed');
      }
    });
  }

  // Dropdown toggles
  document.querySelectorAll('.group-title').forEach(el => {
    el.addEventListener('click', () => {
      const parent = el.closest('.expandable');
      const arrow = el.querySelector('.dropdown-arrow');
      const submenu = parent.querySelector('.sub-links');

      submenu.classList.toggle('active');
      arrow.classList.toggle('rotated');
    });
  });
});
