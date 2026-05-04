package com.bist.service;

import com.bist.entity.HisseEntity;
import com.bist.repository.HisseRepository;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import org.springframework.stereotype.Service;

import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class PortfolioSimulationService {

    private final HisseRepository repository;
    private final PortfolioOptimizer optimizer;
    private final Gson gson = new Gson();

    // Yıllık basit enflasyon / kur düzeltmesi varsayımı (Örn: %40)
    private static final double ANNUAL_INFLATION = 0.40;

    public PortfolioSimulationService(HisseRepository repository, PortfolioOptimizer optimizer) {
        this.repository = repository;
        this.optimizer = optimizer;
    }

    public record PortfolioSonuc(
        Map<String, Double> agirliklar,
        double baslangicSermayesi,
        double aylikEkGirdi,
        double toplamYatirilan,
        double toplamReelYatirilan,
        double nominalDeger,
        double reelDeger,
        double cagr,
        int yilSayisi,
        String baslangicTarihi,
        String bitisTarihi,
        List<String> logs
    ) {}

    public PortfolioSonuc simulatePortfolio(List<String> symbols, double baslangicSermaye, double aylikEkGirdi) {
        List<HisseEntity> entities = repository.findAllById(symbols);
        if (entities.isEmpty()) throw new IllegalArgumentException("Hisse bulunamadı!");

        Map<String, TreeMap<LocalDate, Double>> allPrices = new HashMap<>();
        Map<String, TreeMap<LocalDate, Double>> allDivs = new HashMap<>();

        LocalDate commonStart = LocalDate.MIN;
        LocalDate commonEnd = LocalDate.MAX;

        for (HisseEntity e : entities) {
            TreeMap<LocalDate, Double> p = parseMap(e.getKapanisGecmisiJson());
            TreeMap<LocalDate, Double> d = parseMap(e.getTemettuGecmisiJson());
            allPrices.put(e.getSembol(), p);
            allDivs.put(e.getSembol(), d);

            if (p.isEmpty()) continue;
            if (commonStart.equals(LocalDate.MIN) || p.firstKey().isAfter(commonStart)) {
                commonStart = p.firstKey(); // En yeni başlangıç tarihini baz al (örtüşme)
            }
            if (commonEnd.equals(LocalDate.MAX) || p.lastKey().isBefore(commonEnd)) {
                commonEnd = p.lastKey();
            }
        }

        // Covariance hesaplaması için Günlük Getirileri oluştur
        Map<String, List<Double>> returnsMap = new HashMap<>();
        for (HisseEntity e : entities) {
            List<Double> returns = new ArrayList<>();
            TreeMap<LocalDate, Double> p = allPrices.get(e.getSembol());
            double prev = -1;
            for (LocalDate date = commonStart; !date.isAfter(commonEnd); date = date.plusDays(1)) {
                Map.Entry<LocalDate, Double> floor = p.floorEntry(date);
                if (floor == null) continue;
                double current = floor.getValue();
                if (prev != -1) {
                    returns.add((current - prev) / prev);
                }
                prev = current;
            }
            returnsMap.put(e.getSembol(), returns);
        }

        // Markowitz Optimizasyonu (Minimum Varyans Sepeti)
        var optimal = optimizer.optimize(symbols, returnsMap);
        Map<String, Double> w = optimal.weights();

        // ── DCA & DRIP SIMÜLASYONU ──
        double toplamYatirilan = baslangicSermaye;
        double toplamReelYatirilan = baslangicSermaye;
        Map<String, Double> lotlar = new HashMap<>();
        Map<String, Double> nakitler = new HashMap<>();

        // İlk Tahsis
        for (String s : symbols) {
            double tahsis = baslangicSermaye * w.get(s);
            double ilkFiyat = allPrices.get(s).ceilingEntry(commonStart).getValue();
            double lot = Math.floor(tahsis / ilkFiyat);
            lotlar.put(s, lot);
            nakitler.put(s, tahsis - (lot * ilkFiyat));
        }

        List<String> logs = new ArrayList<>();
        YearMonth currentMonth = YearMonth.from(commonStart);

        for (LocalDate date = commonStart.plusDays(1); !date.isAfter(commonEnd); date = date.plusDays(1)) {
            
            // 1) DCA: Her ay değiştiğinde (düzenli alım)
            YearMonth ym = YearMonth.from(date);
            if (ym.isAfter(currentMonth)) {
                currentMonth = ym;
                toplamYatirilan += aylikEkGirdi;
                
                double yil = ChronoUnit.DAYS.between(commonStart, date) / 365.25;
                double deflator = Math.pow(1 + ANNUAL_INFLATION, yil);
                toplamReelYatirilan += (aylikEkGirdi / deflator);
                
                for (String s : symbols) {
                    double ekTahsis = aylikEkGirdi * w.get(s);
                    nakitler.put(s, nakitler.get(s) + ekTahsis);
                    
                    Map.Entry<LocalDate, Double> floor = allPrices.get(s).floorEntry(date);
                    if (floor != null) {
                        double fiyat = floor.getValue();
                        double yeniLot = Math.floor(nakitler.get(s) / fiyat);
                        lotlar.put(s, lotlar.get(s) + yeniLot);
                        nakitler.put(s, nakitler.get(s) - (yeniLot * fiyat));
                    }
                }
            }

            // 2) DRIP: Temettü Dağıtımı
            for (String s : symbols) {
                if (allDivs.get(s).containsKey(date)) {
                    double div = allDivs.get(s).get(date);
                    double yatan = lotlar.get(s) * div;
                    nakitler.put(s, nakitler.get(s) + yatan);

                    Map.Entry<LocalDate, Double> floor = allPrices.get(s).floorEntry(date);
                    if (floor != null) {
                        double fiyat = floor.getValue();
                        double yeniLot = Math.floor(nakitler.get(s) / fiyat);
                        lotlar.put(s, lotlar.get(s) + yeniLot);
                        nakitler.put(s, nakitler.get(s) - (yeniLot * fiyat));
                        
                        logs.add(String.format("%s | %s - Temettü: %.2f ₺, Yeni Alınan: %.0f Lot", 
                                date.toString(), s, yatan, yeniLot));
                    }
                }
            }
        }

        // ── Sonuçları Derle ──
        double portfoyDegeri = 0;
        for (String s : symbols) {
            Map.Entry<LocalDate, Double> floor = allPrices.get(s).floorEntry(commonEnd);
            double fiyat = floor != null ? floor.getValue() : 0;
            portfoyDegeri += (lotlar.get(s) * fiyat) + nakitler.get(s);
        }

        int yilSayisi = (int) ChronoUnit.YEARS.between(commonStart, commonEnd);
        if (yilSayisi == 0) yilSayisi = 1;

        double cagr = Math.pow(portfoyDegeri / toplamYatirilan, 1.0 / yilSayisi) - 1.0;
        
        // Reel Getiri Hesaplama: (Enflasyonun yıpratıcı etkisi)
        double reelDeger = portfoyDegeri / Math.pow(1 + ANNUAL_INFLATION, yilSayisi);

        return new PortfolioSonuc(
            w, baslangicSermaye, aylikEkGirdi, toplamYatirilan, toplamReelYatirilan, portfoyDegeri, reelDeger, 
            cagr, yilSayisi, commonStart.toString(), commonEnd.toString(), logs
        );
    }

    private TreeMap<LocalDate, Double> parseMap(String json) {
        TreeMap<LocalDate, Double> result = new TreeMap<>();
        if (json == null || json.isEmpty() || json.equals("{}")) return result;
        Type type = new TypeToken<Map<String, Double>>() {}.getType();
        Map<String, Double> map = gson.fromJson(json, type);
        for (Map.Entry<String, Double> entry : map.entrySet()) {
            result.put(LocalDate.parse(entry.getKey()), entry.getValue());
        }
        return result;
    }
}
