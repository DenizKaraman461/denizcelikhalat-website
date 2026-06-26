package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CheckoutForm;
import com.denizcelikhalat.katalog.model.Order;
import com.denizcelikhalat.katalog.model.OrderStatus;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.service.CartService;
import com.denizcelikhalat.katalog.service.OrderService;
import com.denizcelikhalat.katalog.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.util.ArrayList;
import java.util.List;

@Controller
public class OrderController {

    private final OrderService orderService;
    private final CartService cartService;
    private final UserRepository userRepository;

    public OrderController(OrderService orderService,
                           CartService cartService,
                           UserRepository userRepository) {
        this.orderService = orderService;
        this.cartService = cartService;
        this.userRepository = userRepository;
    }

    // ===================== CHECKOUT AKIŞI =====================

    /**
     * GET /checkout -> Sipariş onay formu + sepet özeti.
     * Sepet boşsa /cart'a hata ile döner. Form, kullanıcı bilgileriyle ön doldurulur.
     */
    @GetMapping("/checkout")
    public String checkoutForm(Principal principal, Model model, RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }
        Cart cart = cartService.getCartByEmail(principal.getName());
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Sepetiniz boş. Önce ürün ekleyin.");
            return "redirect:/cart";
        }

        if (!model.containsAttribute("checkoutForm")) {
            model.addAttribute("checkoutForm", prefillForm(principal.getName()));
        }
        model.addAttribute("cart", cart);
        return "checkout";
    }

    /**
     * POST /checkout -> Formu doğrular, geçerliyse siparişi oluşturur (PRG).
     * Geçersizse hata mesajlarıyla checkout.html'i girilen değerleri koruyarak tekrar gösterir.
     */
    @PostMapping("/checkout")
    public String placeOrder(@ModelAttribute("checkoutForm") CheckoutForm form,
                             Principal principal,
                             Model model,
                             RedirectAttributes redirectAttributes) {
        if (principal == null) {
            return "redirect:/login";
        }

        Cart cart = cartService.getCartByEmail(principal.getName());
        if (cart == null || cart.getItems() == null || cart.getItems().isEmpty()) {
            redirectAttributes.addFlashAttribute("error", "Sepetiniz boş. Sipariş oluşturulamaz.");
            return "redirect:/cart";
        }

        // ---- Form doğrulama ----
        List<String> errors = new ArrayList<>();
        if (!StringUtils.hasText(form.getCustomerName())) errors.add("Ad Soyad zorunludur.");
        if (!StringUtils.hasText(form.getCustomerPhone())) errors.add("Telefon zorunludur.");
        if (!StringUtils.hasText(form.getDeliveryAddress())) errors.add("Teslimat adresi zorunludur.");
        if (!form.isPreInfoAccepted()) errors.add("Ön bilgilendirme formunu kabul etmelisiniz.");
        if (!form.isDistanceSalesAccepted()) errors.add("Mesafeli satış sözleşmesini kabul etmelisiniz.");

        if (!errors.isEmpty()) {
            model.addAttribute("errors", errors);
            model.addAttribute("checkoutForm", form); // girilen değerler korunur
            model.addAttribute("cart", cart);
            return "checkout";
        }

        // ---- Sipariş oluştur ----
        try {
            Order order = orderService.placeOrder(principal.getName(), form);
            // PRG: yenilemede tekrar sipariş oluşmasın diye redirect.
            return "redirect:/checkout/success/" + order.getId();
        } catch (IllegalStateException e) {
            // Ürün pasif/stokta yok gibi durumlar -> formu hata ile tekrar göster
            model.addAttribute("errors", List.of(e.getMessage()));
            model.addAttribute("checkoutForm", form);
            model.addAttribute("cart", cart);
            return "checkout";
        }
    }

    /**
     * GET /checkout/success/{orderId} -> Sipariş onay sayfası.
     * Kullanıcı yalnızca kendi siparişini görebilir; admin hepsini görebilir.
     */
    @GetMapping("/checkout/success/{orderId}")
    public String success(@PathVariable Long orderId, Principal principal,
                          Authentication authentication, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/orders";
        }
        boolean admin = isAdmin(authentication);
        String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
        if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
            // Başkasının siparişi -> kendi siparişlerine yönlendir
            return "redirect:/orders";
        }

        model.addAttribute("order", order);
        model.addAttribute("orderId", order.getId());
        model.addAttribute("total", order.getTotalAmount());
        return "checkout_success";
    }

    // ----- yardımcılar -----
    private CheckoutForm prefillForm(String email) {
        CheckoutForm form = new CheckoutForm();
        User user = userRepository.findByEmail(email).orElse(null);
        if (user != null) {
            String fullName = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                    + (user.getLastName() != null ? user.getLastName() : "")).trim();
            form.setCustomerName(fullName);
            form.setCustomerPhone(user.getPhone());
            form.setDeliveryAddress(user.getAddress());
        }
        return form;
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }

    // ===================== PUANLAMA (AJAX) =====================

    @PostMapping("/orders/{id}/rate")
    @ResponseBody
    public String rateOrder(@PathVariable Long id, @RequestParam Integer rating) {
        orderService.rateOrder(id, rating);
        return "OK";
    }

    // ===================== MÜŞTERİ: Siparişlerim =====================

    @GetMapping("/orders")
    public String myOrders(Principal principal, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        model.addAttribute("orders", orderService.getOrdersByEmail(principal.getName()));
        return "my_orders";
    }

    /**
     * GET /orders/{orderId} -> Müşteri sipariş detayı.
     * Kullanıcı yalnızca kendi siparişini görebilir; admin hepsini görebilir.
     */
    @GetMapping("/orders/{orderId}")
    public String orderDetail(@PathVariable Long orderId, Principal principal,
                              Authentication authentication, Model model) {
        if (principal == null) {
            return "redirect:/login";
        }
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/orders";
        }
        boolean admin = isAdmin(authentication);
        String ownerEmail = (order.getUser() != null) ? order.getUser().getEmail() : null;
        if (!admin && (ownerEmail == null || !ownerEmail.equals(principal.getName()))) {
            return "redirect:/orders";
        }
        model.addAttribute("order", order);
        return "order_detail";
    }

    // ===================== ADMIN: Sipariş Yönetimi =====================

    @GetMapping("/admin/orders")
    public String adminOrders(Model model) {
        model.addAttribute("orders", orderService.getAllOrders());
        model.addAttribute("statuses", OrderStatus.values());
        return "admin_orders";
    }

    /**
     * GET /admin/orders/{orderId} -> Admin sipariş detayı (tüm snapshot + durum güncelleme formu).
     * /admin/** zaten ADMIN rolü ister (SecurityConfig).
     */
    @GetMapping("/admin/orders/{orderId}")
    public String adminOrderDetail(@PathVariable Long orderId, Model model) {
        Order order = orderService.getOrderDetail(orderId);
        if (order == null) {
            return "redirect:/admin/orders";
        }
        model.addAttribute("order", order);
        model.addAttribute("statuses", OrderStatus.values());
        return "admin_order_detail";
    }

    @PostMapping("/admin/orders/{id}/status")
    public String updateOrderStatus(@PathVariable Long id,
                                    @RequestParam("status") OrderStatus status,
                                    RedirectAttributes redirectAttributes) {
        orderService.updateOrderStatus(id, status);
        redirectAttributes.addFlashAttribute("success", "Sipariş durumu güncellendi.");
        return "redirect:/admin/orders/" + id;
    }
}
