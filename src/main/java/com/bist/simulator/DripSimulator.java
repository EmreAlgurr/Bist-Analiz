package com.bist.simulator;

import com.bist.model.Hisse;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TreeMap;

/**
 * DRIP (Dividend Re-Investment Plan) backtest simülatörü.
 * <p>
 * Algoritma:
 * <ol>
 *   <li>Başlangıç sermayesiyle mümkün olan maksimum lot alınır.</li>
 *   <li>Her temettü dağıtım gününde:
 *       <code>nakit = lotSayisi × hisseBasiNetTemettu</code></li>
 *   <li>Yatan nakit ile o günkü kapanış fiyatından ek lot alınır
 *       (kesirli lot desteklenir).</li>
 *   <li>Simülasyon sonunda portföy değeri, CAGR ve toplam getiri hesaplanır.</li>
 * </ol>
 */
public final class DripSimulator {

    private final double baslangicSermayesi;

    /**
     * @param baslangicSermayesi TL cinsinden başlangıç sermayesi
     */
    public DripSimulator(double baslangicSermayesi) {
        if (baslangicSermayesi <= 0) {
            throw new IllegalArgumentException("Sermaye pozitif olmalıdır");
        }
        this.baslangicSermayesi = baslangicSermayesi;
    }

    /**
     * Verilen hisse üzerinde DRIP simülasyonunu çalıştırır ve
     * sonuçları konsola yazdırır.
     *
     * @param hisse Simülasyona sokulacak hisse (geçmiş veri yüklü olmalı)
     */
    public SimulasyonSonuc simulasyonCalistir(Hisse hisse) {

        TreeMap<LocalDate, Double> kapanis  = hisse.getGunlukKapanis();
        TreeMap<LocalDate, Double> temettu  = hisse.getTemettuGecmisi();

        if (kapanis.isEmpty()) {
            throw new IllegalStateException(
                hisse.getSembol() + " için kapanış verisi bulunamadı");
        }

        // ── İlk alım ───────────────────────────────────────────────
        LocalDate baslangicTarih = kapanis.firstKey();
        double ilkFiyat          = kapanis.firstEntry().getValue();
        double lotSayisi         = Math.floor(baslangicSermayesi / ilkFiyat);
        double kalanNakit        = baslangicSermayesi - (lotSayisi * ilkFiyat);

        double baslangicLot = lotSayisi;

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.printf( "║        💰  DRIP SİMÜLASYONU — %s                        ║%n",
                padRight(hisse.getSembol(), 12));
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Başlangıç Sermayesi  : %,.2f TL%n", baslangicSermayesi);
        System.out.printf( "║  İlk Alım Fiyatı      : %.2f TL  (%s)%n",
                ilkFiyat, baslangicTarih);
        System.out.printf( "║  İlk Lot Sayısı       : %.0f adet%n", lotSayisi);
        System.out.printf( "║  Kalan Nakit           : %.2f TL%n", kalanNakit);
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║  TARİH        TEMETTÜ/HİSSE   YATAN NAKİT    YENİ LOT   TOPLAM ║");
        System.out.println("║  ──────────   ──────────────   ───────────    ────────   ────── ║");

        // ── Temettü döngüsü ─────────────────────────────────────────
        int temettuSayac = 0;
        for (Map.Entry<LocalDate, Double> entry : temettu.entrySet()) {
            LocalDate tarih   = entry.getKey();
            double hisseBasiTemettu = entry.getValue();

            // Temettü tarihi, kapanış verisi aralığında mı?
            if (tarih.isBefore(baslangicTarih)) continue;

            // Yatan nakit
            double yatanNakit = lotSayisi * hisseBasiTemettu;

            // O günkü kapanış fiyatı (tatil ise en yakın işlem günü)
            double kapaFiyat = hisse.enYakinKapanis(tarih)
                    .orElse(ilkFiyat);

            // Toplam kullanılabilir nakit
            kalanNakit += yatanNakit;

            // Yeni lot al (tam lot)
            double yeniLot = Math.floor(kalanNakit / kapaFiyat);
            kalanNakit -= yeniLot * kapaFiyat;
            lotSayisi  += yeniLot;
            temettuSayac++;

            System.out.printf("║  %s   %10.4f TL     %,12.2f TL  %+7.0f   %6.0f  ║%n",
                    tarih, hisseBasiTemettu, yatanNakit, yeniLot, lotSayisi);
        }

        // ── Son durum ───────────────────────────────────────────────
        LocalDate bitisTarih    = kapanis.lastKey();
        double sonFiyat         = kapanis.lastEntry().getValue();
        double portfoyDegeri    = (lotSayisi * sonFiyat) + kalanNakit;
        double toplamGetiri     = (portfoyDegeri / baslangicSermayesi) - 1.0;
        double yilSayisi        = ChronoUnit.DAYS.between(baslangicTarih, bitisTarih) / 365.25;
        double cagr             = (yilSayisi > 0)
                ? Math.pow(portfoyDegeri / baslangicSermayesi, 1.0 / yilSayisi) - 1.0
                : 0.0;

        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.println("║                     📈  SİMÜLASYON SONUÇLARI                   ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Simülasyon Aralığı   : %s  →  %s  (%.1f yıl)%n",
                baslangicTarih, bitisTarih, yilSayisi);
        System.out.printf( "║  Temettü Dağıtım Sayısı: %d%n", temettuSayac);
        System.out.printf( "║  Başlangıç Lot        : %.0f adet%n", baslangicLot);
        System.out.printf( "║  Güncel Lot            : %.0f adet  (+%.0f DRIP)%n",
                lotSayisi, lotSayisi - baslangicLot);
        System.out.printf( "║  Son Kapanış Fiyatı    : %.2f TL  (%s)%n", sonFiyat, bitisTarih);
        System.out.printf( "║  Kalan Nakit           : %.2f TL%n", kalanNakit);
        System.out.printf( "║  Toplam Portföy Değeri : %,.2f TL%n", portfoyDegeri);
        System.out.printf( "║  Toplam Getiri         : %+.2f%%%n", toplamGetiri * 100);
        System.out.printf( "║  Bileşik Yıllık Getiri : %+.2f%% (CAGR)%n", cagr * 100);
        System.out.println("╚══════════════════════════════════════════════════════════════════╝");

        return new SimulasyonSonuc(
                hisse.getSembol(), baslangicLot, lotSayisi,
                portfoyDegeri, toplamGetiri, cagr, yilSayisi
        );
    }

    // ── Yardımcı ─────────────────────────────────────────────────────
    private String padRight(String s, int n) {
        return String.format("%-" + n + "s", s);
    }

    // ── Sonuç Record'u ──────────────────────────────────────────────

    /**
     * Simülasyon sonuçlarını taşıyan immutable kayıt nesnesi.
     */
    public record SimulasyonSonuc(
            String sembol,
            double baslangicLot,
            double guncelLot,
            double portfoyDegeri,
            double toplamGetiri,
            double cagr,
            double yilSayisi
    ) { }
}
