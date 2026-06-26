package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.QuoteRequest;
import com.denizcelikhalat.katalog.repository.QuoteRequestRepository;
import com.denizcelikhalat.katalog.service.ProductService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;

/**
 * Teklif talepleri. /quote/create herkese açık (SecurityConfig), /admin/quotes yalnızca ADMIN
 * (/admin/** kuralı). Sepet/sipariş akışına dokunmaz.
 */
@Controller
public class QuoteController {

    private final QuoteRequestRepository quoteRequestRepository;
    private final ProductService productService;

    public QuoteController(QuoteRequestRepository quoteRequestRepository,
                           ProductService productService) {
        this.quoteRequestRepository = quoteRequestRepository;
        this.productService = productService;
    }

    /**
     * Ürün detay sayfasındaki "Teklif Al" formu buraya POST eder. Kaydeder ve aynı ürün
     * detay sayfasına "Teklif talebiniz alındı" mesajıyla döner.
     */
    @PostMapping("/quote/create")
    public String createQuote(@ModelAttribute QuoteRequest quoteRequest,
                              RedirectAttributes redirectAttributes) {

        Long productId = quoteRequest.getProductId();

        if (productId == null) {
            redirectAttributes.addFlashAttribute("error", "Ürün bilgisi bulunamadı.");
            return "redirect:/products";
        }

        Product product = productService.getById(productId);

        if (product == null) {
            redirectAttributes.addFlashAttribute("error", "Ürün bulunamadı.");
            return "redirect:/products";
        }

        boolean isCustom = product.getMeasurementMode() != null
                && "CUSTOM".equals(product.getMeasurementMode().name());

        if (!isCustom) {
            redirectAttributes.addFlashAttribute("error", "Bu ürün için teklif talebi oluşturulamaz.");
            return "redirect:/products/" + productId;
        }

        if (quoteRequest.getCustomerName() == null || quoteRequest.getCustomerName().isBlank()
                || quoteRequest.getPhone() == null || quoteRequest.getPhone().isBlank()) {
            redirectAttributes.addFlashAttribute("error", "Ad soyad ve telefon alanları zorunludur.");
            return "redirect:/products/" + productId;
        }

        quoteRequest.setProductName(product.getName());

        if (quoteRequest.getCertificateRequested() == null) {
            quoteRequest.setCertificateRequested(Boolean.FALSE);
        }

        quoteRequest.setCreatedAt(LocalDateTime.now());

        quoteRequestRepository.save(quoteRequest);

        redirectAttributes.addFlashAttribute(
                "success",
                "Teklif talebiniz alındı. En kısa sürede sizinle iletişime geçeceğiz."
        );

        return "redirect:/products/" + productId;
    }

    /**
     * Admin: gelen teklif talepleri listesi.
     */
    @GetMapping("/admin/quotes")
    public String adminQuotes(Model model) {
        model.addAttribute("quotes", quoteRequestRepository.findAllByOrderByCreatedAtDesc());
        return "admin_quotes";
    }
}
