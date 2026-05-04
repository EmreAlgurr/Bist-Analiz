/* ═══ BIST Analiz v2.0 — Frontend Logic ═══ */

let sessionId = null;
let allStocks = [];
let pollTimer = null;
let currentSort = { col: 'dividendYield', asc: false };

/* ── Scan ── */
async function startScan() {
    const btn = document.getElementById('btnScan');
    btn.disabled = true;
    btn.textContent = '⏳ Taranıyor...';

    document.getElementById('progressContainer').style.display = 'block';
    document.getElementById('resultsSection').style.display = 'block';
    updateHeaderStatus('yellow', 'Taranıyor...');

    try {
        const res = await fetch('/api/tara', { method: 'POST' });
        const data = await res.json();
        sessionId = data.sessionId;
        pollTimer = setInterval(pollStatus, 2000);
    } catch (e) {
        alert('Tarama başlatılamadı: ' + e.message);
        btn.disabled = false;
        btn.textContent = '🚀 Piyasayı Tara';
    }
}

async function pollStatus() {
    if (!sessionId) return;
    try {
        const res = await fetch('/api/tara/' + sessionId);
        const data = await res.json();

        const pct = data.toplam > 0 ? (data.tamamlanan / data.toplam * 100) : 0;
        document.getElementById('progressFill').style.width = pct + '%';
        document.getElementById('progressText').textContent =
            data.durum === 'TAMAMLANDI' ? '✅ Tarama tamamlandı!' : '⏳ Taranıyor...';
        document.getElementById('progressCount').textContent =
            data.tamamlanan + ' / ' + data.toplam;

        allStocks = data.hisseler || [];
        renderTable();

        if (data.durum === 'TAMAMLANDI') {
            clearInterval(pollTimer);
            document.getElementById('btnScan').disabled = false;
            document.getElementById('btnScan').textContent = '🚀 Piyasayı Tara';
            updateHeaderStatus('green', data.hisseler.length + ' hisse yüklendi');
        }
    } catch (e) {
        console.error('Poll hatası:', e);
    }
}

/* ── Filter & Render ── */
function getFilters() {
    return {
        minYield: parseFloat(document.getElementById('minYield').value) / 100 || 0,
        minRoe: parseFloat(document.getElementById('minRoe').value) / 100 || 0,
        minPayout: parseFloat(document.getElementById('minPayout').value) / 100 || 0,
        maxPayout: parseFloat(document.getElementById('maxPayout').value) / 100 || 10,
        search: document.getElementById('searchBox').value.toUpperCase().trim()
    };
}

function filterStocks(stocks) {
    const f = getFilters();
    return stocks.filter(h => {
        if (f.search && !h.sembol.includes(f.search)) return false;
        if (h.dividendYield < f.minYield) return false;
        if (h.roe < f.minRoe) return false;
        if (h.payoutRatio < f.minPayout) return false;
        if (h.payoutRatio > f.maxPayout) return false;
        return true;
    });
}

function renderTable() {
    const filtered = filterStocks(allStocks);

    // Sort
    filtered.sort((a, b) => {
        let va = a[currentSort.col], vb = b[currentSort.col];
        if (typeof va === 'string') return currentSort.asc ? va.localeCompare(vb) : vb.localeCompare(va);
        return currentSort.asc ? va - vb : vb - va;
    });

    document.getElementById('filteredCount').textContent = filtered.length + ' hisse';
    document.getElementById('totalCount').textContent = '/ ' + allStocks.length + ' toplam';

    const tbody = document.getElementById('stockTableBody');
    tbody.innerHTML = filtered.map(h => `
        <tr>
            <td><input type="checkbox" class="stock-checkbox" value="${h.sembol}"></td>
            <td>${h.sembol.replace('.IS','')}</td>
            <td>${fmtPrice(h.sonFiyat)}</td>
            <td class="${valClass(h.dividendYield, 0.03)}">${fmtPct(h.dividendYield)}</td>
            <td class="${valClass(h.roe, 0.15)}">${fmtPct(h.roe)}</td>
            <td class="${payoutClass(h.payoutRatio)}">${fmtPct(h.payoutRatio)}</td>
            <td class="val-neutral">${h.temettuSayisi}</td>
            <td><button class="btn-drip" onclick="openDrip('${h.sembol}')">💰 DRIP</button></td>
        </tr>
    `).join('');
}

