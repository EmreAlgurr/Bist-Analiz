package com.bist.controller;

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

    public HisseController(HisseService service,
                           StrategyScreenerService strategyService,
                           SyncService syncService) {
        this.service = service;
        this.strategyService = strategyService;
        this.syncService = syncService;
    }

    @PostMapping("/tara")
    public Map<String, String> taramaBaslat() {
        String sessionId = service.taramaBaslat();
        return Map.of("sessionId", sessionId);
    }

    @GetMapping("/tara/{id}")
    public ResponseEntity<HisseService.TaramaDurum> taramaDurumu(@PathVariable String id) {
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
    public ResponseEntity<StrategyScreenerService.StrategyResult> temettuDevleri() {
        var result = strategyService.temettuDevleri();
        return ResponseEntity.ok(result);
    }

    @GetMapping("/strateji/agresif-buyume")
    public ResponseEntity<StrategyScreenerService.StrategyResult> agresifBuyume() {
        var result = strategyService.agresifBuyume();
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
}
