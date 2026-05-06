package com.bist.api;

import com.bist.entity.HisseEntity;
import com.google.gson.*;

import java.io.IOException;
import java.net.*;
import java.net.http.*;
import java.time.*;
import java.util.*;
import java.util.regex.*;

/**
 * Yahoo Finance REST endpoint'leri üzerinden BIST hisse verisi çeken istemci.
 * <p>
 * v3.1: Strateji filtreleri için ek finansal rasyolar çekiliyor:
 * F/K, PD/DD, Net Kâr Marjı, Ciro Büyümesi, Serbest Nakit Akışı,
 * Hacim, Sektör bilgisi vb.
 */
public final class YahooFinanceClient {

    private static final String CHART_URL = "https://query1.finance.yahoo.com/v8/finance/chart/%s"
            + "?range=%s&interval=1d&events=div";

    private static final String SUMMARY_URL = "https://query1.finance.yahoo.com/v10/finance/quoteSummary/%s"
            + "?modules=defaultKeyStatistics,financialData,summaryDetail,summaryProfile,incomeStatementHistory"
            + "&crumb=%s";

    private static final String CRUMB_URL = "https://query2.finance.yahoo.com/v1/test/getcrumb";

    private static final String CONSENT_URL = "https://fc.yahoo.com/cusc/t";

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36";

    private final HttpClient httpClient;
    private final Gson gson;
    private final String range;
    private final CookieManager cookieManager;
    private String crumb;

    public YahooFinanceClient() {
        this("5y");
    }

