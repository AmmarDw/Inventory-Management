document.addEventListener('DOMContentLoaded', () => {

  console.log('DOM loaded, saw categoryOptionsMap:', categoryOptionsMap);

  function updateOptionSelect(catSel, optSel, newOptInp) {
    optSel.innerHTML = '<option value="" disabled selected>Select an option</option>';

    const catId = catSel.value;
    // coerce to numeric key if possible
    const key = isNaN(catId) ? catId : parseInt(catId, 10);
    const opts = categoryOptionsMap[key] || [];

    opts.forEach(o => {
      const opt = document.createElement('option');
      opt.value = o.optionId;
      opt.textContent = o.optionValue;
      optSel.appendChild(opt);
    });

    const newOpt = document.createElement('option');
    newOpt.value = 'new';
    newOpt.textContent = '+ New Option';
    optSel.appendChild(newOpt);

    newOptInp.style.display = 'none';
  }

  // wire up all existing category selects
  document.querySelectorAll('.category-select').forEach(sel => {
    sel.addEventListener('change', function() {
      const group    = this.closest('.option-group');
      const optSel   = group.querySelector('.option-select');
      const newCat   = group.querySelector('.new-category-input');
      const newOptIn = group.querySelector('.new-option-input');

      newCat.style.display = this.value === 'new' ? 'block' : 'none';
      updateOptionSelect(this, optSel, newOptIn);
    });
  });

  // show new‐option input when “+ New Option” is picked
  document.querySelectorAll('.option-select').forEach(sel => {
    sel.addEventListener('change', function() {
      const newOptIn = this.closest('.option-group')
                           .querySelector('.new-option-input');
      newOptIn.style.display = this.value === 'new' ? 'block' : 'none';
    });
  });

  // remove groups with minimum count check
  document.querySelectorAll('.remove-option-group').forEach(btn => {
    btn.addEventListener('click', () => {
      const container = document.getElementById('option-groups-container');
      const allGroups = container.querySelectorAll('.option-group');

      if (allGroups.length <= 1) {
        showError("You must select or add at least one product option.");
        return;
      }

      btn.closest('.option-group').remove();
    });
  });

  // Show frontend error using existing or newly created .error-message element
  function showError(message) {
    let errorBox = document.querySelector('.error-message');

    if (!errorBox) {
      errorBox = document.createElement('div');
      errorBox.className = 'error-message';

      // Insert in the same place as the existing one would be
      const form = document.querySelector('form');
      const priceGroup = form.querySelector('#price').closest('.form-group');
      form.insertBefore(errorBox, priceGroup.nextSibling);
    }

    errorBox.textContent = message;
    errorBox.style.display = 'block';
  }


  // clone new groups
  document.querySelector('.add-option-group')
          .addEventListener('click', () => {
    const container = document.getElementById('option-groups-container');
    const tpl       = container.querySelector('.option-group');
    const clone     = tpl.cloneNode(true);

    // reset values
    clone.querySelector('.category-select').value = '';
    clone.querySelector('.option-select').innerHTML =
      '<option value="" disabled selected>Select an option</option>';
    clone.querySelector('.new-category-input').style.display = 'none';
    clone.querySelector('.new-category-input input').value   = '';
    clone.querySelector('.new-option-input').style.display   = 'none';
    clone.querySelector('.new-option-input input').value     = '';

    // rebind events on clone
    clone.querySelector('.category-select')
         .addEventListener('change', function() {
      const grp = this.closest('.option-group');
      grp.querySelector('.new-category-input').style.display =
         this.value === 'new' ? 'block' : 'none';
      updateOptionSelect(
        this,
        grp.querySelector('.option-select'),
        grp.querySelector('.new-option-input')
      );
    });
    clone.querySelector('.option-select')
         .addEventListener('change', function() {
      this.closest('.option-group')
          .querySelector('.new-option-input')
          .style.display = this.value === 'new' ? 'block' : 'none';
    });
    clone.querySelector('.remove-option-group')
         .addEventListener('click', function() {
      this.closest('.option-group').remove();
    });

    container.appendChild(clone);
  });

});