function sortTable(col) {
    if (currentSort.col === col) currentSort.asc = !currentSort.asc;
    else { currentSort.col = col; currentSort.asc = false; }
    renderTable();
}

/* ── Selection ── */
function toggleSelectAll() {
    const isChecked = document.getElementById('selectAll').checked;
    document.querySelectorAll('.stock-checkbox').forEach(cb => cb.checked = isChecked);
}

/* ── DRIP Modal ── */
async function openDrip(sembol) {
    const modal = document.getElementById('dripModal');
    const body = document.getElementById('dripModalBody');
    const aylik = parseFloat(document.getElementById('aylikEkGirdi').value) || 0;
    
    modal.style.display = 'flex';
    body.innerHTML = '<div class="modal-loading"><div class="spinner"></div><p>DRIP Simülasyonu hesaplanıyor...</p></div>';

    try {
        const res = await fetch('/api/drip', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId, sembol, sermaye: 80000, aylikEkGirdi: aylik })
        });
        const d = await res.json();
        body.innerHTML = buildDripHTML(d);
    } catch (e) {
        body.innerHTML = '<p style="color:var(--red);text-align:center;">Hata: ' + e.message + '</p>';
    }
}

function buildDripHTML(d) {
    const cagrClass = d.cagr >= 0 ? 'val-positive' : 'val-negative';
    const getiriClass = d.toplamGetiri >= 0 ? 'val-positive' : 'val-negative';
    let eventsHTML = '';
    if (d.olaylar && d.olaylar.length > 0) {
        eventsHTML = `
            <h4 class="drip-events-title">📅 Temettü Olayları</h4>
            <table class="drip-events-table"><thead><tr>
                <th>Tarih</th><th>Temettü/Hisse</th><th>Yatan Nakit</th><th>Yeni Lot</th><th>Toplam</th>
            </tr></thead><tbody>
            ${d.olaylar.map(o => `<tr>
                <td>${o.tarih}</td>
                <td>${o.hisseBasiTemettu.toFixed(4)} TL</td>
                <td class="val-positive">${fmtMoney(o.yatanNakit)}</td>
                <td>+${o.yeniLot.toFixed(0)}</td>
                <td>${o.toplamLot.toFixed(0)}</td>
            </tr>`).join('')}
            </tbody></table>`;
    }
    return `
        <div class="drip-header">
            <h3>💰 DRIP & DCA Simülasyonu — ${d.sembol}</h3>
            <div class="drip-subtitle">${d.baslangicTarih} → ${d.bitisTarih} (${d.yilSayisi.toFixed(1)} yıl) | Toplam Yatırılan: ${fmtMoney(d.toplamYatirilan)}</div>
        </div>
        <div class="drip-metrics">
            <div class="metric-card">
                <div class="metric-label">Toplam Yatırılan</div>
                <div class="metric-value">${fmtMoney(d.toplamYatirilan)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Güncel Lot</div>
                <div class="metric-value val-positive">${d.guncelLot.toFixed(0)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Nominal Değer</div>
                <div class="metric-value big ${getiriClass}">${fmtMoney(d.portfoyDegeri)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Reel Değer (Enfl. Düzeltilmiş)</div>
                <div class="metric-value big val-neutral">${fmtMoney(d.reelDeger)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Toplam Getiri</div>
                <div class="metric-value ${getiriClass}">${(d.toplamGetiri*100).toFixed(2)}%</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">CAGR</div>
                <div class="metric-value ${cagrClass}">${(d.cagr*100).toFixed(2)}%</div>
            </div>
        </div>
        ${eventsHTML}`;
}

