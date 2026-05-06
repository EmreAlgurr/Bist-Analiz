package com.bist.data;

import java.util.List;
import java.util.stream.Collectors;

/**
 * BIST'te işlem gören tüm hisse sembolleri (Yahoo Finance formatı).
 * Kaynak: BIST-100, BIST-50, BIST-30, BIST Yıldız Pazar ve BIST Ana Pazar.
 *
 * Not: Yahoo Finance'de BIST hisseleri ".IS" eki ile listelenir.
 * Tekrar eden semboller otomatik olarak filtrelenir.
 */
public final class BistTum {

    private BistTum() {}

    /**
     * Tüm BIST sembolleri — tekrar edenler .distinct() ile filtrelenir.
     */
    public static final List<String> SEMBOLLER = List.of(
        // ── BIST-30 ──
        "AKBNK.IS","ARCLK.IS","ASELS.IS","BIMAS.IS","EKGYO.IS",
        "EREGL.IS","FROTO.IS","GARAN.IS","GUBRF.IS","HALKB.IS",
        "ISCTR.IS","KCHOL.IS","KOZAA.IS","KOZAL.IS","KRDMD.IS",
        "MGROS.IS","PETKM.IS","PGSUS.IS","SAHOL.IS","SASA.IS",
        "SISE.IS","TAVHL.IS","TCELL.IS","THYAO.IS","TKFEN.IS",
        "TOASO.IS","TRGYO.IS","TTKOM.IS","TUPRS.IS","YKBNK.IS",

        // ── BIST-50 Ek ──
        "AEFES.IS","AGESA.IS","AKSA.IS","AKSEN.IS","ALARK.IS",
        "ALFAS.IS","ANSGR.IS","CCOLA.IS","DOAS.IS","DOHOL.IS",
        "ENKAI.IS","GESAN.IS","HEKTS.IS","ISGYO.IS","MAVI.IS",
        "ODAS.IS","OTKAR.IS","SOKM.IS","TTRAK.IS","VAKBN.IS",

        // ── BIST-100 Ek ──
        "AHGAZ.IS","AKCNS.IS","AKFGY.IS","ALGYO.IS","ALKIM.IS",
        "ASUZU.IS","BERA.IS","BFREN.IS","BIOEN.IS","BRISA.IS",
        "BRYAT.IS","BUCIM.IS","CIMSA.IS","CWENE.IS","ECILC.IS",
        "EGEEN.IS","ENJSA.IS","GLYHO.IS","GOLTS.IS","KAYSE.IS",
        "KMPUR.IS","KONTR.IS","KONYA.IS","KORDS.IS","PENTA.IS",
        "SMRTG.IS","SKBNK.IS","TABGD.IS","TATGD.IS","TURSG.IS",
        "ULKER.IS","VESBE.IS","VESTL.IS","LOGO.IS","MPARK.IS",
        "NETAS.IS","SARKY.IS","SNGYO.IS","TMSN.IS","YEOTK.IS",
        "ZOREN.IS","EUPWR.IS","KLSER.IS","AGHOL.IS","ANHYT.IS",
        "BTCIM.IS","DENGE.IS","QUAGR.IS",

        // ── BIST Yıldız Pazar Ek ──
        "ADEL.IS","AKENR.IS","ALBRK.IS","ALCAR.IS","ANACM.IS",
        "ARDYZ.IS","ARENA.IS","ARMDA.IS","ARSAN.IS","AYES.IS",
        "AYDEM.IS","BAGFS.IS","BASGZ.IS","BINHO.IS","BIENY.IS",
        "BJKAS.IS","BOBET.IS","BORLS.IS","CANTE.IS","CASA.IS",
        "CEMAS.IS","CEMTS.IS","CLEBI.IS","CUSAN.IS",
        "CVKMD.IS","DESA.IS","DEVA.IS","DGNMO.IS","DYOBY.IS",
        "ECZYT.IS","EDIP.IS","EGEPO.IS","EGPRO.IS","EKCUM.IS",
        "EMKEL.IS","ENERY.IS","EPLAS.IS","ERBOS.IS",
        "EUREN.IS","FATIH.IS","FENER.IS","FLAP.IS","FONET.IS",
        "FORTE.IS","GEDIK.IS","GEDZA.IS","GENTS.IS","GEREL.IS",
        "GWIND.IS","HATEK.IS","HTTBT.IS","HURGZ.IS","HUNER.IS",
        "INDES.IS","INFO.IS","INTEM.IS","IPEKE.IS","ISMEN.IS",
        "ISSEN.IS","IZENR.IS","JANTS.IS","KAPLM.IS","KARTN.IS",
        "KATMR.IS","KERVT.IS","KFEIN.IS","KLMSN.IS","KNFRT.IS",
        "KOTON.IS","KRVGD.IS","KUTPO.IS","LKMNH.IS","LMKDC.IS",
        "LUKSK.IS","MACKO.IS","MAKIM.IS","MANAS.IS","MEGAP.IS",
        "MEPET.IS","MERCN.IS","MERIT.IS","METRO.IS","MIATK.IS",
        "MOBTL.IS","MTRKS.IS","NATEN.IS","NTGAZ.IS","NUGYO.IS",
        "OBASE.IS","OBAMS.IS","ORGE.IS","OSTIM.IS","OYYAT.IS",
        "OYAYO.IS","OZKGY.IS","PAPIL.IS","PATEK.IS","PCILT.IS",
        "PENGD.IS","PINSU.IS","PKART.IS","PLTUR.IS","POLHO.IS",
        "POLTK.IS","PRDGS.IS","PRKAB.IS","PRKME.IS",
        "RALYH.IS","RAYSG.IS","REEDR.IS","RGYAS.IS","RODRG.IS",
        "ROYAL.IS","RUBNS.IS","SAFKR.IS","SAMAT.IS","SANEL.IS",
        "SANFM.IS","SANKO.IS","SELEC.IS","SILVR.IS","SRVGY.IS",
        "SMART.IS","SNICA.IS","SNKRN.IS","SODSN.IS","SOKE.IS",
        "SUMAS.IS","SURGY.IS","TEKTU.IS","TERA.IS","TGSAS.IS",
        "TLMAN.IS","TMPOL.IS","TNZTP.IS","TRILC.IS","TSGYO.IS",
        "TSPOR.IS","TUCLK.IS","TUKAS.IS","TUREX.IS","ULUUN.IS",
        "UMPAS.IS","USAK.IS","VAKFN.IS","VERUS.IS","VKGYO.IS",
        "YAPRK.IS","YATAS.IS","YGGYO.IS","YKSLN.IS","YUNSA.IS",

        // ── BIST Ana Pazar Ek ──
        "ACSEL.IS","ADESE.IS","AFYON.IS","AGROT.IS",
        "AHSGY.IS","AKFYE.IS","AKGRT.IS","AKSGY.IS","AKSUE.IS",
        "AKYHO.IS","ALCTL.IS","ALTNY.IS","ALMAD.IS","AVHOL.IS",
        "AVOD.IS","AVPGY.IS","AVTUR.IS","AYCES.IS","BANVT.IS",
        "BARMA.IS","BAYRK.IS","BELEN.IS","BEYAZ.IS","BLCYT.IS",
        "BMSCH.IS","BMSTL.IS","BNTAS.IS","BOSSA.IS","BRMEN.IS",
        "BRKSN.IS","BRKVY.IS","BRLSM.IS","BURCE.IS","BURVA.IS",
        "CELHA.IS","CEOEM.IS","CGCAM.IS","CONSE.IS","COSMO.IS",
        "CRDFA.IS","CRFSA.IS","DAGI.IS","DAPGM.IS","DARDL.IS",
        "DBSKY.IS","DERIM.IS","DESPC.IS","DITAS.IS","DMRGD.IS",
        "DNISI.IS","DOBUR.IS","DURDO.IS",
        "DZGYO.IS","EKIZ.IS","EKSUN.IS","ELITE.IS","EMNIS.IS",
        "ERCBK.IS","ERSU.IS","ETYAT.IS","EUHOL.IS","EYGYO.IS",
        "FADE.IS","FMIZP.IS","FRIGO.IS","GARFA.IS","GAZAP.IS",
        "GLBMD.IS","GLRYH.IS","GMTAS.IS","GOKNR.IS","GRSEL.IS",
        "GRTRK.IS","GSDHO.IS","GSRAY.IS","GZNMI.IS","HKTM.IS",
        "HLGYO.IS","HUBVC.IS","IBDSN.IS","IDEAS.IS","IDGYO.IS",
        "IEYHO.IS","IHEVA.IS","IHGZT.IS","IHLAS.IS","IHLGM.IS",
        "IHYAY.IS","IMASM.IS","ISKPL.IS","ISKUR.IS","ITTFH.IS",
        "IZFAS.IS","KARSN.IS","KARYE.IS","KGYO.IS","KIMMR.IS",
        "KLGYO.IS","KLNMA.IS","KOCMT.IS","KRPLS.IS","KRSTL.IS",
        "KRTEK.IS","KSTUR.IS","KUYAS.IS","KZBGY.IS","LIDER.IS",
        "MAGEN.IS","MAALT.IS","MARKA.IS","MRSHL.IS","MSGYO.IS",
        "MZHLD.IS","NUHCM.IS","OSMEN.IS","OZGYO.IS","OZRDN.IS",
        "OZSUB.IS","PAGYO.IS","PARSN.IS","PEGYO.IS","PEKGY.IS",
        "PEMBE.IS","PETUN.IS","PKENT.IS","PNLSN.IS","PNSUT.IS",
        "PRZMA.IS","RTALB.IS","RYSGB.IS",
        "SAYAS.IS","SEGYO.IS","SEKFK.IS","SEKUR.IS","SELGD.IS",
        "SEYKM.IS","SKTAS.IS","SNPAM.IS",
        "TDGYO.IS","TETMT.IS","TKNSA.IS","TKURU.IS",
        "TRCAS.IS","TURGG.IS","UFUK.IS","ULUSE.IS",
        "UNYEC.IS","UTPYA.IS","VAKKO.IS","VANGD.IS","VERTU.IS",
        "VKFYO.IS","YAYLA.IS","YESIL.IS","YGYO.IS","YYAPI.IS",
        "ZEDUR.IS","ZRGYO.IS"
    ).stream().distinct().collect(Collectors.toUnmodifiableList());
}
