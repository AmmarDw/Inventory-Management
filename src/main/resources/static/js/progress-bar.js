/**
 * Creates and animates a progress bar.
 * @param {HTMLElement} container - The DOM element to append the progress bar to.
 * @param {number} totalVolume - The used portion of the capacity.
 * @param {number} capacity - The total capacity.
 */
function createProgressBar(container, totalVolume, capacity) {
    // Clear any previous bar
    container.innerHTML = '';

    // Percentage calculation is now handled inside this function
    let filledPercentage = 0;
    if (capacity > 0 && totalVolume >= 0) {
        filledPercentage = (totalVolume / capacity) * 100;
    }

    // Sanitize the input percentage
    const percentage = Math.max(0, Math.min(100, filledPercentage));

    // Create the wrapper and fill elements
    const wrapper = document.createElement('div');
    wrapper.className = 'progress-bar-wrapper';

    const fill = document.createElement('div');
    fill.className = 'progress-bar-fill';

    const percentageText = document.createElement('span');
    fill.appendChild(percentageText);
    wrapper.appendChild(fill);
    container.appendChild(wrapper);

    // Determine color based on thresholds
    let colorClass = 'green';
    if (percentage > 85) {
        colorClass = 'red';
    } else if (percentage > 74) {
        colorClass = 'orange';
    } else if (percentage > 60) {
        colorClass = 'yellow';
    }
    
    // Animate the bar after a short delay to ensure it's in the DOM
    setTimeout(() => {
        fill.classList.add(colorClass);
        fill.style.width = `${percentage}%`;

        // Animate the percentage text
        let start = 0;
        const duration = 1500; // Match CSS transition duration
        const stepTime = 20;
        const totalSteps = duration / stepTime;
        const increment = percentage / totalSteps;

        const timer = setInterval(() => {
            start += increment;
            if (start >= percentage) {
                start = percentage;
                clearInterval(timer);
            }
            percentageText.textContent = `${Math.round(start)}%`;
        }, stepTime);

    }, 100); // 100ms delay
}