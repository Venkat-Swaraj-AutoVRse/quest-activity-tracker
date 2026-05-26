// Global State
let currentRange = 'today';
let usageData = [];

// DOM Elements
const serverAddressEl = document.getElementById('server-address');
const totalTimeEl = document.getElementById('val-total-time');
const activeAppsEl = document.getElementById('val-active-apps');
const totalLaunchesEl = document.getElementById('val-total-launches');
const leaderboardListEl = document.getElementById('leaderboard-list');
const searchInput = document.getElementById('search-input');
const btnRefresh = document.getElementById('btn-refresh');
const chartCenterValueEl = document.getElementById('chart-center-value');
const svgChart = document.getElementById('svg-chart');
const chartLegend = document.getElementById('chart-legend');
const rangeButtons = document.querySelectorAll('.btn-range');

// Colors for the donut chart segments
const SEGMENT_COLORS = [
    '#8b5cf6', // Violet
    '#06b6d4', // Cyan
    '#ec4899', // Magenta
    '#10b981', // Emerald
    '#f59e0b', // Amber
    '#3b82f6', // Blue
    '#ef4444'  // Red
];

// Initialize UI
document.addEventListener('DOMContentLoaded', () => {
    // Set server address based on active host
    serverAddressEl.textContent = `http://${window.location.host || 'localhost:8080'}`;
    
    // Fetch initial data
    fetchData();

    // Range selector click handlers
    rangeButtons.forEach(btn => {
        btn.addEventListener('click', (e) => {
            rangeButtons.forEach(b => b.classList.remove('active'));
            btn.classList.add('active');
            currentRange = btn.dataset.range;
            fetchData();
        });
    });

    // Refresh button handler
    btnRefresh.addEventListener('click', fetchData);

    // Search input handler
    searchInput.addEventListener('input', filterAppList);

    // Export handlers
    document.getElementById('btn-export-csv').addEventListener('click', exportToCSV);
    document.getElementById('btn-export-json').addEventListener('click', exportToJSON);

    // Setup periodic polling (every 10 seconds)
    setInterval(fetchData, 10000);
});

// Fetch screen time statistics from server
async function fetchData() {
    try {
        const response = await fetch(`/api/usage?range=${currentRange}`);
        if (!response.ok) throw new Error('API server returned an error');
        
        usageData = await response.json();
        updateConnectionStatus(true);
        renderDashboard();
    } catch (e) {
        console.error('Error fetching usage data:', e);
        updateConnectionStatus(false);
    }
}

// Update UI connection indicator status
function updateConnectionStatus(isOnline) {
    const statusIndicator = document.querySelector('.status-indicator');
    if (isOnline) {
        statusIndicator.className = 'status-indicator online';
    } else {
        statusIndicator.className = 'status-indicator offline';
        leaderboardListEl.innerHTML = `
            <div class="empty-state">
                <span>⚠️</span>
                <p>Cannot connect to the Quest Headset. Make sure it is on and the app is running.</p>
            </div>
        `;
    }
}

// Render all components of the dashboard
function renderDashboard() {
    if (usageData.length === 0) {
        renderEmptyState();
        return;
    }

    // 1. Calculate Aggregated Metrics
    let totalMs = 0;
    let totalLaunches = 0;
    usageData.forEach(app => {
        totalMs += app.totalTimeInForeground;
        totalLaunches += app.launchCount;
    });

    totalTimeEl.textContent = formatPlaytime(totalMs);
    activeAppsEl.textContent = `${usageData.length} Apps`;
    totalLaunchesEl.textContent = `${totalLaunches} Launches`;

    // 2. Render Donut Chart
    renderChart(totalMs);

    // 3. Render Leaderboard List
    renderLeaderboard();
}

// Render empty state UI
function renderEmptyState() {
    totalTimeEl.textContent = '0m 0s';
    activeAppsEl.textContent = '0 Apps';
    totalLaunchesEl.textContent = '0 Launches';
    chartCenterValueEl.textContent = '0m';
    
    // Clear chart
    svgChart.innerHTML = `<circle cx="100" cy="100" r="70" fill="transparent" stroke="rgba(255,255,255,0.05)" stroke-width="20"></circle>`;
    chartLegend.innerHTML = '';
    
    leaderboardListEl.innerHTML = `
        <div class="empty-state">
            <span>🎮</span>
            <p>No active app playtime recorded for this time range yet.</p>
        </div>
    `;
}

