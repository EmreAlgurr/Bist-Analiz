/* ═══ BIST Analiz v3.2 — Frontend Logic ═══ */

let sessionId = null;
let allStocks = [];
let strategyStocks = [];
let currentSort = { col: 'dividendYield', asc: false };
let stratSort = { col: 'skor', asc: false };

/* ══════════════════════════════════════════════════════════════
   PAGE STARTUP — Otomatik Yükleme
   ══════════════════════════════════════════════════════════════ */

(async function startup() {
    // 1) Sync durumu & enflasyon bilgisini çek
    try {
        const res = await fetch('/api/sync/status');
        const data = await res.json();
        if (data.enflasyon) {
            document.getElementById('enflasyonDeger').textContent =
                '%' + (data.enflasyon * 100).toFixed(1);
        }
        if (data.running) {
            updateHeaderStatus('yellow', 'Arka planda veri senkronizasyonu devam ediyor...');
            document.getElementById('btnSync').disabled = true;
            document.getElementById('btnSync').textContent = '⏳ Güncelleniyor...';
            startSyncPoll();
        }
    } catch (e) {
        console.log('Sync status çekilemedi:', e);
    }

    // 2) Tüm hisseleri DB'den yükle
    await loadAllStocks();
})();

/**
 * DB'deki tüm hisseleri tek seferde yükler ve tabloya render eder.
 * Polling yok — veri zaten sunucu tarafında hazır.
 */
async function loadAllStocks() {
    updateHeaderStatus('yellow', 'Hisseler yükleniyor...');

    try {
        // Session oluştur
        const taraRes = await fetch('/api/tara', { method: 'POST' });
        const taraData = await taraRes.json();
        sessionId = taraData.sessionId;

        // Hisseleri çek (anında gelir — DB query)
        const res = await fetch('/api/tara/' + sessionId);
        const data = await res.json();

        allStocks = data.hisseler || [];

        // Enflasyon güncelle
        if (data.enflasyonOrani) {
            document.getElementById('enflasyonDeger').textContent =
                '%' + (data.enflasyonOrani * 100).toFixed(1);
        }

        renderTable();
        updateHeaderStatus('green', allStocks.length + ' hisse yüklendi');

    } catch (e) {
        console.error('Hisse yükleme hatası:', e);
        updateHeaderStatus('red', 'Hisse verileri yüklenemedi');
    }
}

/* ══════════════════════════════════════════════════════════════
   STRATEGY LOADING
   ══════════════════════════════════════════════════════════════ */

async function loadStrategy(type) {
    const btn = type === 'temettu-devleri'
        ? document.getElementById('btnTemettuDevleri')
        : document.getElementById('btnAgresifBuyume');

    btn.classList.add('loading');
    updateHeaderStatus('yellow', 'Strateji analizi yapılıyor...');

    try {
        const res = await fetch('/api/strateji/' + type);
        if (!res.ok) throw new Error('Strateji API hatası: ' + res.status);
        const data = await res.json();

        strategyStocks = data.hisseler || [];
        renderStrategyResults(data);
        updateHeaderStatus('green', data.filtrelenen + ' hisse filtrelendi (' + data.stratejiAdi + ')');
    } catch (e) {
        alert('Strateji yüklenemedi: ' + e.message);
        updateHeaderStatus('red', 'Hata!');
    } finally {
        btn.classList.remove('loading');
    }
}

function renderStrategyResults(data) {
    const section = document.getElementById('strategyResults');
    section.style.display = 'block';

    document.getElementById('strategyTitle').textContent =
        (data.stratejiAdi === 'Temettü Devleri' ? '⭐' : '🚀') + ' ' + data.stratejiAdi + ' Sonuçları';
    document.getElementById('strategyDesc').textContent = data.stratejiAciklama;
    document.getElementById('stratFilteredCount').textContent = data.filtrelenen + ' hisse';
    document.getElementById('stratTotalCount').textContent = '/ ' + data.toplamTaranan + ' taranan';

    // Sektör istatistikleri
    if (data.sektorIstatistikleri && Object.keys(data.sektorIstatistikleri).length > 0) {
        document.getElementById('sectorStats').style.display = 'block';
        const chips = document.getElementById('sectorChips');
        chips.innerHTML = Object.values(data.sektorIstatistikleri)
            .filter(s => s.hisseSayisi > 1)
            .sort((a, b) => b.hisseSayisi - a.hisseSayisi)
            .slice(0, 12)
            .map(s => `
                <div class="sector-chip">
                    <span class="sector-name">${s.sektor}</span>
                    <span class="sector-detail">F/K: ${s.ortFk.toFixed(1)} · ROE: ${(s.ortRoe*100).toFixed(0)}% · ${s.hisseSayisi} hisse</span>
                </div>
            `).join('');
    } else {
        document.getElementById('sectorStats').style.display = 'none';
    }

    renderStrategyTable();
    section.scrollIntoView({ behavior: 'smooth' });
}

