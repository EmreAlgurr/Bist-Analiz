package com.bist;

import com.bist.api.YahooFinanceClient;
import com.bist.model.Hisse;
import com.bist.screener.HisseScreener;
import com.bist.simulator.DripSimulator;

import java.util.ArrayList;
import java.util.List;

/**
 * BIST Hisse Analiz Motoru — Ana giriş noktası.
 * <p>
 * Akış:
 * <ol>
 *   <li>Tanımlı BIST sembollerini Yahoo Finance'ten çek.</li>
 *   <li>Temel analiz filtrelerinden geçir (Screener).</li>
 *   <li>Filtreden geçen her hisse için DRIP backtesti çalıştır.</li>
 * </ol>
 *
 * Çalıştırma:
 * <pre>
 *   mvn clean package
 *   java -jar target/hisse-analiz-1.0.0.jar
 * </pre>
 */
public final class Main {

    // ── Analiz edilecek BIST sembolleri ──────────────────────────────
    private static final String[] SEMBOLLER = {
        "TUPRS.IS",   // Tüpraş
        "THYAO.IS",   // Türk Hava Yolları
        "SISE.IS",    // Şişecam
        "EREGL.IS",   // Ereğli Demir Çelik
        "TOASO.IS",   // Tofaş
        "KCHOL.IS",   // Koç Holding
        "SAHOL.IS",   // Sabancı Holding
        "PETKM.IS",   // Petkim
        "AKBNK.IS",   // Akbank
        "GARAN.IS",   // Garanti BBVA
        "ASELS.IS",   // ASELSAN
        "BIMAS.IS",   // BİM
    };

    // ── Simülasyon parametreleri ─────────────────────────────────────
    private static final double BASLANGIC_SERMAYESI = 80_000.0; // TL
    private static final String VERI_ARALIGI        = "5y";     // 5 yıl geriye

    // ── Main ────────────────────────────────────────────────────────
    public static void main(String[] args) {

        bannerYazdir();

        // 1) Veri çekme
        YahooFinanceClient client = new YahooFinanceClient(VERI_ARALIGI);
        List<Hisse> hisseler = new ArrayList<>();

        System.out.println("\n🔄  Veri çekme başlıyor...\n");

        for (String sembol : SEMBOLLER) {
            try {
                Hisse h = client.hisseVerisiCek(sembol);
                hisseler.add(h);

                // API rate-limit koruması: istekler arası 1 sn bekle
                Thread.sleep(1000);
            } catch (Exception e) {
                System.err.printf("  ❌  %s verisi alınamadı: %s%n",
                        sembol, e.getMessage());
            }
        }

        if (hisseler.isEmpty()) {
            System.err.println("\n❌  Hiçbir hisse verisi alınamadı. Çıkılıyor...");
            System.exit(1);
        }

        // 2) Filtreleme (Screener)
        HisseScreener screener = new HisseScreener();   // Varsayılan eşikler
        screener.raporYazdir(hisseler);

        List<Hisse> filtrelenmis = screener.filtrele(hisseler);

        // 3) DRIP Simülasyonu — filtreden geçen her hisse için
        if (filtrelenmis.isEmpty()) {
            System.out.println("\n⚠️  Filtreyi geçen hisse bulunamadı.");
            System.out.println("    Tüm hisseler için DRIP simülasyonu çalıştırılıyor...\n");
            // Filtre geçen yoksa en az temettü verisi olan ilk 3 hisseyi dene
            filtrelenmis = hisseler.stream()
                    .filter(h -> !h.getTemettuGecmisi().isEmpty())
                    .limit(3)
                    .toList();
        }

        if (filtrelenmis.isEmpty()) {
            System.out.println("\n⚠️  Temettü geçmişi olan hisse bulunamadı. Simülasyon atlanıyor.");
            System.exit(0);
        }

        DripSimulator simulator = new DripSimulator(BASLANGIC_SERMAYESI);

        System.out.printf("%n🚀  DRIP Simülasyonu başlıyor (Sermaye: %,.0f TL)...%n",
                BASLANGIC_SERMAYESI);

        List<DripSimulator.SimulasyonSonuc> sonuclar = new ArrayList<>();

        for (Hisse h : filtrelenmis) {
            try {
                DripSimulator.SimulasyonSonuc sonuc = simulator.simulasyonCalistir(h);
                sonuclar.add(sonuc);
            } catch (Exception e) {
                System.err.printf("  ❌  %s simülasyonu başarısız: %s%n",
                        h.getSembol(), e.getMessage());
            }
        }

        // 4) Özet karşılaştırma tablosu
        if (!sonuclar.isEmpty()) {
            ozetTabloYazdir(sonuclar);
        }
    }

    // ── Özet Tablo ──────────────────────────────────────────────────

    private static void ozetTabloYazdir(List<DripSimulator.SimulasyonSonuc> sonuclar) {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════════╗");
        System.out.println("║                  🏆  DRIP KARŞILAŞTIRMA TABLOSU                     ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  %-12s  %10s  %10s  %16s  %10s  ║%n",
                "SEMBOL", "İLK LOT", "SON LOT", "PORTFÖY DEĞERİ", "CAGR");
        System.out.println("║  ────────────  ──────────  ──────────  ────────────────  ──────────  ║");

        for (var s : sonuclar) {
            System.out.printf("║  %-12s  %10.0f  %10.0f  %,14.2f TL  %+9.2f%%  ║%n",
                    s.sembol(), s.baslangicLot(), s.guncelLot(),
                    s.portfoyDegeri(), s.cagr() * 100);
        }

        // En iyi CAGR
        var enIyi = sonuclar.stream()
                .max((a, b) -> Double.compare(a.cagr(), b.cagr()))
                .orElseThrow();

        System.out.println("╠══════════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  🥇  En iyi CAGR: %s  → %+.2f%% yıllık bileşik getiri           ║%n",
                enIyi.sembol(), enIyi.cagr() * 100);
        System.out.println("╚══════════════════════════════════════════════════════════════════════╝");
    }

    // ── Banner ──────────────────────────────────────────────────────

    private static void bannerYazdir() {
        System.out.println("""
        
        ╔══════════════════════════════════════════════════════════╗
        ║                                                          ║
        ║      ██████╗ ██╗███████╗████████╗                        ║
        ║      ██╔══██╗██║██╔════╝╚══██╔══╝                        ║
        ║      ██████╔╝██║███████╗   ██║                           ║
        ║      ██╔══██╗██║╚════██║   ██║                           ║
        ║      ██████╔╝██║███████║   ██║                           ║
        ║      ╚═════╝ ╚═╝╚══════╝  ╚═╝                           ║
        ║                                                          ║
        ║      📊 Hisse Analiz & DRIP Simülasyon Motoru            ║
        ║      v1.0.0 — Borsa İstanbul                             ║
        ║                                                          ║
        ╚══════════════════════════════════════════════════════════╝
        """);
    }
}
