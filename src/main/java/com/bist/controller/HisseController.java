package com.bist.controller;

import com.bist.entity.HisseEntity;
import com.bist.repository.HisseRepository;
import com.bist.service.HisseService;
import com.bist.service.StrategyScreenerService;
import com.bist.service.SyncService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HisseController {

    private final HisseService service;
    private final StrategyScreenerService strategyService;
    private final SyncService syncService;
    private final HisseRepository hisseRepository;

    public HisseController(HisseService service,
                           StrategyScreenerService strategyService,
                           SyncService syncService,
                           HisseRepository hisseRepository) {
        this.service = service;
        this.strategyService = strategyService;
        this.syncService = syncService;
        this.hisseRepository = hisseRepository;
    }

    @PostMapping("/tara")
    public Map<String, String> taramaBaslat() {
        String sessionId = service.taramaBaslat();
        return Map.of("sessionId", sessionId);
    }

    @GetMapping("/tara/{id}")
    public ResponseEntity<HisseService.TaramaDurum> taramaDurumu(@PathVariable("id") String id) {
        var durum = service.taramaDurumu(id);
        if (durum == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(durum);
    }

    @PostMapping("/drip")
    public ResponseEntity<HisseService.DripSonuc> dripSimulasyonu(
            @RequestBody Map<String, Object> body) {
        String sessionId = (String) body.get("sessionId");
        String sembol = (String) body.get("sembol");
        double sermaye = body.containsKey("sermaye")
                ? ((Number) body.get("sermaye")).doubleValue()
                : 80000.0;
        double aylikEkGirdi = body.containsKey("aylikEkGirdi")
                ? ((Number) body.get("aylikEkGirdi")).doubleValue()
                : 0.0;

        var sonuc = service.dripCalistir(sessionId, sembol, sermaye, aylikEkGirdi);
        if (sonuc == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(sonuc);
    }

    // ── Strateji Endpoint'leri ────────────────────────────────

    @GetMapping("/strateji/temettu-devleri")
    public ResponseEntity<StrategyScreenerService.StrategyResult> temettuDevleri(
            @RequestParam Map<String, String> params) {
        var result = strategyService.temettuDevleri(params);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/strateji/agresif-buyume")
    public ResponseEntity<StrategyScreenerService.StrategyResult> agresifBuyume(
            @RequestParam Map<String, String> params) {
        var result = strategyService.agresifBuyume(params);
        return ResponseEntity.ok(result);
    }

    // ── Senkronizasyon Endpoint'leri ─────────────────────────

    @PostMapping("/sync")
    public Map<String, String> manuelSync() {
        String mesaj = syncService.manuelSenkronizasyon();
        return Map.of("mesaj", mesaj);
    }

    @GetMapping("/sync/status")
    public Map<String, Object> syncStatus() {
        Map<String, Object> result = new HashMap<>();
        result.put("running", syncService.isSyncRunning());
        result.put("enflasyon", syncService.getGuncelEnflasyon());
        result.put("progress", syncService.getSyncProgress());
        return result;
    }

    // ── Hisse Detay & Manuel Güncelleme ──────────────────────

    @GetMapping("/hisse/{sembol}")
    public ResponseEntity<HisseEntity> getHisse(@PathVariable("sembol") String sembol) {
        return hisseRepository.findById(sembol)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/hisse/{sembol}")
    public ResponseEntity<Map<String, String>> updateHisse(
            @PathVariable("sembol") String sembol,
            @RequestBody Map<String, Object> body) {
        var opt = hisseRepository.findById(sembol);
        if (opt.isEmpty()) return ResponseEntity.notFound().build();

        HisseEntity e = opt.get();

        if (body.containsKey("dividendYield"))     e.setDividendYield(toDouble(body.get("dividendYield")));
        if (body.containsKey("roe"))               e.setRoe(toDouble(body.get("roe")));
        if (body.containsKey("payoutRatio"))        e.setPayoutRatio(toDouble(body.get("payoutRatio")));
        if (body.containsKey("sonFiyat"))           e.setSonFiyat(toDouble(body.get("sonFiyat")));
        if (body.containsKey("fk"))                e.setFk(toDouble(body.get("fk")));
        if (body.containsKey("pddd"))              e.setPddd(toDouble(body.get("pddd")));
        if (body.containsKey("netKarMarji"))        e.setNetKarMarji(toDouble(body.get("netKarMarji")));
        if (body.containsKey("ciroBuyumesi"))       e.setCiroBuyumesi(toDouble(body.get("ciroBuyumesi")));
        if (body.containsKey("netBorcFavoek"))      e.setNetBorcFavoek(toDouble(body.get("netBorcFavoek")));
        if (body.containsKey("serbestNakitAkisi"))  e.setSerbestNakitAkisi(toDouble(body.get("serbestNakitAkisi")));
        if (body.containsKey("sektor"))             e.setSektor((String) body.get("sektor"));
        if (body.containsKey("temettuSayisi"))      e.setTemettuSayisi(((Number) body.get("temettuSayisi")).intValue());

        e.setSonGuncelleme(java.time.LocalDateTime.now());
        hisseRepository.save(e);

        return ResponseEntity.ok(Map.of("mesaj", sembol + " başarıyla güncellendi."));
    }

    private double toDouble(Object val) {
        if (val instanceof Number) return ((Number) val).doubleValue();
        return Double.parseDouble(val.toString());
    }
}
