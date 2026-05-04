package com.bist.controller;

import com.bist.service.HisseService;
import com.bist.service.PortfolioSimulationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HisseController {

    private final HisseService service;
    private final PortfolioSimulationService portfolioService;

    public HisseController(HisseService service, PortfolioSimulationService portfolioService) {
        this.service = service;
        this.portfolioService = portfolioService;
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

    @PostMapping("/portfolio")
    public ResponseEntity<PortfolioSimulationService.PortfolioSonuc> portfolioSimulasyonu(
            @RequestBody Map<String, Object> body) {
        @SuppressWarnings("unchecked")
        List<String> semboller = (List<String>) body.get("semboller");
        double sermaye = body.containsKey("sermaye")
                ? ((Number) body.get("sermaye")).doubleValue()
                : 80000.0;
        double aylikEkGirdi = body.containsKey("aylikEkGirdi")
                ? ((Number) body.get("aylikEkGirdi")).doubleValue()
                : 0.0;

        try {
            var sonuc = portfolioService.simulatePortfolio(semboller, sermaye, aylikEkGirdi);
            return ResponseEntity.ok(sonuc);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().build();
        }
    }
}