function renderStrategyTable() {
    const sorted = [...strategyStocks].sort((a, b) => {
        let va = a[stratSort.col], vb = b[stratSort.col];
        if (typeof va === 'string') return stratSort.asc ? va.localeCompare(vb) : vb.localeCompare(va);
        return stratSort.asc ? va - vb : vb - va;
    });

    const tbody = document.getElementById('strategyTableBody');
    tbody.innerHTML = sorted.map(h => {
        const skorClass = h.skor >= 70 ? 'skor-high' : h.skor >= 40 ? 'skor-mid' : 'skor-low';
        const zLabel = h.fkZScore < 0
            ? `<span class="val-positive" title="Sektöre göre ucuz">F/K: ${h.fkZScore.toFixed(1)}σ</span>`
            : `<span class="val-negative" title="Sektöre göre pahalı">F/K: +${h.fkZScore.toFixed(1)}σ</span>`;
        const roeZLabel = h.roeZScore > 0
            ? `<span class="val-positive" title="Sektöre göre verimli">ROE: +${h.roeZScore.toFixed(1)}σ</span>`
            : `<span class="val-neutral">ROE: ${h.roeZScore.toFixed(1)}σ</span>`;

        return `
        <tr>
            <td><span class="skor-badge ${skorClass}">${h.skor.toFixed(0)}</span></td>
            <td class="symbol-cell">${h.sembol.replace('.IS','')}</td>
            <td class="sector-cell">${h.sektor}</td>
            <td>${fmtPrice(h.sonFiyat)}</td>
            <td class="${valClass(h.dividendYield, 0.03)}">${fmtPct(h.dividendYield)}</td>
            <td class="${valClass(h.roe, 0.15)}">${fmtPct(h.roe)}</td>
            <td class="val-neutral">${h.fk > 0 ? h.fk.toFixed(1) : '-'}</td>
            <td class="${valClass(h.ciroBuyumesi, 0.20)}">${fmtPct(h.ciroBuyumesi)}</td>
            <td class="${h.netKarMarji >= 0.10 ? 'val-positive' : h.netKarMarji > 0 ? 'val-neutral' : 'val-negative'}">${fmtPct(h.netKarMarji)}</td>
            <td class="${h.netBorcFavoek < 2.5 ? 'val-positive' : 'val-negative'}">${h.netBorcFavoek > 0 ? h.netBorcFavoek.toFixed(1) + 'x' : '-'}</td>
            <td class="zscore-cell">${zLabel} ${roeZLabel}</td>
            <td><button class="btn-drip" onclick="openDrip('${h.sembol}')">💰 DRIP</button></td>
        </tr>`;
    }).join('');
}

function sortStratTable(col) {
    if (stratSort.col === col) stratSort.asc = !stratSort.asc;
    else { stratSort.col = col; stratSort.asc = false; }
    renderStrategyTable();
}

function closeStrategyResults() {
    document.getElementById('strategyResults').style.display = 'none';
}

/* ══════════════════════════════════════════════════════════════
   FILTER & RENDER
   ══════════════════════════════════════════════════════════════ */

function getFilters() {
    return {
        minYield: parseFloat(document.getElementById('minYield').value) / 100 || 0,
        minRoe: parseFloat(document.getElementById('minRoe').value) / 100 || 0,
        minPayout: parseFloat(document.getElementById('minPayout').value) / 100 || 0,
        maxPayout: parseFloat(document.getElementById('maxPayout').value) / 100 || 10,
        maxFk: parseFloat(document.getElementById('maxFk').value) || 1000,
        minNetKarMarji: parseFloat(document.getElementById('minNetKarMarji').value) / 100 || -1,
        minCiroBuyumesi: parseFloat(document.getElementById('minCiroBuyumesi').value) / 100 || -1,
        maxBorcFavoek: parseFloat(document.getElementById('maxBorcFavoek').value) || 100,
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
        if (f.maxPayout < 1.0 && h.payoutRatio > f.maxPayout) return false;
        if (h.fk > f.maxFk && h.fk !== 0) return false; // F/K 0 means N/A, we usually let it pass or not? Let's say if fk > maxFk we hide.
        if (h.netKarMarji < f.minNetKarMarji) return false;
        if (h.ciroBuyumesi < f.minCiroBuyumesi) return false;
        if (h.netBorcFavoek > f.maxBorcFavoek && h.netBorcFavoek !== 0) return false;
        return true;
    });
}