    public YahooFinanceClient(String range) {
        this.cookieManager = new CookieManager();
        this.cookieManager.setCookiePolicy(CookiePolicy.ACCEPT_ALL);
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(15))
                .cookieHandler(cookieManager)
                .build();
        this.gson = new Gson();
        this.range = range;
    }

    /**
     * Verilen sembol için hem geçmiş fiyat/temettü hem de rasyoları
     * tek bir {@link HisseEntity} nesnesinde birleştirir.
     */
    public HisseEntity hisseVerisiCek(String sembol) throws IOException, InterruptedException {
        System.out.printf("  ⏳  %s verisi çekiliyor...%n", sembol);

        HisseEntity hisse = new HisseEntity();
        hisse.setSembol(sembol);

        // Map'leri başlat (null pointer almamak için)
        hisse.setGunlukKapanis(new TreeMap<>());
        hisse.setTemettuGecmisi(new TreeMap<>());

        // 1) Geçmiş fiyatlar ve temettüler
        gecmisFiyatVeTemettuCek(hisse);

        // 2) Finansal rasyolar (crumb ile) — v3.1 genişletilmiş
        rasyolariCek(hisse);

        System.out.printf("  ✅  %s — %d gün, %d temettü, yield=%.2f%%, roe=%.2f%%, fk=%.1f, sektor=%s%n",
                sembol,
                hisse.getGunlukKapanis().size(),
                hisse.getTemettuGecmisi().size(),
                hisse.getDividendYield() * 100,
                hisse.getRoe() * 100,
                hisse.getFk(),
                hisse.getSektor() != null ? hisse.getSektor() : "N/A");

        return hisse;
    }

    // ── Yahoo Oturum Yönetimi ────────────────────────────────────────

    private void oturumBaslat() throws IOException, InterruptedException {
        if (crumb != null)
            return;

        System.out.println("  🔑  Yahoo Finance oturumu başlatılıyor...");

        try {
            httpGetRaw(CONSENT_URL);
        } catch (IOException e) {
            // 404 dönebilir, sorun yok — cookie alınmış olur
        }

        String crumbResponse = httpGetRaw(CRUMB_URL);
        if (crumbResponse == null || crumbResponse.isBlank()) {
            System.err.println("  ⚠️  Crumb alınamadı, rasyolar çekilemeyebilir.");
            return;
        }

        this.crumb = crumbResponse.trim();
        System.out.printf("  🔑  Crumb alındı: %s%n", crumb);
    }

    // ── Geçmiş Fiyat + Temettü ───────────────────────────────────────

    private void gecmisFiyatVeTemettuCek(HisseEntity hisse) throws IOException, InterruptedException {
        String url = String.format(CHART_URL, hisse.getSembol(), range);
        String json = httpGet(url);

        JsonObject root = JsonParser.parseString(json).getAsJsonObject();
        JsonObject chart = root.getAsJsonObject("chart");

        JsonElement error = chart.get("error");
        if (error != null && !error.isJsonNull()) {
            throw new IOException("Yahoo Finance hata döndürdü: " + error);
        }

        JsonObject result = chart.getAsJsonArray("result").get(0).getAsJsonObject();

        // ── Kapanış fiyatları ──
        JsonArray timestamps = result.getAsJsonArray("timestamp");
        JsonObject indicators = result.getAsJsonObject("indicators");
        JsonArray closes = indicators
                .getAsJsonArray("quote").get(0).getAsJsonObject()
                .getAsJsonArray("close");

        // ── Hacim verileri (günlük ortalama hacim hesabı için) ──
        JsonArray volumes = null;
        try {
            volumes = indicators
                    .getAsJsonArray("quote").get(0).getAsJsonObject()
                    .getAsJsonArray("volume");
        } catch (Exception ignored) {
        }

        double totalVolume = 0;
        int volumeCount = 0;

        if (timestamps != null && closes != null) {
            for (int i = 0; i < timestamps.size(); i++) {
                JsonElement closeEl = closes.get(i);
                if (closeEl.isJsonNull())
                    continue;

                long epoch = timestamps.get(i).getAsLong();
                LocalDate tarih = Instant.ofEpochSecond(epoch)
                        .atZone(ZoneId.of("Europe/Istanbul"))
                        .toLocalDate();
                double fiyat = closeEl.getAsDouble();
                hisse.getGunlukKapanis().put(tarih, fiyat);

                // Hacim toplama (son 30 gün benzeri yaklaşım)
                if (volumes != null && i < volumes.size() && !volumes.get(i).isJsonNull()) {
                    totalVolume += volumes.get(i).getAsDouble() * fiyat;
                    volumeCount++;
                }
            }
        }

        // Günlük ortalama hacim (TL cinsinden)
        if (volumeCount > 0) {
            hisse.setGunlukOrtHacim(totalVolume / volumeCount);
        }

        // ── Son Fiyat: Her zaman kapanış verisinden set et ──
        if (!hisse.getGunlukKapanis().isEmpty()) {
            hisse.setSonFiyat(hisse.getGunlukKapanis().lastEntry().getValue());
        }

        // Chart meta'dan anlık fiyatı almayı dene (daha güncel olabilir)
        try {
            JsonObject meta = result.getAsJsonObject("meta");
            if (meta != null && meta.has("regularMarketPrice") && !meta.get("regularMarketPrice").isJsonNull()) {
                double marketPrice = meta.get("regularMarketPrice").getAsDouble();
                if (marketPrice > 0) {
                    hisse.setSonFiyat(marketPrice);
                }
            }
        } catch (Exception ignored) {
        }

        // ── Temettü olayları ──
        JsonObject events = result.getAsJsonObject("events");
        if (events != null && events.has("dividends")) {
            JsonObject divs = events.getAsJsonObject("dividends");
            for (String key : divs.keySet()) {
                JsonObject div = divs.getAsJsonObject(key);
                long epoch = div.get("date").getAsLong();
                double amount = div.get("amount").getAsDouble();

                LocalDate tarih = Instant.ofEpochSecond(epoch)
                        .atZone(ZoneId.of("Europe/Istanbul"))
                        .toLocalDate();
                hisse.getTemettuGecmisi().put(tarih, amount);
            }
        }

        // ── Chart meta'dan temel bilgileri çıkarmayı dene ──
        rasyolariChartMetadanCikar(hisse, result);
    }

    // ── Chart Meta'dan Rasyo Çıkarma (Yedek) ────────────────────────

    private void rasyolariChartMetadanCikar(HisseEntity hisse, JsonObject chartResult) {
        try {
            JsonObject meta = chartResult.getAsJsonObject("meta");
            if (meta == null)
                return;

            if (meta.has("dividendYield") && !meta.get("dividendYield").isJsonNull()) {
                double yield = meta.get("dividendYield").getAsDouble();
                if (hisse.getDividendYield() == 0.0 && yield > 0) {
                    hisse.setDividendYield(yield);
                }
            }
        } catch (Exception ignored) {
        }
    }

    // ── Finansal Rasyolar (Crumb ile) — v3.1 Genişletilmiş ──────────

    private void rasyolariCek(HisseEntity hisse) throws IOException, InterruptedException {
        try {
            oturumBaslat();

            if (crumb == null) {
                rasyolariGecmistenHesapla(hisse);
                return;
            }

            String url = String.format(SUMMARY_URL, hisse.getSembol(), crumb);
            String json = httpGet(url);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            JsonObject summary = root.getAsJsonObject("quoteSummary");

            JsonElement errorEl = summary.get("error");
            if (errorEl != null && !errorEl.isJsonNull()) {
                rasyolariGecmistenHesapla(hisse);
                return;
            }

            JsonArray resultArr = summary.getAsJsonArray("result");
            if (resultArr == null || resultArr.isEmpty()) {
                rasyolariGecmistenHesapla(hisse);
                return;
            }

            JsonObject result = resultArr.get(0).getAsJsonObject();

            // ── summaryDetail: yield, payout, F/K ──
            Optional.ofNullable(result.getAsJsonObject("summaryDetail"))
                    .ifPresent(sd -> {
                        double yield = rawDouble(sd, "dividendYield");
                        double payout = rawDouble(sd, "payoutRatio");
                        double trailingPE = rawDouble(sd, "trailingPE");

                        if (yield > 0)
                            hisse.setDividendYield(yield);
                        if (payout > 0)
                            hisse.setPayoutRatio(payout);
                        if (trailingPE > 0)
                            hisse.setFk(trailingPE);
                    });

            // ── financialData: ROE, margins, revenue growth, FCF ──
            Optional.ofNullable(result.getAsJsonObject("financialData"))
                    .ifPresent(fd -> {
                        double roe = rawDouble(fd, "returnOnEquity");
                        double profitMargin = rawDouble(fd, "profitMargins");
                        double revenueGrowth = rawDouble(fd, "revenueGrowth");
                        double freeCashflow = rawDouble(fd, "freeCashflow");
                        double totalDebt = rawDouble(fd, "totalDebt");
                        double ebitda = rawDouble(fd, "ebitda");
                        double totalRevenue = rawDouble(fd, "totalRevenue");

                        if (roe > 0)
                            hisse.setRoe(roe);
                        if (profitMargin != 0)
                            hisse.setNetKarMarji(profitMargin);
                        if (revenueGrowth != 0)
                            hisse.setCiroBuyumesi(revenueGrowth);
                        if (freeCashflow != 0)
                            hisse.setSerbestNakitAkisi(freeCashflow);

                        // Net Borç / FAVÖK hesaplama
                        if (ebitda > 0 && totalDebt > 0) {
                            hisse.setNetBorcFavoek(totalDebt / ebitda);
                        }
                    });

            // ── defaultKeyStatistics: PD/DD, payout fallback ──
            Optional.ofNullable(result.getAsJsonObject("defaultKeyStatistics"))
                    .ifPresent(ks -> {
                        double priceToBook = rawDouble(ks, "priceToBook");
                        if (priceToBook > 0)
                            hisse.setPddd(priceToBook);

                        if (hisse.getPayoutRatio() == 0.0) {
                            double payout = rawDouble(ks, "payoutRatio");
                            if (payout > 0)
                                hisse.setPayoutRatio(payout);
                        }

                        // F/K fallback
                        if (hisse.getFk() == 0.0) {
                            double forwardPE = rawDouble(ks, "forwardPE");
                            if (forwardPE > 0)
                                hisse.setFk(forwardPE);
                        }
                    });

            // ── summaryProfile: Sektör ──
            Optional.ofNullable(result.getAsJsonObject("summaryProfile"))
                    .ifPresent(sp -> {
                        if (sp.has("sector") && !sp.get("sector").isJsonNull()) {
                            hisse.setSektor(sp.get("sector").getAsString());
                        }
                    });

            if (hisse.getDividendYield() == 0.0 || hisse.getRoe() == 0.0) {
                rasyolariGecmistenHesapla(hisse);
            }

        } catch (Exception e) {
            rasyolariGecmistenHesapla(hisse);
        }
    }

    // ── Geçmiş Veriden Rasyo Hesaplama (Fallback) ────────────────────

    private void rasyolariGecmistenHesapla(HisseEntity hisse) {
        try {
            var temettuMap = hisse.getTemettuGecmisi();
            var kapanisMap = hisse.getGunlukKapanis();

            if (temettuMap.isEmpty() || kapanisMap.isEmpty())
                return;

            LocalDate birYilOnce = kapanisMap.lastKey().minusYears(1);
            double sonBirYilTemettu = temettuMap.entrySet().stream()
                    .filter(e -> !e.getKey().isBefore(birYilOnce))
                    .mapToDouble(Map.Entry::getValue)
                    .sum();

            double sonFiyat = kapanisMap.lastEntry().getValue();
            hisse.setSonFiyat(sonFiyat);

            if (hisse.getDividendYield() == 0.0 && sonFiyat > 0) {
                hisse.setDividendYield(sonBirYilTemettu / sonFiyat);
            }

        } catch (Exception e) {
            System.err.printf("  ⚠️  %s geçmişten rasyo hesaplama hatası: %s%n",
                    hisse.getSembol(), e.getMessage());
        }
    }

    // ── Yardımcılar ──────────────────────────────────────────────────

    private double rawDouble(JsonObject parent, String key) {
        JsonElement el = parent.get(key);
        if (el == null || el.isJsonNull())
            return 0.0;

        if (el.isJsonObject()) {
            JsonElement raw = el.getAsJsonObject().get("raw");
            return (raw != null && !raw.isJsonNull()) ? raw.getAsDouble() : 0.0;
        }
        try {
            return el.getAsDouble();
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private String httpGet(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json")
                .GET()
                .timeout(Duration.ofSeconds(30))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(String.format(
                    "HTTP %d — %s", response.statusCode(), url));
        }

        return response.body();
    }

    /**
     * HTTP GET — durum kodunu kontrol etmez (cookie toplama amaçlı).
     */
    private String httpGetRaw(String url) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", USER_AGENT)
                .header("Accept", "*/*")
                .GET()
                .timeout(Duration.ofSeconds(15))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException(String.format(
                    "HTTP %d — %s", response.statusCode(), url));
        }

        return response.body();
    }
}
