document.addEventListener('DOMContentLoaded', () => {
  const container = document.getElementById('stock-groups-container');
  const form      = document.querySelector('form');
  const choicesMap = new WeakMap();
  
  // Store the original group HTML before any initialization occurs
  const originalGroup = container.querySelector('.stock-group');
  const originalGroupHTML = originalGroup.outerHTML;

  function showError(msg) {
    let err = form.querySelector('.error-message');
    if (!err) {
      err = document.createElement('div');
      err.className = 'error-message';
      form.append(err);
    }
    err.textContent = msg;
    err.style.display = 'block';
  }

  function initChoices(sel) {
    if (sel.dataset.choice === 'active') return;
    const instance = new Choices(sel, {
      searchEnabled: true,
      shouldSort:    false
    });
    choicesMap.set(sel, instance);
    sel.dataset.choice = 'active';
  }

  // Initialize existing product selects
  container.querySelectorAll('select.product-select').forEach(initChoices);

  // Removing groups
  container.addEventListener('click', e => {
    if (!e.target.matches('.remove-stock-group')) return;
    const groups = container.querySelectorAll('.stock-group');
    if (groups.length <= 1) {
      showError('You must load at least one product.');
      return;
    }
    const group = e.target.closest('.stock-group');
    const sel   = group.querySelector('select.product-select');
    if (choicesMap.has(sel)) {
      choicesMap.get(sel).destroy();
      choicesMap.delete(sel);
    }
    group.remove();
  });

  // Cloning groups
  document.querySelector('.add-stock-group').addEventListener('click', () => {
    // Create new group from original unmodified HTML
    const temp = document.createElement('div');
    temp.innerHTML = originalGroupHTML;
    const clone = temp.firstElementChild;

    // Reset form values
    clone.querySelector('input[type="number"]').value = '';
    const sel = clone.querySelector('select.product-select');
    sel.value = '';
    
    // Initialize Choices on the new select
    initChoices(sel);

    container.appendChild(clone);
  });
});


  // Filter dialogs (stubs—you’d hook up real modals)
  // document.querySelectorAll('.filter-btn').forEach(btn => {
  //   btn.addEventListener('click', () => {
  //     alert('Open filter popup here');
  //   });
  // });