function renderTable() {
    const filtered = filterStocks(allStocks);

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
            <td>${h.sembol.replace('.IS','')}</td>
            <td class="sector-cell">${h.sektor || '-'}</td>
            <td>${fmtPrice(h.sonFiyat)}</td>
            <td class="${valClass(h.dividendYield, 0.03)}">${fmtPct(h.dividendYield)}</td>
            <td class="${valClass(h.roe, 0.15)}">${fmtPct(h.roe)}</td>
            <td class="${payoutClass(h.payoutRatio)}">${fmtPct(h.payoutRatio)}</td>
            <td class="val-neutral">${h.fk > 0 ? h.fk.toFixed(1) : '-'}</td>
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

/* ══════════════════════════════════════════════════════════════
   DRIP MODAL
   ══════════════════════════════════════════════════════════════ */

async function openDrip(sembol) {
    const modal = document.getElementById('dripModal');
    const body = document.getElementById('dripModalBody');
    const sermaye = parseFloat(document.getElementById('baslangicSermaye').value) || 0;
    const aylik = parseFloat(document.getElementById('aylikEkGirdi').value) || 0;
    
    modal.style.display = 'flex';
    body.innerHTML = '<div class="modal-loading"><div class="spinner"></div><p>DRIP Simülasyonu hesaplanıyor...</p></div>';

    try {
        const res = await fetch('/api/drip', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ sessionId, sembol, sermaye: sermaye, aylikEkGirdi: aylik })
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
    const enflasyonLabel = d.kullanilanEnflasyon
        ? `(TCMB TÜFE: %${(d.kullanilanEnflasyon * 100).toFixed(1)})`
        : '';

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
                <div class="metric-label">Nominal Yatırılan</div>
                <div class="metric-value">${fmtMoney(d.toplamYatirilan)}</div>
            </div>
            <div class="metric-card">
                <div class="metric-label">Reel Maliyet ${enflasyonLabel}</div>
                <div class="metric-value val-neutral">${fmtMoney(d.toplamReelYatirilan)}</div>
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
                <div class="metric-label">Reel Değer ${enflasyonLabel}</div>
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

/* ══════════════════════════════════════════════════════════════
   SYNC
   ══════════════════════════════════════════════════════════════ */

async function triggerSync() {
    if (!confirm('Tüm BIST hisselerini Yahoo Finance\'den güncellemek uzun sürebilir (~5 dk). Devam?')) return;
    
    const btn = document.getElementById('btnSync');
    btn.disabled = true;
    btn.textContent = '⏳ Güncelleniyor...';
    updateHeaderStatus('yellow', 'Veri güncelleme başlatıldı...');

    try {
        const res = await fetch('/api/sync', { method: 'POST' });
        const data = await res.json();
        alert(data.mesaj);
        updateHeaderStatus('yellow', 'Arka planda güncelleme devam ediyor');
        startSyncPoll();
    } catch (e) {
        alert('Senkronizasyon hatası: ' + e.message);
        btn.disabled = false;
        btn.textContent = '🔄 Verileri Güncelle';
    }
}

function startSyncPoll() {
    const syncPoll = setInterval(async () => {
        try {
            const res = await fetch('/api/sync/status');
            const data = await res.json();
            
            if (data.enflasyon) {
                document.getElementById('enflasyonDeger').textContent =
                    '%' + (data.enflasyon * 100).toFixed(1);
            }

            // Progress göster
            if (data.progress && data.running) {
                updateHeaderStatus('yellow', '🔄 ' + data.progress);
                document.getElementById('btnSync').textContent = '⏳ ' + data.progress;
            }
            
            if (!data.running) {
                clearInterval(syncPoll);
                document.getElementById('btnSync').disabled = false;
                document.getElementById('btnSync').textContent = '🔄 Verileri Güncelle';
                updateHeaderStatus('green', '✅ Veriler güncellendi — ' + (data.progress || ''));
                // Tabloyu yenile
                await loadAllStocks();
            }
        } catch (e) {
            clearInterval(syncPoll);
        }
    }, 5000);
}

/* ══════════════════════════════════════════════════════════════
   HELPERS
   ══════════════════════════════════════════════════════════════ */

function closeModal(e) { if (e.target === document.getElementById('dripModal')) closeDripModal(); }
function closeDripModal() { document.getElementById('dripModal').style.display = 'none'; }

function fmtPct(v) { return (v * 100).toFixed(2) + '%'; }
function fmtPrice(v) { return v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' ₺'; }
function fmtMoney(v) { return v.toLocaleString('tr-TR', { minimumFractionDigits: 2, maximumFractionDigits: 2 }) + ' TL'; }
function valClass(v, threshold) { return v >= threshold ? 'val-positive' : v > 0 ? 'val-neutral' : 'val-negative'; }
function payoutClass(v) { return (v >= 0.3 && v <= 0.8) ? 'val-positive' : v > 0 ? 'val-neutral' : 'val-negative'; }
function updateHeaderStatus(color, text) {
    document.getElementById('headerStats').innerHTML =
        `<div class="stat-chip"><span class="dot ${color}"></span> ${text}</div>`;
}

/* ── Live Filter Listeners — her değişiklikte tabloyu yeniden filtrele ── */
['minYield','minRoe','minPayout','maxPayout','maxFk','minNetKarMarji','minCiroBuyumesi','maxBorcFavoek','searchBox'].forEach(id => {
    document.getElementById(id).addEventListener('input', () => { if (allStocks.length) renderTable(); });
});
