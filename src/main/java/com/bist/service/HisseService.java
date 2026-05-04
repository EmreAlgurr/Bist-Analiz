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

/**
 * Hızlı Screener Servisi (v3.0)
 * Artık dış HTTP çağrıları yapmaz, doğrudan SQLite (Repository) üzerinden
 * anlık sonuç döner.
 */
@Service
public class HisseService {

    private final HisseRepository repository;
    private final Gson gson = new Gson();

    public HisseService(HisseRepository repository) {
        this.repository = repository;
    }

    // ── DTO Records ──────────────────────────────────────────

    public record TaramaDurum(
        String sessionId, String durum, int tamamlanan, int toplam,
        List<HisseDto> hisseler, List<String> hatalar
    ) {}

    public record HisseDto(
        String sembol, double dividendYield, double roe, double payoutRatio,
        double sonFiyat, int temettuSayisi, int gunSayisi
    ) {}

    public record DripSonuc(
        String sembol, double baslangicLot, double guncelLot,
        double portfoyDegeri, double reelDeger, double toplamGetiri, double cagr,
        double yilSayisi, String baslangicTarih, String bitisTarih,
        int temettuDagitimSayisi, double baslangicSermayesi, double toplamYatirilan,
        double sonFiyat, double kalanNakit, List<TemettuOlay> olaylar
    ) {}

    public record TemettuOlay(
        String tarih, double hisseBasiTemettu, double yatanNakit,
        double yeniLot, double toplamLot
    ) {}

    // ── Screener & Veri Çekme ────────────────────────────────

    public String taramaBaslat() {
        // Artık tarama anında DB'den yapılıyor.
        // Frontend yapısını (Polling) bozmamak için sahte bir sessionId dönüyoruz.
        return UUID.randomUUID().toString().substring(0, 8);
    }

    public TaramaDurum taramaDurumu(String sessionId) {
        // Doğrudan Caching katmanından (SQLite) çok hızlı çekim
        List<HisseEntity> entities = repository.findAll();
        
        List<HisseDto> dtoList = entities.stream()
            .map(e -> new HisseDto(
                e.getSembol(), e.getDividendYield(), e.getRoe(), e.getPayoutRatio(),
                e.getSonFiyat(), e.getTemettuSayisi(), e.getGunSayisi()
            ))
            .sorted(Comparator.comparing(HisseDto::sembol))
            .toList();

        // Her zaman %100 TAMAMLANDI dönüyoruz
        return new TaramaDurum(
            sessionId, "TAMAMLANDI", entities.size(), entities.size(),
            dtoList, new ArrayList<>()
        );
    }

    // ── DRIP Simülasyonu ──────────────────────────────────────

    public DripSonuc dripCalistir(String sessionId, String sembol, double sermaye, double aylikEkGirdi) {
        HisseEntity entity = repository.findById(sembol).orElse(null);
        if (entity == null) return null;

        TreeMap<LocalDate, Double> kapanis = parseMap(entity.getKapanisGecmisiJson());
        TreeMap<LocalDate, Double> temettu = parseMap(entity.getTemettuGecmisiJson());

        if (kapanis.isEmpty()) return null;

        LocalDate basTarih = kapanis.firstKey();
        double ilkFiyat = kapanis.firstEntry().getValue();
        double lotSayisi = Math.floor(sermaye / ilkFiyat);
        double kalanNakit = sermaye - (lotSayisi * ilkFiyat);
        double basLot = lotSayisi;
        double toplamYatirilan = sermaye;

        List<TemettuOlay> olaylar = new ArrayList<>();
        int sayac = 0;
        YearMonth currentMonth = YearMonth.from(basTarih);

        for (LocalDate date = basTarih.plusDays(1); !date.isAfter(kapanis.lastKey()); date = date.plusDays(1)) {
            // DCA: Her ay değiştiğinde (düzenli alım)
            YearMonth ym = YearMonth.from(date);
            if (ym.isAfter(currentMonth)) {
                currentMonth = ym;
                toplamYatirilan += aylikEkGirdi;
                kalanNakit += aylikEkGirdi;
                
                Map.Entry<LocalDate, Double> floor = kapanis.floorEntry(date);
                if (floor != null) {
                    double f = floor.getValue();
                    double yl = Math.floor(kalanNakit / f);
                    kalanNakit -= yl * f;
                    lotSayisi += yl;
                }
            }

            // DRIP: Temettü Dağıtımı
            if (temettu.containsKey(date)) {
                double hbd = temettu.get(date);
                double yatanNakit = lotSayisi * hbd;
                
                Map.Entry<LocalDate, Double> floor = kapanis.floorEntry(date);
                double kf = floor != null ? floor.getValue() : ilkFiyat;

                kalanNakit += yatanNakit;
                double yeniLot = Math.floor(kalanNakit / kf);
                kalanNakit -= yeniLot * kf;
                lotSayisi += yeniLot;
                sayac++;

                olaylar.add(new TemettuOlay(date.toString(), hbd, yatanNakit, yeniLot, lotSayisi));
            }
        }

        LocalDate bitTarih = kapanis.lastKey();
        double sonF = kapanis.lastEntry().getValue();
        double portfoy = (lotSayisi * sonF) + kalanNakit;
        double topGetiri = (portfoy / toplamYatirilan) - 1.0;
        double yil = ChronoUnit.DAYS.between(basTarih, bitTarih) / 365.25;
        double cagr = yil > 0 ? Math.pow(portfoy / toplamYatirilan, 1.0 / yil) - 1.0 : 0;
        
        // Basit Yıllık %40 Enflasyon Düzeltmesi (Reel Getiri)
        double reelDeger = portfoy / Math.pow(1.40, yil);

        return new DripSonuc(sembol, basLot, lotSayisi, portfoy, reelDeger, topGetiri, cagr,
                yil, basTarih.toString(), bitTarih.toString(), sayac, sermaye, toplamYatirilan,
                sonF, kalanNakit, olaylar);
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
