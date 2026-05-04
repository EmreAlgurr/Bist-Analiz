package com.bist.model;

import java.time.LocalDate;
import java.util.*;

/**
 * Bir BIST hissesinin tüm finansal verisini tutan domain nesnesi.
 * <p>
 * İçerir:
 * - Temel analiz rasyoları  (dividendYield, roe, payoutRatio)
 * - Geçmiş günlük kapanış fiyatları  (tarih → fiyat)
 * - Geçmiş temettü dağıtımları         (tarih → hisse başı net temettü)
 */
public final class Hisse {

    // ── Tanımlayıcılar ──────────────────────────────────────────────
    private final String sembol;      // Örn: "TUPRS.IS"
    private final String isim;        // Örn: "Tüpraş"

    // ── Temel Analiz Rasyoları ───────────────────────────────────────
    private double dividendYield;     // Temettü verimi  (0.05 = %5)
    private double roe;               // Özsermaye kârlılığı (0.40 = %40)
    private double payoutRatio;       // Dağıtım oranı  (0.50 = %50)

    // ── Geçmiş Veriler ──────────────────────────────────────────────
    /** Tarih → Kapanış fiyatı (TL).  TreeMap ile tarih sıralı tutulur. */
    private final TreeMap<LocalDate, Double> gunlukKapanis = new TreeMap<>();

    /** Tarih → Hisse başı net temettü (TL). */
    private final TreeMap<LocalDate, Double> temettuGecmisi = new TreeMap<>();

    // ── Yapıcı ──────────────────────────────────────────────────────
    public Hisse(String sembol, String isim) {
        this.sembol = Objects.requireNonNull(sembol, "Sembol boş olamaz");
        this.isim   = (isim != null) ? isim : sembol;
    }

    // ── Getter / Setter — Rasyolar ──────────────────────────────────
    public String getSembol()          { return sembol; }
    public String getIsim()            { return isim; }

    public double getDividendYield()   { return dividendYield; }
    public void   setDividendYield(double v) { this.dividendYield = v; }

    public double getRoe()             { return roe; }
    public void   setRoe(double v)     { this.roe = v; }

    public double getPayoutRatio()     { return payoutRatio; }
    public void   setPayoutRatio(double v) { this.payoutRatio = v; }

    // ── Geçmiş Veri Erişimi ─────────────────────────────────────────
    public TreeMap<LocalDate, Double> getGunlukKapanis()  { return gunlukKapanis; }
    public TreeMap<LocalDate, Double> getTemettuGecmisi() { return temettuGecmisi; }

    public void kapanisEkle(LocalDate tarih, double fiyat) {
        gunlukKapanis.put(tarih, fiyat);
    }

    public void temettuEkle(LocalDate tarih, double miktar) {
        temettuGecmisi.put(tarih, miktar);
    }

    /**
     * Verilen tarihe en yakın (eşit veya önceki) kapanış fiyatını döndürür.
     * Borsa tatil günlerinde en son işlem gününün fiyatı kullanılır.
     */
    public Optional<Double> enYakinKapanis(LocalDate tarih) {
        Map.Entry<LocalDate, Double> entry = gunlukKapanis.floorEntry(tarih);
        return Optional.ofNullable(entry).map(Map.Entry::getValue);
    }

    /** Son kapanış fiyatı (en güncel tarih). */
    public double sonKapanis() {
        if (gunlukKapanis.isEmpty()) {
            throw new IllegalStateException(sembol + " için kapanış verisi yok");
        }
        return gunlukKapanis.lastEntry().getValue();
    }

    // ── toString ────────────────────────────────────────────────────
    @Override
    public String toString() {
        return String.format(
            "Hisse{sembol='%s', isim='%s', yield=%.2f%%, roe=%.2f%%, payout=%.2f%%}",
            sembol, isim,
            dividendYield * 100, roe * 100, payoutRatio * 100
        );
    }
}
