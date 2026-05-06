package com.bist.api;

import com.google.gson.*;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * TCMB EVDS (Elektronik Veri Dağıtım Sistemi) API İstemcisi.
 *
 * TÜFE (Tüketici Fiyat Endeksi) verisini çekerek gerçek yıllık enflasyon
 * oranını hesaplar. API anahtarı gerektirir.
 *
 * Kayıt: https://evds2.tcmb.gov.tr/ → Kayıt Ol → Profilim → API Anahtarı
 * Seri Kodu: TP.FE.OKTG01 (TÜFE Genel, 2003=100)
 */
public class TcmbEvdsClient {

    private static final String EVDS_URL =
        "https://evds2.tcmb.gov.tr/service/evds/series=TP.FE.OKTG01" +
        "&startDate=%s&endDate=%s&type=json&key=%s";

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd-MM-yyyy");
    private static final double FALLBACK_INFLATION = 0.40; // %40 varsayılan

    private final HttpClient httpClient;

    public TcmbEvdsClient() {
        this.httpClient = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NORMAL)
            .connectTimeout(Duration.ofSeconds(10))
            .build();
    }

    /**
     * TCMB EVDS'den son 1 yılın TÜFE verisini çekerek yıllık enflasyon oranını hesaplar.
     *
     * @param apiKey TCMB EVDS API anahtarı
     * @return Yıllık enflasyon oranı (ondalık; 0.58 = %58)
     */
    public double yillikEnflasyonHesapla(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            System.out.println("⚠️  TCMB API anahtarı tanımlanmamış, varsayılan enflasyon kullanılıyor: %" +
                    (int)(FALLBACK_INFLATION * 100));
            return FALLBACK_INFLATION;
        }

        try {
            // Son 13 ay veri çek (12 ay + 1 ay önceki baz ay)
            LocalDate bitisTarih = LocalDate.now();
            LocalDate baslangicTarih = bitisTarih.minusMonths(13);

            String url = String.format(EVDS_URL,
                baslangicTarih.format(DATE_FMT),
                bitisTarih.format(DATE_FMT),
                apiKey);

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                System.err.printf("⚠️  TCMB EVDS API hatası: HTTP %d%n", response.statusCode());
                return FALLBACK_INFLATION;
            }

            return parseEnflasyon(response.body());

        } catch (Exception e) {
            System.err.printf("⚠️  TCMB enflasyon verisi çekilemedi: %s — Varsayılan kullanılıyor.%n", e.getMessage());
            return FALLBACK_INFLATION;
        }
    }

    /**
     * EVDS JSON yanıtından yıllık enflasyonu hesaplar.
     * Formül: (Son Ay TÜFE / 12 Ay Önceki TÜFE) - 1
     */
    private double parseEnflasyon(String json) {
        try {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonArray items = root.getAsJsonArray("items");

            if (items == null || items.size() < 2) {
                System.err.println("⚠️  TCMB EVDS'den yeterli veri gelmedi.");
                return FALLBACK_INFLATION;
            }

            // İlk ve son elemanı al (en eski ve en yeni TÜFE endeksi)
            double ilkTufe = 0, sonTufe = 0;

            // İlk geçerli ve son geçerli değerleri bul
            for (int i = 0; i < items.size(); i++) {
                JsonObject item = items.get(i).getAsJsonObject();
                JsonElement val = item.get("TP_FE_OKTG01");
                if (val != null && !val.isJsonNull()) {
                    double tufe = val.getAsDouble();
                    if (ilkTufe == 0) ilkTufe = tufe;
                    sonTufe = tufe;
                }
            }

            if (ilkTufe <= 0 || sonTufe <= 0) {
                return FALLBACK_INFLATION;
            }

            double yillikEnflasyon = (sonTufe / ilkTufe) - 1.0;

            System.out.printf("📈 TCMB TÜFE verisi: İlk=%.2f, Son=%.2f → Yıllık Enflasyon=%%%.1f%n",
                    ilkTufe, sonTufe, yillikEnflasyon * 100);

            return yillikEnflasyon;

        } catch (Exception e) {
            System.err.printf("⚠️  TÜFE verisi parse hatası: %s%n", e.getMessage());
            return FALLBACK_INFLATION;
        }
    }

    /**
     * Varsayılan enflasyon oranını döner (API key yoksa veya hata durumunda).
     */
    public static double fallbackEnflasyon() {
        return FALLBACK_INFLATION;
    }
}
