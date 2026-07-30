package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CartItem;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.ShippingCalculationResult;
import com.denizcelikhalat.katalog.model.ShippingCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * AŞAMA 2: Ağırlık hesaplama + AŞAMA 3B: lojistik kategori (ShippingCategory) birim testleri.
 * Saf POJO'larla çalışır (DB/Spring context gerekmez) — ShippingService'teki standardMaxWeight/
 * heavyMaxWeight alanları @Value varsayılanlarıyla AYNI Java alan başlatıcılarına sahip
 * olduğundan (50 / 300), Spring olmadan "new ShippingService()" ile de doğru çalışır.
 */
class ShippingServiceTest {

    private final ShippingService shippingService = new ShippingService();

    // ---- Yardımcılar ----

    private Product buildProduct(BigDecimal shippingWeightPerMeter) {
        Product product = new Product();
        product.setShippingWeightPerMeter(shippingWeightPerMeter);
        return product;
    }

    private CartItem buildItem(Product product, BigDecimal measurementAmount) {
        CartItem item = new CartItem();
        item.setProduct(product);
        item.setMeasurementAmount(measurementAmount);
        return item;
    }

    private Cart buildCart(CartItem... items) {
        Cart cart = new Cart();
        cart.setItems(List.of(items));
        return cart;
    }

    // ---- Testler ----

    /**
     * Test 1: 6mm halat, 0.1400 kg/m, 100 metre -> 14 kg -> STANDARD.
     */
    @Test
    void tekUrunAgirligiDogruHesaplanmali() {
        Product halat6mm = buildProduct(new BigDecimal("0.1400"));
        CartItem item = buildItem(halat6mm, new BigDecimal("100"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("14.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * Test 2: 6mm (100m x 0.14 = 14kg) + 10mm (50m x 0.40 = 20kg) = 34 kg.
     */
    @Test
    void birdenFazlaUrununAgirligiToplanmali() {
        Product halat6mm = buildProduct(new BigDecimal("0.1400"));
        Product halat10mm = buildProduct(new BigDecimal("0.4000"));

        CartItem item1 = buildItem(halat6mm, new BigDecimal("100"));
        CartItem item2 = buildItem(halat10mm, new BigDecimal("50"));
        Cart cart = buildCart(item1, item2);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("34.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }

    /**
     * Yeni Test: 200 metre x 0.5 kg/m = 100 kg -> HEAVY_CARGO
     * (standard-max-weight=50 ile heavy-max-weight=300 arasında).
     */
    @Test
    void agirUrunHeavyCargoOlarakSiniflandirilmali() {
        Product product = buildProduct(new BigDecimal("0.5000"));
        CartItem item = buildItem(product, new BigDecimal("200"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("100.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.HEAVY_CARGO, result.getCategory());
    }

    /**
     * Yeni Test: 1000 metre x 0.5 kg/m = 500 kg -> MANUAL_REVIEW
     * (heavy-max-weight=300'ü aştığı için). Bu durumda requiresManualReview YİNE DE false
     * kalır (veri eksik değil, ağırlık güvenilir şekilde hesaplandı) — ama category
     * MANUAL_REVIEW'dır (eşik aşıldığı için otomatik fiyatlandırma yapılmamalı).
     */
    @Test
    void esikUstuAgirlikManualReviewOlarakSiniflandirilmali() {
        Product product = buildProduct(new BigDecimal("0.5000"));
        CartItem item = buildItem(product, new BigDecimal("1000"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, new BigDecimal("500.00").compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Test 3: shippingWeightPerMeter null olan bir ürün -> weightCalculated=false,
     * requiresManualReview=true. Sistem PATLAMAMALI (exception fırlatılmamalı).
     */
    @Test
    void shippingWeightPerMeterNullIseManuelIncelemeIsaretlenmeli() {
        Product weightBilgisiOlmayanUrun = buildProduct(null);
        CartItem item = buildItem(weightBilgisiOlmayanUrun, new BigDecimal("25"));
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertFalse(result.isWeightCalculated());
        assertTrue(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Ek test: measurementAmount null olan bir kalem (örn. NONE/PRESET modunda adet bazlı
     * satılan ürün) -> sistem PATLAMAMALI; bu kalem de manuel inceleme gerektirir sayılır
     * (quantity/adet, metre yerine kullanılmaz) -> category MANUAL_REVIEW.
     */
    @Test
    void olcuMiktariNullIseManuelIncelemeIsaretlenmeliVePatlamamali() {
        Product product = buildProduct(new BigDecimal("0.1400"));
        CartItem item = buildItem(product, null); // örn. NONE modunda measurementAmount hiç set edilmez
        Cart cart = buildCart(item);

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertFalse(result.isWeightCalculated());
        assertTrue(result.isRequiresManualReview());
        assertEquals(ShippingCategory.MANUAL_REVIEW, result.getCategory());
    }

    /**
     * Ek test: sepet null -> exception fırlatılmamalı, boş/başarılı sonuç dönmeli
     * (hesaplanamayan kalem yok, dolayısıyla manuel inceleme de gerekmez).
     */
    @Test
    void sepetNullIsePatlamamaliVeSifirAgirlikDonmeli() {
        ShippingCalculationResult result = shippingService.calculateCartWeight(null);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory()); // 0 kg <= standard-max-weight
    }

    /**
     * Ek test: sepet boş (kalem yok) -> 0 kg, weightCalculated=true, requiresManualReview=false,
     * category=STANDARD.
     */
    @Test
    void bosSepetSifirAgirlikDonmeli() {
        Cart cart = buildCart(); // hiç kalem yok

        ShippingCalculationResult result = shippingService.calculateCartWeight(cart);

        assertNotNull(result);
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(result.getTotalWeight()));
        assertTrue(result.isWeightCalculated());
        assertFalse(result.isRequiresManualReview());
        assertEquals(ShippingCategory.STANDARD, result.getCategory());
    }
}