// Draw the dynamic SVG Donut Chart
function renderChart(totalMs) {
    chartCenterValueEl.textContent = formatPlaytimeMinutesOnly(totalMs);
    
    // Reset SVG
    svgChart.innerHTML = '';
    chartLegend.innerHTML = '';

    if (totalMs === 0) return;

    // Sort apps to get top ones for chart, group remainder into "Others"
    const sortedApps = [...usageData].sort((a, b) => b.totalTimeInForeground - a.totalTimeInForeground);
    const topLimit = 5;
    const displayApps = [];
    let othersMs = 0;

    sortedApps.forEach((app, idx) => {
        if (idx < topLimit) {
            displayApps.push({
                name: app.appName,
                ms: app.totalTimeInForeground,
                color: SEGMENT_COLORS[idx]
            });
        } else {
            othersMs += app.totalTimeInForeground;
        }
    });

    if (othersMs > 0) {
        displayApps.push({
            name: 'Others',
            ms: othersMs,
            color: '#6b7280' // Gray color for Others
        });
    }

    // Draw slices using stroke-dasharray (Radius = 70, Circumference = 2 * PI * 70 = 439.82)
    const radius = 70;
    const circumference = 2 * Math.PI * radius;
    let accumulatedOffset = 0;

    displayApps.forEach(app => {
        const percentage = app.ms / totalMs;
        const strokeLength = circumference * percentage;
        const strokeSpace = circumference - strokeLength;
        const offset = circumference - accumulatedOffset;

        // Create SVG Circle slice
        const circle = document.createElementNS('http://www.w3.org/2000/svg', 'circle');
        circle.setAttribute('cx', '100');
        circle.setAttribute('cy', '100');
        circle.setAttribute('r', radius.toString());
        circle.setAttribute('fill', 'transparent');
        circle.setAttribute('stroke', app.color);
        circle.setAttribute('stroke-width', '20');
        circle.setAttribute('stroke-dasharray', `${strokeLength} ${strokeSpace}`);
        circle.setAttribute('stroke-dashoffset', offset.toString());
        circle.setAttribute('style', `transition: stroke-dashoffset 0.8s ease-out;`);
        
        svgChart.appendChild(circle);
        accumulatedOffset += strokeLength;

        // Add to Legend
        const legendItem = document.createElement('div');
        legendItem.className = 'legend-item';
        legendItem.innerHTML = `
            <div class="legend-left">
                <span class="legend-dot" style="background-color: ${app.color}"></span>
                <span class="legend-name">${app.name}</span>
            </div>
            <div class="legend-right">
                <span class="legend-time">${formatPlaytime(app.ms)}</span>
                <span class="legend-pct">${(percentage * 100).toFixed(1)}%</span>
            </div>
        `;
        chartLegend.appendChild(legendItem);
    });
}

// Render Leaderboard items
function renderLeaderboard() {
    leaderboardListEl.innerHTML = '';
    
    // Sort and render
    const sorted = [...usageData].sort((a, b) => b.totalTimeInForeground - a.totalTimeInForeground);
    
    sorted.forEach((app, idx) => {
        const item = document.createElement('div');
        item.className = 'leaderboard-item';
        item.dataset.appName = app.appName.toLowerCase();
        item.dataset.pkgName = app.packageName.toLowerCase();

        const rankClass = idx < 3 ? `rank-${idx + 1}` : '';
        const logoEmoji = getAppEmoji(app.appName, app.packageName);
        const lastUsedStr = app.lastTimeUsed > 0 ? new Date(app.lastTimeUsed).toLocaleTimeString() : 'Never';

        item.innerHTML = `
            <div class="item-left">
                <span class="item-rank ${rankClass}">${idx + 1}</span>
                <div class="item-logo">${logoEmoji}</div>
                <div class="item-meta">
                    <h4>${app.appName}</h4>
                    <span>${app.packageName} • Last used ${lastUsedStr}</span>
                </div>
            </div>
            <div class="item-right">
                <div class="item-time">${formatPlaytime(app.totalTimeInForeground)}</div>
                <div class="item-launches">${app.launchCount} launches</div>
            </div>
        `;
        leaderboardListEl.appendChild(item);
    });
}

