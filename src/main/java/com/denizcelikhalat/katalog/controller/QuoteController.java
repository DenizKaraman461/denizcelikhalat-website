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
import org.springframework.web.bind.annotation.RequestParam;
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
        // Ürün adı snapshot'ı (varsa) ve dönüş hedefi için ürünü bul.
        Long productId = quoteRequest.getProductId();
        Product product = (productId != null) ? productService.getById(productId) : null;
        if (product != null) {
            quoteRequest.setProductName(product.getName());
        }

        if (quoteRequest.getCertificateRequested() == null) {
            quoteRequest.setCertificateRequested(Boolean.FALSE);
        }
        if (quoteRequest.getStatus() == null || quoteRequest.getStatus().isBlank()) {
            quoteRequest.setStatus("NEW");
        }
        quoteRequest.setCreatedAt(LocalDateTime.now());

        quoteRequestRepository.save(quoteRequest);

        redirectAttributes.addFlashAttribute("success", "Teklif talebiniz alındı. En kısa sürede sizinle iletişime geçeceğiz.");

        // Aynı ürün detay sayfasına geri dön; productId yoksa ürün listesine.
        return (productId != null) ? ("redirect:/products/" + productId) : "redirect:/products";
    }

    /**
     * Admin: gelen teklif talepleri listesi.
     */
    @GetMapping("/admin/quotes")
    public String adminQuotes(Model model) {
        model.addAttribute("quotes", quoteRequestRepository.findAllByOrderByCreatedAtDesc());
        return "admin_quotes";
    }

    /**
     * Admin: bir teklif talebinin durumunu günceller. /admin/** altında olduğu için yalnızca ADMIN.
     * CSRF açık (form token gönderir). Sonra tekrar /admin/quotes'a döner.
     */
    @PostMapping("/admin/quotes/status")
    public String updateQuoteStatus(@RequestParam("quoteId") Long quoteId,
                                    @RequestParam("status") String status,
                                    RedirectAttributes redirectAttributes) {
        QuoteRequest quote = quoteRequestRepository.findById(quoteId).orElse(null);
        if (quote == null) {
            redirectAttributes.addFlashAttribute("error", "Teklif talebi bulunamadı.");
            return "redirect:/admin/quotes";
        }
        quote.setStatus((status != null && !status.isBlank()) ? status : "NEW");
        quoteRequestRepository.save(quote);
        redirectAttributes.addFlashAttribute("success", "Teklif durumu güncellendi.");
        return "redirect:/admin/quotes";
    }
}
