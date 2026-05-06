package com.bist.service;

import com.bist.entity.HisseEntity;
import com.bist.repository.HisseRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Strateji Tarama Servisi (v3.2)
 *
 * İki ana strateji filtresi:
 * 1. Temettü Devleri (Core/Defansif) — Yüksek verimli, düşük riskli, nakit akışı güçlü
 * 2. Agresif Büyüme (Satellite/Ofansif) — Teknoloji, yenilenebilir enerji, telekomünikasyon
 *
 * v3.2 Düzeltmeleri:
 * - Agresif Büyüme: Finans, Holding, GYO, Ağır İnşaat sektörleri EXCLUDE edildi
 * - Agresif Büyüme: Net Kâr Marjı > 0 zorunlu (zarar eden şirketleri ele)
 * - Agresif Büyüme: F/K limitörü eklendi (max 50)
 * - Sektörel Z-Score normalizasyonu
 * - Hacim (likidite) filtresi
 * - Yatırım döngüsü filtresi
 */
@Service
public class StrategyScreenerService {

    private final HisseRepository repository;

    @Value("${strategy.min.volume:1000000}")
    private double minGunlukHacim;

    /**
     * Agresif Büyüme filtresinden HARIÇ tutulan sektörler.
     * Bu sektörlerdeki şirketler "yıkıcı büyüme" kategorisine girmez.
     */
    private static final Set<String> AGRESIF_EXCLUDE_SEKTORLER = Set.of(
        "Financial Services",   // Bankalar, sigorta, finans holdingleri
        "Real Estate",          // GYO'lar, emlak şirketleri
        "Industrials"           // Ağır sanayi, inşaat, holding (ENKAI, ALARK vb.)
    );

    /**
     * Agresif Büyüme için İZİN VERİLEN sektörler.
     * Bu sektörler "yıkıcı inovasyon" yapabilecek sektörlerdir.
     */
    private static final Set<String> AGRESIF_ALLOW_SEKTORLER = Set.of(
        "Technology",               // Yazılım, donanım, BT
        "Communication Services",   // Telekomünikasyon, medya
        "Healthcare",               // İlaç, biyoteknoloji
        "Consumer Cyclical",        // Perakende teknoloji, e-ticaret
        "Consumer Defensive",       // Hızlı tüketim büyümesi
        "Utilities",                // Yenilenebilir enerji
        "Energy",                   // Enerji dönüşümü
        "Basic Materials"           // Hammadde / maden büyümesi
    );

    public StrategyScreenerService(HisseRepository repository) {
        this.repository = repository;
    }

    // ── DTO Records ──────────────────────────────────────────────────

    public record StrategyResult(
        String stratejiAdi,
        String stratejiAciklama,
        int toplamTaranan,
        int filtrelenen,
        List<ScreenedStock> hisseler,
        Map<String, SektorIstatistik> sektorIstatistikleri
    ) {}

    public record ScreenedStock(
        String sembol,
        String sektor,
        double sonFiyat,
        double dividendYield,
        double roe,
        double payoutRatio,
        double fk,
        double pddd,
        double netKarMarji,
        double ciroBuyumesi,
        double netBorcFavoek,
        double serbestNakitAkisi,
        double argeCircOrani,
        double yatirimAmortismanOrani,
        double gunlukOrtHacim,
        int temettuSayisi,
        double skor,
        double fkZScore,
        double roeZScore,
        String badge
    ) {}

    public record SektorIstatistik(
        String sektor,
        double ortFk,
        double ortRoe,
        double ortPddd,
        int hisseSayisi
    ) {}

    // ── 1. TEMETTÜ DEVLERİ (Core/Defansif) ──────────────────────────