// Filter the active list based on search query
function filterAppList() {
    const query = searchInput.value.toLowerCase();
    const items = leaderboardListEl.querySelectorAll('.leaderboard-item');

    items.forEach(item => {
        const appName = item.dataset.appName;
        const pkgName = item.dataset.pkgName;
        if (appName.includes(query) || pkgName.includes(query)) {
            item.style.display = 'flex';
        } else {
            item.style.display = 'none';
        }
    });
}

// Helper to choose a cool emoji for default apps
function getAppEmoji(appName, pkgName) {
    const lowerApp = appName.toLowerCase();
    const lowerPkg = pkgName.toLowerCase();

    if (lowerPkg.includes('store') || lowerApp.includes('store')) return '🛍️';
    if (lowerPkg.includes('browser') || lowerApp.includes('browser') || lowerApp.includes('navigator')) return '🌐';
    if (lowerPkg.includes('player') || lowerApp.includes('player') || lowerApp.includes('tv') || lowerApp.includes('video')) return '📺';
    if (lowerApp.includes('lasertag') || lowerApp.includes('boxing') || lowerApp.includes('game') || lowerApp.includes('vr')) return '🎮';
    if (lowerApp.includes('tracker') || lowerApp.includes('activity')) return '🪐';
    if (lowerApp.includes('settings') || lowerApp.includes('configuration')) return '⚙️';
    if (lowerApp.includes('camera') || lowerApp.includes('capture')) return '📷';
    if (lowerApp.includes('alert')) return '🔔';

    // Sideloaded package initials / standard fallback
    return '📦';
}

// Format time in ms to custom human readable (e.g. 2h 15m 12s)
function formatPlaytime(ms) {
    const totalSecs = Math.floor(ms / 1000);
    const hrs = Math.floor(totalSecs / 3600);
    const mins = Math.floor((totalSecs % 3600) / 60);
    const secs = totalSecs % 60;

    if (hrs > 0) {
        return `${hrs}h ${mins}m`;
    }
    if (mins > 0) {
        return `${mins}m ${secs}s`;
    }
    return `${secs}s`;
}

// Format time in ms to minutes only (e.g. 135m)
function formatPlaytimeMinutesOnly(ms) {
    const mins = Math.round(ms / 60000);
    return `${mins}m`;
}

// Export statistics as JSON
function exportToJSON() {
    if (usageData.length === 0) return;
    
    const dataStr = "data:text/json;charset=utf-8," + encodeURIComponent(JSON.stringify(usageData, null, 2));
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `quest_activity_stats_${currentRange}_${new Date().toISOString().slice(0,10)}.json`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
}

// Export statistics as CSV
function exportToCSV() {
    if (usageData.length === 0) return;

    let csvContent = "Rank,App Name,Package Name,Playtime (seconds),Launches,Last Active Timestamp,Last Active Date\n";
    
    const sorted = [...usageData].sort((a, b) => b.totalTimeInForeground - a.totalTimeInForeground);
    sorted.forEach((app, idx) => {
        const timeSecs = Math.floor(app.totalTimeInForeground / 1000);
        const lastActiveDate = app.lastTimeUsed > 0 ? new Date(app.lastTimeUsed).toISOString() : "Never";
        // Escape quotes
        const appNameClean = app.appName.replace(/"/g, '""');
        csvContent += `${idx + 1},"${appNameClean}","${app.packageName}",${timeSecs},${app.launchCount},${app.lastTimeUsed},"${lastActiveDate}"\n`;
    });

    const dataStr = "data:text/csv;charset=utf-8," + encodeURIComponent(csvContent);
    const downloadAnchor = document.createElement('a');
    downloadAnchor.setAttribute("href", dataStr);
    downloadAnchor.setAttribute("download", `quest_activity_stats_${currentRange}_${new Date().toISOString().slice(0,10)}.csv`);
    document.body.appendChild(downloadAnchor);
    downloadAnchor.click();
    downloadAnchor.remove();
}
