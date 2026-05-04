package com.bist.screener;

import com.bist.model.Hisse;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Temel analiz rasyolarına göre hisse filtreleme motoru.
 * <p>
 * Filtreleme kriterleri:
 * <ol>
 *   <li>Temettü Verimi  ≥ {@code minDividendYield}</li>
 *   <li>Özsermaye Kârlılığı (ROE)  ≥ {@code minRoe}</li>
 *   <li>Temettü Dağıtım Oranı  ∈ [{@code minPayout}, {@code maxPayout}]</li>
 * </ol>
 *
 * Tüm eşik değerleri yapıcıda (constructor) enjekte edilir — SOLID / OCP uyumlu.
 */
public final class HisseScreener {

    private final double minDividendYield;
    private final double minRoe;
    private final double minPayout;
    private final double maxPayout;

    /**
     * @param minDividendYield Minimum temettü verimi  (ör. 0.04 = %4)
     * @param minRoe           Minimum ROE              (ör. 0.40 = %40)
     * @param minPayout        Minimum dağıtım oranı    (ör. 0.30 = %30)
     * @param maxPayout        Maksimum dağıtım oranı   (ör. 0.80 = %80)
     */
    public HisseScreener(double minDividendYield,
                          double minRoe,
                          double minPayout,
                          double maxPayout) {
        this.minDividendYield = minDividendYield;
        this.minRoe           = minRoe;
        this.minPayout        = minPayout;
        this.maxPayout        = maxPayout;
    }

    /** Varsayılan eşik değerleriyle screener: Yield≥4%, ROE≥40%, Payout %30–%80 */
    public HisseScreener() {
        this(0.04, 0.40, 0.30, 0.80);
    }

    /**
     * Verilen listeyi Java Stream API ile filtreler ve
     * kriterlere uyan hisseleri döndürür.
     */
    public List<Hisse> filtrele(List<Hisse> hisseler) {
        return hisseler.stream()
                .filter(h -> h.getDividendYield() >= minDividendYield)
                .filter(h -> h.getRoe()           >= minRoe)
                .filter(h -> h.getPayoutRatio()   >= minPayout)
                .filter(h -> h.getPayoutRatio()   <= maxPayout)
                .collect(Collectors.toList());
    }

    /**
     * Her hissenin durumunu ve filtreye uyup uymadığını detaylı loglar.
     */
    public void raporYazdir(List<Hisse> tumHisseler) {
        List<Hisse> gecenler = filtrele(tumHisseler);

        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════════════╗");
        System.out.println("║               📊  TEMEL ANALİZ FİLTRE SONUÇLARI                ║");
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");
        System.out.printf( "║  Kriter: Yield ≥ %.0f%%  |  ROE ≥ %.0f%%  |  Payout: %.0f%%–%.0f%%      ║%n",
                minDividendYield * 100, minRoe * 100, minPayout * 100, maxPayout * 100);
        System.out.printf( "║  Taranan: %d hisse  |  Geçen: %d hisse                          ║%n",
                tumHisseler.size(), gecenler.size());
        System.out.println("╠══════════════════════════════════════════════════════════════════╣");

        System.out.printf( "║  %-12s  %10s  %10s  %12s  %8s  ║%n",
                "SEMBOL", "YIELD", "ROE", "PAYOUT", "DURUM");
        System.out.println("║  ────────────  ──────────  ──────────  ────────────  ────────  ║");

        for (Hisse h : tumHisseler) {
            boolean gecti = gecenler.contains(h);
            System.out.printf("║  %-12s  %9.2f%%  %9.2f%%  %11.2f%%  %8s  ║%n",
                    h.getSembol(),
                    h.getDividendYield() * 100,
                    h.getRoe() * 100,
                    h.getPayoutRatio() * 100,
                    gecti ? "✅ GEÇTİ" : "❌ KALDI");
        }

        System.out.println("╚══════════════════════════════════════════════════════════════════╝");
    }
}
