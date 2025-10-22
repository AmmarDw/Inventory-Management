function createCircularProgressBar(container, percentage) {
    container.innerHTML = ''; // Clear previous content
    const sanitizedPercentage = Math.max(0, Math.min(100, percentage));
    
    const size = 120;
    const strokeWidth = 10;
    const radius = (size / 2) - (strokeWidth / 2);
    const circumference = 2 * Math.PI * radius;
    const offset = circumference - (sanitizedPercentage / 100) * circumference;

    let colorClass = 'green';
    if (sanitizedPercentage > 85) colorClass = 'red';
    else if (sanitizedPercentage > 74) colorClass = 'orange';
    else if (sanitizedPercentage > 60) colorClass = 'yellow';

    const svgHtml = `
        <svg class="circular-progress-svg" width="${size}" height="${size}">
            <circle class="progress-background" r="${radius}" cx="${size/2}" cy="${size/2}"></circle>
            <circle class="progress-circle ${colorClass}" r="${radius}" cx="${size/2}" cy="${size/2}"
                    stroke-dasharray="${circumference}"
                    stroke-dashoffset="${circumference}"></circle>
        </svg>
        <div class="progress-text">0%</div>
    `;
    container.classList.add('circular-progress-container');
    container.innerHTML = svgHtml;

    const circle = container.querySelector('.progress-circle');
    const text = container.querySelector('.progress-text');

    setTimeout(() => {
        circle.style.strokeDashoffset = offset;
        
        let start = 0;
        const duration = 1500;
        const stepTime = 20;
        const totalSteps = duration / stepTime;
        const increment = sanitizedPercentage / totalSteps;

        const timer = setInterval(() => {
            start += increment;
            if (start >= sanitizedPercentage) {
                start = sanitizedPercentage;
                clearInterval(timer);
            }
            text.textContent = `${start.toFixed(1)}%`;
        }, stepTime);
    }, 100);
}