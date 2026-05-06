package com.bist.entity;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.TreeMap;

@Entity
@Table(name = "hisseler")
public class HisseEntity {

    @Id
    @Column(name = "sembol", nullable = false, unique = true)
    private String sembol;

    private double dividendYield;
    private double roe;
    private double payoutRatio;
    private double sonFiyat;
    private int temettuSayisi;
    private int gunSayisi;

    // ── v3.1: Strateji Filtresi İçin Yeni Alanlar ──
    private String sektor;          // Sektör bilgisi (Yahoo'dan "sector")
    private double fk;              // Fiyat/Kazanç (P/E, trailingPE)
    private double pddd;            // PD/DD (Price-to-Book, priceToBook)
    private double netKarMarji;     // Net Kâr Marjı (profitMargins)
    private double ciroBuyumesi;    // Yıllık Ciro Büyümesi (revenueGrowth)
    private double netBorcFavoek;   // Net Borç / FAVÖK
    private double serbestNakitAkisi; // Serbest Nakit Akışı (freeCashflow)
    private double argeCircOrani;   // Ar-Ge / Ciro oranı
    private double yatirimAmortismanOrani; // Yatırım / Amortisman (CapEx/Depreciation)
    private double gunlukOrtHacim;  // Günlük Ort İşlem Hacmi (averageDailyVolume10Day * fiyat)

    // Geçmiş verileri basit tutmak için JSON formatında TEXT alanlarda tutuyoruz
    @Column(columnDefinition = "TEXT")
    private String kapanisGecmisiJson;

    @Column(columnDefinition = "TEXT")
    private String temettuGecmisiJson;

    private LocalDateTime sonGuncelleme;

    @Transient
    private TreeMap<LocalDate, Double> gunlukKapanis = new TreeMap<>();

    @Transient
    private TreeMap<LocalDate, Double> temettuGecmisi = new TreeMap<>();

    // ── Getters & Setters ──

    public String getSembol() { return sembol; }
    public void setSembol(String sembol) { this.sembol = sembol; }

    public double getDividendYield() { return dividendYield; }
    public void setDividendYield(double dividendYield) { this.dividendYield = dividendYield; }

    public double getRoe() { return roe; }
    public void setRoe(double roe) { this.roe = roe; }

    public double getPayoutRatio() { return payoutRatio; }
    public void setPayoutRatio(double payoutRatio) { this.payoutRatio = payoutRatio; }

    public double getSonFiyat() { return sonFiyat; }
    public void setSonFiyat(double sonFiyat) { this.sonFiyat = sonFiyat; }

    public int getTemettuSayisi() { return temettuSayisi; }
    public void setTemettuSayisi(int temettuSayisi) { this.temettuSayisi = temettuSayisi; }

    public int getGunSayisi() { return gunSayisi; }
    public void setGunSayisi(int gunSayisi) { this.gunSayisi = gunSayisi; }

    // ── v3.1 Getters & Setters ──

    public String getSektor() { return sektor; }
    public void setSektor(String sektor) { this.sektor = sektor; }

    public double getFk() { return fk; }
    public void setFk(double fk) { this.fk = fk; }

    public double getPddd() { return pddd; }
    public void setPddd(double pddd) { this.pddd = pddd; }

    public double getNetKarMarji() { return netKarMarji; }
    public void setNetKarMarji(double netKarMarji) { this.netKarMarji = netKarMarji; }

    public double getCiroBuyumesi() { return ciroBuyumesi; }
    public void setCiroBuyumesi(double ciroBuyumesi) { this.ciroBuyumesi = ciroBuyumesi; }

    public double getNetBorcFavoek() { return netBorcFavoek; }
    public void setNetBorcFavoek(double netBorcFavoek) { this.netBorcFavoek = netBorcFavoek; }

    public double getSerbestNakitAkisi() { return serbestNakitAkisi; }
    public void setSerbestNakitAkisi(double serbestNakitAkisi) { this.serbestNakitAkisi = serbestNakitAkisi; }

    public double getArgeCircOrani() { return argeCircOrani; }
    public void setArgeCircOrani(double argeCircOrani) { this.argeCircOrani = argeCircOrani; }

    public double getYatirimAmortismanOrani() { return yatirimAmortismanOrani; }
    public void setYatirimAmortismanOrani(double yatirimAmortismanOrani) { this.yatirimAmortismanOrani = yatirimAmortismanOrani; }

    public double getGunlukOrtHacim() { return gunlukOrtHacim; }
    public void setGunlukOrtHacim(double gunlukOrtHacim) { this.gunlukOrtHacim = gunlukOrtHacim; }

    // ── JSON & Transient ──

    public String getKapanisGecmisiJson() { return kapanisGecmisiJson; }
    public void setKapanisGecmisiJson(String kapanisGecmisiJson) { this.kapanisGecmisiJson = kapanisGecmisiJson; }

    public String getTemettuGecmisiJson() { return temettuGecmisiJson; }
    public void setTemettuGecmisiJson(String temettuGecmisiJson) { this.temettuGecmisiJson = temettuGecmisiJson; }

    public LocalDateTime getSonGuncelleme() { return sonGuncelleme; }
    public void setSonGuncelleme(LocalDateTime sonGuncelleme) { this.sonGuncelleme = sonGuncelleme; }

    public TreeMap<LocalDate, Double> getGunlukKapanis() { return gunlukKapanis; }
    public void setGunlukKapanis(TreeMap<LocalDate, Double> gunlukKapanis) { this.gunlukKapanis = gunlukKapanis; }

    public TreeMap<LocalDate, Double> getTemettuGecmisi() { return temettuGecmisi; }
    public void setTemettuGecmisi(TreeMap<LocalDate, Double> temettuGecmisi) { this.temettuGecmisi = temettuGecmisi; }
}