/* ── Portfolio Optimization ── */
async function optimizePortfolio() {
    const checkboxes = document.querySelectorAll('.stock-checkbox:checked');
    const symbols = Array.from(checkboxes).map(cb => cb.value);
    
    if (symbols.length < 2 || symbols.length > 10) {
        alert("Lütfen portföy optimizasyonu için en az 2, en fazla 10 hisse seçin.");
        return;
    }

    const modal = document.getElementById('dripModal');
    const body = document.getElementById('dripModalBody');
    const aylik = parseFloat(document.getElementById('aylikEkGirdi').value) || 0;
    
    modal.style.display = 'flex';
    body.innerHTML = '<div class="modal-loading"><div class="spinner"></div><p>Markowitz Portföy Optimizasyonu yapılıyor...</p></div>';

    try {
        const res = await fetch('/api/portfolio', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ semboller: symbols, sermaye: 80000, aylikEkGirdi: aylik })
        });
        if (!res.ok) throw new Error("Optimizasyon başarısız oldu.");
        const d = await res.json();
        body.innerHTML = buildPortfolioHTML(d);
    } catch (e) {
        body.innerHTML = '<p style="color:var(--red);text-align:center;">Hata: ' + e.message + '</p>';
    }
}

function buildPortfolioHTML(d) {
    const cagrClass = d.cagr >= 0 ? 'val-positive' : 'val-negative';
    
    let weightsHTML = Object.entries(d.agirliklar)
        .sort((a,b) => b[1] - a[1])
        .filter(w => w[1] > 0.01)
        .map(w => `<span class="badge" style="margin:2px;">${w[0].replace('.IS','')}: ${(w[1]*100).toFixed(1)}%</span>`)
        .join('');

    return `
        <div class="drip-header">
            <h3>📦 Optimal Sepet (Markowitz)</h3>
            <div class="drip-subtitle">${d.baslangicTarihi} → ${d.bitisTarihi} (${d.yilSayisi.toFixed(1)} yıl)</div>
            <div style="margin-top:10px;">${weightsHTML}</div>
        </div>
        <div class="drip-metrics">
            <div class="metric-card">
                <div class="metric-label">Toplam Yatırılan</div>
                <div class="metric-value">${fmtMoney(d.toplamYatirilan)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Nominal Bakiye</div>
                <div class="metric-value big val-positive">${fmtMoney(d.nominalDeger)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Reel Bakiye</div>
                <div class="metric-value big val-neutral">${fmtMoney(d.reelDeger)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Bileşik Getiri (CAGR)</div>
                <div class="metric-value big ${cagrClass}">${(d.cagr*100).toFixed(2)}%</div>
            </div>
        </div>`;
}

function closeModal(e) { if (e.target === document.getElementById('dripModal')) closeDripModal(); }
function closeDripModal() { document.getElementById('dripModal').style.display = 'none'; }

/* ── Helpers ── */
function fmtPct(v) { return (v * 100).toFixed(2) + '%'; }
function fmtPrice(v) { return v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ₺'; }
function fmtMoney(v) { return v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' TL'; }
function valClass(v, threshold) { return v >= threshold ? 'val-positive' : v > 0 ? 'val-neutral' : 'val-negative'; }
function payoutClass(v) { return (v >= 0.3 && v <= 0.8) ? 'val-positive' : v > 0 ? 'val-neutral' : 'val-negative'; }
function updateHeaderStatus(color, text) {
    document.getElementById('headerStats').innerHTML =
        `<div class="stat-chip"><span class="dot ${color}"></span> ${text}</div>`;
}

/* ── Live Filter Listeners ── */
['minYield','minRoe','minPayout','maxPayout','searchBox'].forEach(id => {
    document.getElementById(id).addEventListener('input', () => { if (allStocks.length) renderTable(); });
});
