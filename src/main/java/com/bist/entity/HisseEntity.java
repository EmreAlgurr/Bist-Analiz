package com.bist.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    // Geçmiş verileri basit tutmak için JSON formatında TEXT alanlarda tutuyoruz
    @Column(columnDefinition = "TEXT")
    private String kapanisGecmisiJson;

    @Column(columnDefinition = "TEXT")
    private String temettuGecmisiJson;

    private LocalDateTime sonGuncelleme;

    // Getters & Setters
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

    public String getKapanisGecmisiJson() { return kapanisGecmisiJson; }
    public void setKapanisGecmisiJson(String kapanisGecmisiJson) { this.kapanisGecmisiJson = kapanisGecmisiJson; }

    public String getTemettuGecmisiJson() { return temettuGecmisiJson; }
    public void setTemettuGecmisiJson(String temettuGecmisiJson) { this.temettuGecmisiJson = temettuGecmisiJson; }

    public LocalDateTime getSonGuncelleme() { return sonGuncelleme; }
    public void setSonGuncelleme(LocalDateTime sonGuncelleme) { this.sonGuncelleme = sonGuncelleme; }
}
