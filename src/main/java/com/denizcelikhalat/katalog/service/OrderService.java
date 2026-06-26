package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.Cart;
import com.denizcelikhalat.katalog.model.CartItem;
import com.denizcelikhalat.katalog.model.CheckoutForm;
import com.denizcelikhalat.katalog.model.Order;
import com.denizcelikhalat.katalog.model.OrderItem;
import com.denizcelikhalat.katalog.model.OrderStatus;
import com.denizcelikhalat.katalog.model.PriceCurrency;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.model.User;
import com.denizcelikhalat.katalog.repository.CartRepository;
import com.denizcelikhalat.katalog.repository.OrderRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class OrderService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm");

    private final OrderRepository orderRepository;
    private final CartRepository cartRepository;
    private final EmailService emailService;

    @Value("${app.order.notify-email:info@denizcelikhalat.com}")
    private String shopNotifyEmail;

    public OrderService(OrderRepository orderRepository,
                        CartRepository cartRepository,
                        EmailService emailService) {
        this.orderRepository = orderRepository;
        this.cartRepository = cartRepository;
        this.emailService = emailService;
    }

    /**
     * Checkout formundaki bilgilerle siparişi oluşturur. Sepeti alır, Order + OrderItem'ları
     * kaydeder, sepeti boşaltır ve bilgilendirme e-postalarını gönderir.
     *
     * Basit stok: sayısal stok takibi YOK. Yalnızca ürün active/inStock değilse sipariş reddedilir.
     * Ödeme entegrasyonu olmadığından durum PENDING olarak ayarlanır.
     */
    @Transactional
    public Order placeOrder(String email, CheckoutForm form) {
        Cart cart = cartRepository.findByUserEmailWithItems(email)
                .orElseThrow(() -> new IllegalStateException("Sepet bulunamadı."));

        List<CartItem> items = cart.getItems();
        if (items == null || items.isEmpty()) {
            throw new IllegalStateException("Sepetiniz boş, sipariş oluşturulamaz.");
        }

        User user = cart.getUser();

        Order order = new Order();
        order.setUser(user);
        order.setOrderDate(LocalDateTime.now());
        order.setStatus(OrderStatus.PENDING); // ödeme yok -> PENDING

        // ===== Checkout snapshot'ları =====
        order.setCustomerName(form.getCustomerName());
        order.setCustomerPhone(form.getCustomerPhone());
        order.setDeliveryAddress(form.getDeliveryAddress());
        order.setCustomerNote(form.getCustomerNote());
        order.setCustomerEmail(user != null ? user.getEmail() : email);
        order.setPreInfoAccepted(form.isPreInfoAccepted());
        order.setDistanceSalesAccepted(form.isDistanceSalesAccepted());

        StringBuilder itemsText = new StringBuilder();

        // ===== Karışık para birimine izin verilir: toplamlar para birimine göre gruplanır (dönüşüm YOK) =====
        Map<PriceCurrency, BigDecimal> totalsByCurrency = new LinkedHashMap<>();

        for (CartItem item : items) {
            Product product = item.getProduct();
            int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;

            // Yayın & basit stok doğrulaması (sayısal stok yok)
            if (product != null) {
                if (Boolean.FALSE.equals(product.getActive())) {
                    throw new IllegalStateException("'" + product.getName()
                            + "' ürünü artık satışta değil. Lütfen sepetten çıkarın.");
                }
                if (Boolean.FALSE.equals(product.getInStock())) {
                    throw new IllegalStateException("'" + product.getName()
                            + "' ürünü stokta yok. Lütfen sepetten çıkarın.");
                }
            }

            // Kalemin para birimi (tek para birimi zorunluluğu YOK)
            PriceCurrency itemCurrency = resolveItemCurrency(item);

            BigDecimal unitPrice = (item.getUnitPriceSnapshot() != null)
                    ? item.getUnitPriceSnapshot()
                    : ((product != null && product.getPrice() != null)
                        ? BigDecimal.valueOf(product.getPrice()) : BigDecimal.ZERO);

            BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

            OrderItem orderItem = new OrderItem(product, quantity, unitPrice,
                    item.getSelectedMeasurement(), item.getMeasurementAmount());
            orderItem.setCurrencySnapshot(itemCurrency);
            order.addItem(orderItem);

            totalsByCurrency.merge(itemCurrency, lineTotal, BigDecimal::add);

            String productName = (product != null && product.getName() != null) ? product.getName() : "Ürün";
            itemsText.append("  - ").append(productName).append("  x ").append(quantity);
            if (item.getSelectedMeasurement() != null && !item.getSelectedMeasurement().isBlank()) {
                itemsText.append("  | Ölçü: ").append(item.getSelectedMeasurement());
            }
            itemsText.append("   =   ").append(formatMoney(lineTotal, itemCurrency)).append("\n");
        }

        // ===== Legacy uyumluluk: tek para birimliyse o toplam/para birimi; karışıksa ZERO/USD fallback =====
        if (totalsByCurrency.size() == 1) {
            Map.Entry<PriceCurrency, BigDecimal> only = totalsByCurrency.entrySet().iterator().next();
            order.setCurrency(only.getKey());
            order.setTotalAmount(only.getValue());
        } else {
            // Karışık (veya boş) -> totalAmount gösterimde KULLANILMAZ; şablon/e-posta getTotalsByCurrency() kullanır.
            order.setCurrency(PriceCurrency.USD);
            order.setTotalAmount(BigDecimal.ZERO);
        }

        // cascade = ALL -> order_items da kaydedilir.
        Order saved = orderRepository.save(order);

        // Sepeti boşalt (orphanRemoval -> cart_items silinir).
        cart.getItems().clear();
        cartRepository.save(cart);

        // ===== Bilgilendirme e-postaları (arka planda) =====
        sendOrderEmails(saved, user, itemsText.toString(), Cart.orderByPreferred(totalsByCurrency));

        return saved;
    }

    @Transactional
    public void rateOrder(Long orderId, Integer rating) {
        if (rating == null || rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Puan 1 ile 5 arasında olmalıdır.");
        }
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));
        order.setRating(rating);
        orderRepository.save(order);
    }

    /**
     * Başarı/detay sayfası için tek siparişi kalemleri + kullanıcısıyla birlikte getirir.
     */
    @Transactional(readOnly = true)
    public Order getOrderDetail(Long orderId) {
        return orderRepository.findDetailById(orderId).orElse(null);
    }

    private void sendOrderEmails(Order order, User user, String itemsText,
                                 Map<PriceCurrency, BigDecimal> totalsByCurrency) {
        String code = "#DCH-" + order.getId();
        String date = (order.getOrderDate() != null) ? order.getOrderDate().format(DATE_FMT) : "-";
        String address = (order.getDeliveryAddress() != null && !order.getDeliveryAddress().isBlank())
                ? order.getDeliveryAddress() : "-";

        // Önce checkout snapshot'ı, yoksa kullanıcı bilgisi
        String fullName = (order.getCustomerName() != null && !order.getCustomerName().isBlank())
                ? order.getCustomerName()
                : (user != null
                    ? ((user.getFirstName() != null ? user.getFirstName() : "") + " " +
                       (user.getLastName() != null ? user.getLastName() : "")).trim()
                    : "");
        String phone = (order.getCustomerPhone() != null && !order.getCustomerPhone().isBlank())
                ? order.getCustomerPhone()
                : ((user != null && user.getPhone() != null) ? user.getPhone() : "-");
        String customerEmail = (order.getCustomerEmail() != null && !order.getCustomerEmail().isBlank())
                ? order.getCustomerEmail()
                : (user != null ? user.getEmail() : null);
        String note = (order.getCustomerNote() != null && !order.getCustomerNote().isBlank())
                ? order.getCustomerNote() : null;

        // Para birimine göre gruplu toplamlar (dönüşüm yok)
        StringBuilder totalsBlock = new StringBuilder("Para Birimine Göre Toplamlar:\n");
        if (totalsByCurrency == null || totalsByCurrency.isEmpty()) {
            totalsBlock.append("  - ").append(formatMoney(BigDecimal.ZERO, PriceCurrency.USD)).append("\n");
        } else {
            for (Map.Entry<PriceCurrency, BigDecimal> e : totalsByCurrency.entrySet()) {
                totalsBlock.append("  - ").append(e.getKey().getLabel()).append(": ")
                        .append(formatMoney(e.getValue(), e.getKey())).append("\n");
            }
        }
        String totalsText = totalsBlock.toString();

        // --- Müşteriye onay maili ---
        if (customerEmail != null && !customerEmail.isBlank()) {
            String subject = "Siparişiniz Alındı - " + code + " | Deniz Çelik Halat";
            String body = "Sayın " + (fullName.isBlank() ? "Müşterimiz" : fullName) + ",\n\n"
                    + "Siparişiniz başarıyla alındı. Teşekkür ederiz!\n\n"
                    + "Sipariş Kodu : " + code + "\n"
                    + "Tarih        : " + date + "\n\n"
                    + "Sipariş İçeriği:\n"
                    + itemsText + "\n"
                    + totalsText + "\n"
                    + "Teslimat Adresi:\n" + address + "\n"
                    + (note != null ? ("\nSipariş Notu:\n" + note + "\n") : "")
                    + "\nSiparişiniz hazırlanmaya başlandığında tarafınıza bilgi verilecektir.\n\n"
                    + "Deniz Çelik Halat\n"
                    + "Bornova, İzmir";
            emailService.sendPlainText(customerEmail, subject, body);
        }

        // --- Dükkana yeni sipariş bildirimi ---
        String adminSubject = "Yeni Sipariş - " + code;
        String adminBody = "Yeni bir sipariş alındı.\n\n"
                + "Sipariş Kodu : " + code + "\n"
                + "Tarih        : " + date + "\n\n"
                + "Müşteri:\n"
                + "  Ad Soyad : " + (fullName.isBlank() ? "-" : fullName) + "\n"
                + "  E-posta  : " + (customerEmail != null ? customerEmail : "-") + "\n"
                + "  Telefon  : " + phone + "\n\n"
                + "Teslimat Adresi:\n" + address + "\n"
                + (note != null ? ("\nSipariş Notu:\n" + note + "\n") : "")
                + "\nSipariş İçeriği:\n"
                + itemsText + "\n"
                + totalsText;
        emailService.sendPlainText(shopNotifyEmail, adminSubject, adminBody);
    }

    private static String formatMoney(BigDecimal amount, PriceCurrency currency) {
        if (amount == null) amount = BigDecimal.ZERO;
        String symbol = (currency != null) ? currency.getSymbol() : "$";
        return String.format(Locale.forLanguageTag("tr-TR"), "%,.2f", amount) + " " + symbol;
    }

    // Bir kalemin para birimini snapshot'tan, yoksa üründen, o da yoksa USD olarak çözer.
    private static PriceCurrency resolveItemCurrency(CartItem item) {
        if (item.getCurrencySnapshot() != null) return item.getCurrencySnapshot();
        if (item.getProduct() != null && item.getProduct().getCurrency() != null) {
            return item.getProduct().getCurrency();
        }
        return PriceCurrency.USD;
    }

    @Transactional(readOnly = true)
    public List<Order> getAllOrders() {
        return orderRepository.findAllWithUserOrderByOrderDateDesc();
    }

    @Transactional(readOnly = true)
    public List<Order> getOrdersByEmail(String email) {
        return orderRepository.findByUser_EmailOrderByOrderDateDesc(email);
    }

    @Transactional
    public void updateOrderStatus(Long orderId, OrderStatus status) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new IllegalArgumentException("Sipariş bulunamadı: " + orderId));
        order.setStatus(status);
        orderRepository.save(order);
    }
}
