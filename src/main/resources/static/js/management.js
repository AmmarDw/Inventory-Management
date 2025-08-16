// management.js - Shared functionality for all management pages

// Modal management
const modals = [];
let currentModal = null;

function registerModal(modalId) {
    const modal = document.getElementById(modalId);
    if (modal) {
        modals.push(modal);
        modal.querySelector('.close-modal').addEventListener('click', closeAllModals);
    }
    return modal;
}

function openModal(modal) {
    modal.style.display = 'flex';
    currentModal = modal;
}

function closeAllModals() {
    modals.forEach(modal => modal.style.display = 'none');
    currentModal = null;
}

// Close modals when clicking outside
window.addEventListener('click', (event) => {
    if (event.target.classList.contains('modal')) {
        closeAllModals();
    }
});

// CSRF token handling
function getCSRFToken() {
    return {
        token: document.querySelector('meta[name="_csrf"]').content,
        header: document.querySelector('meta[name="_csrf_header"]').content
    };
}

// Error display
function displayFormErrors(errors, errorContainerIds) {
    // Reset errors
    Object.values(errorContainerIds).forEach(id => {
        const el = document.getElementById(id);
        if (el) el.textContent = '';
    });
    
    // Display new errors
    Object.entries(errors).forEach(([field, message]) => {
        const containerId = errorContainerIds[field];
        if (containerId) {
            const container = document.getElementById(containerId);
            if (container) container.textContent = message;
        }
    });
}

// Initialize modals
document.addEventListener('DOMContentLoaded', function() {
    // Register common modals
    registerModal('viewModal');
    registerModal('deleteModal');
    const filterModal = registerModal('filterModal');

    // Open Filter Modal
    document.getElementById('filterBtn').addEventListener('click', () => {
      openModal(filterModal);
    });
    
    // Close modals when clicking the X button
    document.querySelectorAll('.close-modal').forEach(button => {
        button.addEventListener('click', closeAllModals);
    });
});