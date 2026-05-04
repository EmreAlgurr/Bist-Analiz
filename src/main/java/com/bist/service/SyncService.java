package com.bist.service;

import com.bist.api.YahooFinanceClient;
import com.bist.data.Bist100;
import com.bist.api.YahooFinanceClient;
import com.bist.data.Bist100;
import com.bist.entity.HisseEntity;
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

    @PostConstruct
    public void initDbIfEmpty() {
        if (repository.count() == 0) {
            System.out.println("⚠️ Veritabanı boş! İlk veri çekme işlemi başlatılıyor...");
            new Thread(this::verileriGuncelle).start();
        }
    }

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
                HisseEntity h = client.hisseVerisiCek(sembol);

                // DB'de varsa üzerine yaz (update), yoksa yeni kayıt
                HisseEntity entity = repository.findById(sembol).orElse(h);
                if (entity != h) {
                    entity.setDividendYield(h.getDividendYield());
                    entity.setRoe(h.getRoe());
                    entity.setPayoutRatio(h.getPayoutRatio());
                    entity.setSonFiyat(h.getSonFiyat());
                    entity.setGunlukKapanis(h.getGunlukKapanis());
                    entity.setTemettuGecmisi(h.getTemettuGecmisi());
                }

                entity.setTemettuSayisi(h.getTemettuGecmisi().size());
                entity.setGunSayisi(h.getGunlukKapanis().size());

                // JSON Serileştirme
                Map<String, Double> pMap = h.getGunlukKapanis().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
                Map<String, Double> dMap = h.getTemettuGecmisi().entrySet().stream()
                        .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));

                entity.setKapanisGecmisiJson(gson.toJson(pMap));
                entity.setTemettuGecmisiJson(gson.toJson(dMap));
                entity.setSonGuncelleme(LocalDateTime.now());

                repository.save(entity);
                Thread.sleep(2000);

            } catch (Exception e) {
                System.err.println("❌ Hata (" + sembol + "): " + e.getMessage());
            }
        }
        System.out.println("✅ Tüm hisseler başarıyla veritabanına senkronize edildi.");
    }
}
