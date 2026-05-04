package com.bist.service;

import com.bist.api.YahooFinanceClient;
import com.bist.data.Bist100;
import com.bist.entity.HisseEntity;
import com.bist.model.Hisse;
import com.bist.repository.HisseRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class SyncService {

    private final HisseRepository repository;
    private final Gson gson;

    public SyncService(HisseRepository repository) {
        this.repository = repository;
        this.gson = new GsonBuilder().create();
    }

    /**
     * Uygulama ilk açıldığında veritabanı boşsa ilk senkronizasyonu başlatır.
     */
    @PostConstruct
    public void initDbIfEmpty() {
        if (repository.count() == 0) {
            System.out.println("⚠️ Veritabanı boş! İlk veri çekme işlemi başlatılıyor...");
            new Thread(this::verileriGuncelle).start();
        }
    }

    /**
     * Cron Job: Her gece saat 03:00'te çalışır.
     * Rate-Limit yememek için istekler arasına Thread.sleep(2000) koyulmuştur.
     */
    @Scheduled(cron = "0 0 3 * * ?")
    public void geceSenkronizasyonu() {
        System.out.println("🌙 Gece senkronizasyonu başlıyor (BIST-100)...");
        verileriGuncelle();
    }

    public void verileriGuncelle() {
        YahooFinanceClient client = new YahooFinanceClient("5y");

        for (String sembol : Bist100.SEMBOLLER) {
            try {
                System.out.println("⬇️ DB'ye Çekiliyor: " + sembol);
                Hisse h = client.hisseVerisiCek(sembol);

                // Veritabanında varsa Update (orElse ile yoksa Insert)
                HisseEntity entity = repository.findById(sembol).orElse(new HisseEntity());
                
                entity.setSembol(h.getSembol());
                entity.setDividendYield(h.getDividendYield());
                entity.setRoe(h.getRoe());
                entity.setPayoutRatio(h.getPayoutRatio());
                entity.setSonFiyat(h.getGunlukKapanis().isEmpty() ? 0.0 : h.sonKapanis());
                entity.setTemettuSayisi(h.getTemettuGecmisi().size());
                entity.setGunSayisi(h.getGunlukKapanis().size());

                // Karmaşık Map yapılarını (Tarih -> Değer) SQLite'a basit TEXT (JSON) olarak kaydediyoruz
                Map<String, Double> kapanisStrMap = h.getGunlukKapanis().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
                Map<String, Double> temettuStrMap = h.getTemettuGecmisi().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));

                entity.setKapanisGecmisiJson(gson.toJson(kapanisStrMap));
                entity.setTemettuGecmisiJson(gson.toJson(temettuStrMap));
                entity.setSonGuncelleme(LocalDateTime.now());

                repository.save(entity);

                // Yahoo Finance Rate Limit koruması
                Thread.sleep(2000);

            } catch (Exception e) {
                System.err.println("❌ Hata (" + sembol + "): " + e.getMessage());
            }
        }
        System.out.println("✅ Tüm hisseler başarıyla veritabanına senkronize edildi.");
    }
}
