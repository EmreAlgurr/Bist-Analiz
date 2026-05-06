package com.bist.service;

import com.bist.api.TcmbEvdsClient;
import com.bist.api.YahooFinanceClient;
import com.bist.data.BistTum;
import com.bist.entity.HisseEntity;
import com.bist.repository.HisseRepository;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * Veri Senkronizasyon Servisi (v3.2)
 *
 * Güncelleme stratejisi:
 * - Uygulama açılışında: Eğer veriler 6 saatten eskiyse otomatik günceller
 * - Her gece saat 03:00'te zamanlanmış güncelleme
 * - Manuel güncelleme butonu (frontend'den tetiklenebilir)
 * - Senkronizasyon zaten çalışıyorsa ikinci bir tetik engellenir
 *
 * Performans:
 * - 4 paralel thread ile Yahoo Finance'den veri çeker
 * - Her thread arası 600ms rate-limit koruması
 * - Toplam süre: ~400 hisse / 4 thread / ~2s per hisse ≈ ~3-4 dakika
 */
@Service
public class SyncService {

    private final HisseRepository repository;
    private final Gson gson;
    private final AtomicBoolean syncRunning = new AtomicBoolean(false);
    private final AtomicInteger basarili = new AtomicInteger(0);
    private final AtomicInteger hatali = new AtomicInteger(0);

    @Value("${tcmb.evds.api.key:}")
    private String tcmbApiKey;

    /** Güncel enflasyon oranı (TCMB EVDS'den çekilir, yoksa varsayılan) */
    private double guncelEnflasyon = 0.40;

    /** Son senkronizasyon progress bilgisi */
    private volatile String syncProgressMessage = "";

    private static final int THREAD_COUNT = 4;

    public SyncService(HisseRepository repository) {
        this.repository = repository;
        this.gson = new GsonBuilder().create();
    }

    @PostConstruct
    public void baslangicKontrol() {
        // TCMB'den enflasyon verisini çek
        enflasyonGuncelle();

        long mevcutSayisi = repository.count();

        if (mevcutSayisi == 0) {
            System.out.println("⚠️ Veritabanı boş! İlk veri çekme işlemi başlatılıyor...");
            new Thread(this::verileriGuncelle).start();
        } else {
            // En eski güncelleme zamanını kontrol et
            var hisseler = repository.findAll();
            LocalDateTime enEskiGuncelleme = hisseler.stream()
                .filter(h -> h.getSonGuncelleme() != null)
                .map(HisseEntity::getSonGuncelleme)
                .min(LocalDateTime::compareTo)
                .orElse(LocalDateTime.MIN);

            long saatFarki = ChronoUnit.HOURS.between(enEskiGuncelleme, LocalDateTime.now());

            if (saatFarki > 6) {
                System.out.printf("⏰ Veriler %d saat önce güncellendi. Otomatik yenileme başlatılıyor...%n", saatFarki);
                new Thread(this::verileriGuncelle).start();
            } else {
                System.out.printf("✅ Veriler güncel (%d saat önce güncellendi). %d hisse mevcut.%n",
                        saatFarki, mevcutSayisi);
            }
        }
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void geceSenkronizasyonu() {
        System.out.println("🌙 Gece senkronizasyonu başlıyor (Tüm BIST)...");
        enflasyonGuncelle();
        verileriGuncelle();
    }

    public String manuelSenkronizasyon() {
        if (syncRunning.get()) {
            return "⚠️ Senkronizasyon zaten devam ediyor. Lütfen bekleyin. " + syncProgressMessage;
        }
        new Thread(this::verileriGuncelle).start();
        return "Senkronizasyon başlatıldı. Toplam " + BistTum.SEMBOLLER.size() +
               " hisse " + THREAD_COUNT + " paralel thread ile çekilecek.";
    }

    public boolean isSyncRunning() {
        return syncRunning.get();
    }

    public double getGuncelEnflasyon() {
        return guncelEnflasyon;
    }

    public String getSyncProgress() {
        return syncProgressMessage;
    }

    private void enflasyonGuncelle() {
        TcmbEvdsClient tcmbClient = new TcmbEvdsClient();
        this.guncelEnflasyon = tcmbClient.yillikEnflasyonHesapla(tcmbApiKey);
        System.out.printf("📈 Enflasyon oranı: %%%.1f%n", guncelEnflasyon * 100);
    }

    /**
     * Paralel veri çekme — 4 thread ile Yahoo Finance'den hisse verileri çeker.
     * Her hisse ayrı bir YahooFinanceClient oturumu kullanır (thread-safe).
     */
    public void verileriGuncelle() {
        if (!syncRunning.compareAndSet(false, true)) {
            System.out.println("⚠️ Senkronizasyon zaten çalışıyor, atlanıyor.");
            return;
        }

        basarili.set(0);
        hatali.set(0);
        int toplam = BistTum.SEMBOLLER.size();

        System.out.printf("📊 Paralel senkronizasyon başlıyor — %d hisse, %d thread...%n", toplam, THREAD_COUNT);

        // Listeyi duplicate'lerden temizle (BistTum'da tekrar eden semboller olabilir)
        List<String> uniqueSemboller = BistTum.SEMBOLLER.stream().distinct().toList();

        ExecutorService executor = Executors.newFixedThreadPool(THREAD_COUNT);

        try {
            // Sembolleri thread sayısına göre parçalara böl
            List<List<String>> parcalar = partitionList(uniqueSemboller, THREAD_COUNT);

            List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < parcalar.size(); i++) {
                final int threadId = i + 1;
                final List<String> parca = parcalar.get(i);
                futures.add(executor.submit(() -> parcaCek(threadId, parca, uniqueSemboller.size())));
            }

            // Tüm thread'lerin bitmesini bekle
            for (Future<?> future : futures) {
                try {
                    future.get(30, TimeUnit.MINUTES); // Max 30 dakika timeout
                } catch (Exception e) {
                    System.err.println("❌ Thread hatası: " + e.getMessage());
                }
            }

        } finally {
            executor.shutdown();
            syncRunning.set(false);
            syncProgressMessage = String.format("✅ Tamamlandı: %d başarılı, %d hatalı",
                    basarili.get(), hatali.get());
            System.out.printf("✅ Senkronizasyon tamamlandı — %d başarılı, %d hatalı, %d toplam.%n",
                    basarili.get(), hatali.get(), uniqueSemboller.size());
        }
    }

    /**
     * Bir thread'in çekeceği hisse parçasını işler.
     * Her thread kendi YahooFinanceClient oturumunu açar.
     */
    private void parcaCek(int threadId, List<String> semboller, int toplamHisse) {
        YahooFinanceClient client = new YahooFinanceClient("5y");
        String threadTag = "[T" + threadId + "]";

        for (String sembol : semboller) {
            try {
                HisseEntity h = client.hisseVerisiCek(sembol);

                synchronized (repository) {
                    HisseEntity entity = repository.findById(sembol).orElse(h);
                    if (entity != h) {
                        entity.setDividendYield(h.getDividendYield());
                        entity.setRoe(h.getRoe());
                        entity.setPayoutRatio(h.getPayoutRatio());
                        entity.setSonFiyat(h.getSonFiyat());
                        entity.setGunlukKapanis(h.getGunlukKapanis());
                        entity.setTemettuGecmisi(h.getTemettuGecmisi());
                        entity.setSektor(h.getSektor());
                        entity.setFk(h.getFk());
                        entity.setPddd(h.getPddd());
                        entity.setNetKarMarji(h.getNetKarMarji());
                        entity.setCiroBuyumesi(h.getCiroBuyumesi());
                        entity.setNetBorcFavoek(h.getNetBorcFavoek());
                        entity.setSerbestNakitAkisi(h.getSerbestNakitAkisi());
                        entity.setArgeCircOrani(h.getArgeCircOrani());
                        entity.setYatirimAmortismanOrani(h.getYatirimAmortismanOrani());
                        entity.setGunlukOrtHacim(h.getGunlukOrtHacim());
                    }

                    entity.setTemettuSayisi(h.getTemettuGecmisi().size());
                    entity.setGunSayisi(h.getGunlukKapanis().size());

                    Map<String, Double> pMap = h.getGunlukKapanis().entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));
                    Map<String, Double> dMap = h.getTemettuGecmisi().entrySet().stream()
                            .collect(Collectors.toMap(e -> e.getKey().toString(), Map.Entry::getValue));

                    entity.setKapanisGecmisiJson(gson.toJson(pMap));
                    entity.setTemettuGecmisiJson(gson.toJson(dMap));
                    entity.setSonGuncelleme(LocalDateTime.now());

                    repository.save(entity);
                }

                int done = basarili.incrementAndGet();
                syncProgressMessage = String.format("%d / %d hisse çekildi", done + hatali.get(), toplamHisse);

                // Rate limit: Yahoo Finance'in IP bazlı sınırına takılmamak için
                Thread.sleep(600);

            } catch (Exception e) {
                hatali.incrementAndGet();
                System.err.printf("%s ❌ %s: %s%n", threadTag, sembol, e.getMessage());
                try { Thread.sleep(300); } catch (InterruptedException ignored) {}
            }
        }
    }

    /**
     * Listeyi eşit parçalara böler.
     */
    private <T> List<List<T>> partitionList(List<T> list, int partitions) {
        List<List<T>> result = new ArrayList<>();
        int size = list.size();
        int chunkSize = (int) Math.ceil((double) size / partitions);

        for (int i = 0; i < size; i += chunkSize) {
            result.add(list.subList(i, Math.min(i + chunkSize, size)));
        }
        return result;
    }
}