    /**
     * Temettü Devleri filtreleme mantığı:
     * - Temettü Verimi > %5
     * - Dağıtım Oranı %40 - %85
     * - ROE > %15 (BIST koşullarına uygun; %40 çok agresif)
     * - F/K ve PD/DD < Sektör Ortalaması (Z-Score ile)
     * - Son 5 yılın en az 4'ünde temettü dağıtmış
     * - Hacim filtresi (likidite)
     */
    public StrategyResult temettuDevleri() {
        List<HisseEntity> tumHisseler = repository.findAll();
        Map<String, SektorIstatistik> sektorStats = sektorIstatistikleriniHesapla(tumHisseler);

        List<ScreenedStock> sonuclar = new ArrayList<>();

        for (HisseEntity h : tumHisseler) {
            // Hacim Filtresi
            if (h.getGunlukOrtHacim() < minGunlukHacim && h.getGunlukOrtHacim() > 0) continue;

            // 1) Temettü Verimi > %5
            if (h.getDividendYield() < 0.05) continue;

            // 2) Dağıtım Oranı %40 - %85
            if (h.getPayoutRatio() < 0.40 || h.getPayoutRatio() > 0.85) continue;

            // 3) ROE > %15
            if (h.getRoe() < 0.15) continue;

            // 4) Temettü İstikrarı: Son 5 yılda en az 4 temettü
            if (h.getTemettuSayisi() < 4) continue;

            // Sektörel Z-Score Hesapla
            double fkZ = 0, roeZ = 0;
            SektorIstatistik sektorStat = sektorStats.get(h.getSektor());

            if (sektorStat != null && sektorStat.ortFk() > 0 && h.getFk() > 0) {
                fkZ = calculateZScore(h.getFk(), tumHisseler, "fk", h.getSektor());
            }
            if (sektorStat != null && sektorStat.ortRoe() > 0) {
                roeZ = calculateZScore(h.getRoe(), tumHisseler, "roe", h.getSektor());
            }

            // Temettü Devleri Skoru (0-100)
            double skor = hesaplaTemettuSkoru(h, fkZ, roeZ);

            sonuclar.add(buildScreenedStock(h, skor, fkZ, roeZ, "⭐ Temettü Devi"));
        }

        sonuclar.sort(Comparator.comparingDouble(ScreenedStock::skor).reversed());

        return new StrategyResult(
            "Temettü Devleri",
            "Nakit akışı sağlayan, kârını paylaşan, piyasa volatilitesine dayanıklı defansif devler.",
            tumHisseler.size(), sonuclar.size(), sonuclar, sektorStats
        );
    }

    // ── 2. AGRESİF BÜYÜME (Satellite/Ofansif) — v3.2 Düzeltilmiş ───

    /**
     * Agresif Büyüme filtreleme mantığı (v3.2):
     *
     * SERT KURALLAR (Hard Filters):
     * 1. SEKTÖR KISITI: Finans, GYO, Ağır Sanayi/Holding HARIÇ
     * 2. Ciro Büyümesi > %25 (Yıllık)
     * 3. Net Kâr Marjı > 0 (Zarar yazmamış olmalı)
     * 4. F/K < 50 (Balon fiyatlamayı engelle)
     * 5. Net Borç / FAVÖK < 2.5
     *
     * PUANLAMA:
     * - Rule of 40 (Ciro Büyümesi + Net Kâr Marjı)
     * - Serbest Nakit Akışı > 0
     * - Yatırım Döngüsü (CapEx/Depreciation > 1)
     * - Hacim filtresi
     */
    public StrategyResult agresifBuyume() {
        List<HisseEntity> tumHisseler = repository.findAll();
        Map<String, SektorIstatistik> sektorStats = sektorIstatistikleriniHesapla(tumHisseler);

        List<ScreenedStock> sonuclar = new ArrayList<>();

        for (HisseEntity h : tumHisseler) {
            // ══ SERT KURAL 1: Sektör Kısıtı ══
            // Finans, GYO, Ağır Sanayi/Holding sektörlerini EXCLUDE et
            String sektor = h.getSektor();
            if (sektor == null || sektor.isBlank()) continue;
            if (AGRESIF_EXCLUDE_SEKTORLER.contains(sektor)) continue;

            // ══ Hacim Filtresi ══
            if (h.getGunlukOrtHacim() < minGunlukHacim && h.getGunlukOrtHacim() > 0) continue;

            // ══ SERT KURAL 2: Ciro Büyümesi > %25 ══
            if (h.getCiroBuyumesi() < 0.25) continue;

            // ══ SERT KURAL 3: Net Kâr Marjı > 0 (Zarar yazmamış) ══
            if (h.getNetKarMarji() <= 0) continue;

            // ══ SERT KURAL 4: F/K Limitörü (0 < F/K < 50) ══
            // F/K 0 ise veri yoktur, 50+ ise balon/anomali
            if (h.getFk() <= 0 || h.getFk() > 50) continue;

            // ══ SERT KURAL 5: Borçluluk kontrolü ══
            if (h.getNetBorcFavoek() > 2.5 && h.getNetBorcFavoek() != 0) continue;

            // ── Rule of 40 hesapla ──
            double ruleOf40 = (h.getCiroBuyumesi() * 100) + (h.getNetKarMarji() * 100);

            // Yatırım Döngüsü Filtresi
            boolean yatirimOK = h.getYatirimAmortismanOrani() > 1.0 || h.getYatirimAmortismanOrani() == 0;

            // Sektörel Z-Score
            double fkZ = 0, roeZ = 0;
            if (h.getFk() > 0) {
                fkZ = calculateZScore(h.getFk(), tumHisseler, "fk", sektor);
            }
            if (h.getRoe() > 0) {
                roeZ = calculateZScore(h.getRoe(), tumHisseler, "roe", sektor);
            }

            // Agresif Büyüme Skoru (0-100)
            double skor = hesaplaAgresifSkor(h, ruleOf40, fkZ, yatirimOK);

            sonuclar.add(buildScreenedStock(h, skor, fkZ, roeZ, "🚀 Agresif Büyüme"));
        }

        sonuclar.sort(Comparator.comparingDouble(ScreenedStock::skor).reversed());

        return new StrategyResult(
            "Agresif Büyüme",
            "Teknoloji, yenilenebilir enerji ve telekom sektöründen; cirosunu katlayan, kârlı, yarının devleri. " +
            "Finans, GYO ve ağır sanayi hariç tutulmuştur.",
            tumHisseler.size(), sonuclar.size(), sonuclar, sektorStats
        );
    }

    // ── SEKTÖREL İSTATİSTİK & Z-SCORE ───────────────────────────────

    private Map<String, SektorIstatistik> sektorIstatistikleriniHesapla(List<HisseEntity> hisseler) {
        Map<String, List<HisseEntity>> sektorGruplari = hisseler.stream()
            .filter(h -> h.getSektor() != null && !h.getSektor().isBlank())
            .collect(Collectors.groupingBy(HisseEntity::getSektor));

        Map<String, SektorIstatistik> result = new HashMap<>();

        for (var entry : sektorGruplari.entrySet()) {
            String sektor = entry.getKey();
            List<HisseEntity> grup = entry.getValue();

            double ortFk = grup.stream().filter(h -> h.getFk() > 0 && h.getFk() < 500).mapToDouble(HisseEntity::getFk).average().orElse(0);
            double ortRoe = grup.stream().filter(h -> h.getRoe() > 0).mapToDouble(HisseEntity::getRoe).average().orElse(0);
            double ortPddd = grup.stream().filter(h -> h.getPddd() > 0).mapToDouble(HisseEntity::getPddd).average().orElse(0);

            result.put(sektor, new SektorIstatistik(sektor, ortFk, ortRoe, ortPddd, grup.size()));
        }

        return result;
    }

    /**
     * Sektörel Z-Score hesaplaması.
     * Z = (X - μ) / σ
     */
    private double calculateZScore(double value, List<HisseEntity> tumHisseler,
                                    String metrik, String sektor) {
        if (sektor == null) return 0;

        List<Double> values = tumHisseler.stream()
            .filter(h -> sektor.equals(h.getSektor()))
            .map(h -> switch (metrik) {
                case "fk" -> h.getFk();
                case "roe" -> h.getRoe();
                case "pddd" -> h.getPddd();
                default -> 0.0;
            })
            .filter(v -> v > 0 && v < 1000) // Aşırı uç değerleri filtrele
            .toList();

        if (values.size() < 2) return 0;

        double mean = values.stream().mapToDouble(Double::doubleValue).average().orElse(0);
        double variance = values.stream().mapToDouble(v -> Math.pow(v - mean, 2)).average().orElse(0);
        double stdDev = Math.sqrt(variance);

        if (stdDev == 0) return 0;
        return (value - mean) / stdDev;
    }

    // ── SKOR HESAPLAMA ──────────────────────────────────────────────

    private double hesaplaTemettuSkoru(HisseEntity h, double fkZ, double roeZ) {
        double skor = 0;

        // Temettü Verimi: max 25 puan (yield >= %10 → tam puan)
        skor += Math.min(25, (h.getDividendYield() / 0.10) * 25);

        // ROE: max 25 puan (ROE >= %40 → tam puan)
        skor += Math.min(25, (h.getRoe() / 0.40) * 25);

        // Dağıtım Oranı ideal aralığı (%50-%70): max 15 puan
        double idealPayout = 1.0 - Math.abs(h.getPayoutRatio() - 0.60) / 0.25;
        skor += Math.max(0, Math.min(15, idealPayout * 15));

        // Temettü İstikrarı: max 15 puan
        skor += Math.min(15, (h.getTemettuSayisi() / 8.0) * 15);

        // F/K Z-Score (negatif = sektöre göre ucuz): max 10 puan
        if (fkZ < 0) skor += Math.min(10, Math.abs(fkZ) * 5);

        // ROE Z-Score (pozitif = sektöre göre verimli): max 10 puan
        if (roeZ > 0) skor += Math.min(10, roeZ * 5);

        return Math.min(100, Math.max(0, skor));
    }

    private double hesaplaAgresifSkor(HisseEntity h, double ruleOf40, double fkZ, boolean yatirimOK) {
        double skor = 0;

        // Rule of 40: max 30 puan (>40 → tam puan)
        skor += Math.min(30, (ruleOf40 / 40.0) * 30);

        // Ciro Büyümesi: max 25 puan (>%60 → tam puan)
        skor += Math.min(25, (h.getCiroBuyumesi() / 0.60) * 25);

        // Net Kâr Marjı: max 15 puan (>%15 → tam puan)
        skor += Math.min(15, (h.getNetKarMarji() / 0.15) * 15);

        // Borç kontrolü: max 10 puan (düşük borç = iyi)
        if (h.getNetBorcFavoek() > 0 && h.getNetBorcFavoek() < 2.5) {
            skor += (1.0 - h.getNetBorcFavoek() / 2.5) * 10;
        } else if (h.getNetBorcFavoek() == 0) {
            skor += 10;
        }

        // Serbest Nakit Akışı: max 10 puan
        if (h.getSerbestNakitAkisi() > 0) skor += 10;

        // Yatırım Döngüsü bonus: max 10 puan
        if (yatirimOK && h.getYatirimAmortismanOrani() > 1.0) {
            skor += Math.min(10, (h.getYatirimAmortismanOrani() - 1.0) * 10);
        }

        return Math.min(100, Math.max(0, skor));
    }

    // ── Yardımcılar ──────────────────────────────────────────────────

    private ScreenedStock buildScreenedStock(HisseEntity h, double skor, double fkZ, double roeZ, String badge) {
        return new ScreenedStock(
            h.getSembol(), nvl(h.getSektor()), h.getSonFiyat(),
            h.getDividendYield(), h.getRoe(), h.getPayoutRatio(),
            h.getFk(), h.getPddd(), h.getNetKarMarji(),
            h.getCiroBuyumesi(), h.getNetBorcFavoek(),
            h.getSerbestNakitAkisi(), h.getArgeCircOrani(),
            h.getYatirimAmortismanOrani(), h.getGunlukOrtHacim(),
            h.getTemettuSayisi(), skor, fkZ, roeZ, badge
        );
    }

    private String nvl(String s) {
        return s != null && !s.isBlank() ? s : "Bilinmiyor";
    }
}
