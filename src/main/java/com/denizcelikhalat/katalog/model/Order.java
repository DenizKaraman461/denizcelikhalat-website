package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Entity
@Table(name = "orders") // "order" MySQL'de rezerve kelime olduğu için tablo adı "orders"
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Bir kullanıcının birden çok siparişi olabilir.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    private BigDecimal totalAmount;

    private LocalDateTime orderDate;

    // Enum'u ordinal yerine isim olarak sakla (ileri uyumluluk için).
    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private OrderStatus status;

    private String deliveryAddress;

    // Müşteri değerlendirmesi (1-5). Henüz puanlanmadıysa null.
    private Integer rating;

    // ===== Checkout snapshot alanları (sipariş anındaki müşteri/onay bilgileri) =====
    @Column(name = "customer_name", length = 120)
    private String customerName;

    @Column(name = "customer_email", length = 180)
    private String customerEmail;

    @Column(name = "customer_phone", length = 40)
    private String customerPhone;

    @Column(name = "customer_note", columnDefinition = "TEXT")
    private String customerNote;

    @Column(name = "pre_info_accepted", nullable = false)
    private Boolean preInfoAccepted = false;

    @Column(name = "distance_sales_accepted", nullable = false)
    private Boolean distanceSalesAccepted = false;

    // Sipariş para birimi (sepetteki tek para biriminden alınır; dönüşüm yok). Varsayılan USD.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PriceCurrency currency = PriceCurrency.USD;

    // Sipariş silinince kalemleri de silinir.
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<OrderItem> items = new ArrayList<>();

    public Order() {
    }

    public Order(Long id, User user, BigDecimal totalAmount, LocalDateTime orderDate,
                 OrderStatus status, String deliveryAddress, List<OrderItem> items) {
        this.id = id;
        this.user = user;
        this.totalAmount = totalAmount;
        this.orderDate = orderDate;
        this.status = status;
        this.deliveryAddress = deliveryAddress;
        this.items = items;
    }

    public Order(User user, BigDecimal totalAmount, LocalDateTime orderDate,
                 OrderStatus status, String deliveryAddress) {
        this.user = user;
        this.totalAmount = totalAmount;
        this.orderDate = orderDate;
        this.status = status;
        this.deliveryAddress = deliveryAddress;
    }

    // İlişkinin iki tarafını da senkron tutmak için yardımcı metotlar
    public void addItem(OrderItem item) {
        items.add(item);
        item.setOrder(this);
    }

    public void removeItem(OrderItem item) {
        items.remove(item);
        item.setOrder(null);
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public LocalDateTime getOrderDate() {
        return orderDate;
    }

    public void setOrderDate(LocalDateTime orderDate) {
        this.orderDate = orderDate;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getDeliveryAddress() {
        return deliveryAddress;
    }

    public void setDeliveryAddress(String deliveryAddress) {
        this.deliveryAddress = deliveryAddress;
    }

    public Integer getRating() {
        return rating;
    }

    public void setRating(Integer rating) {
        this.rating = rating;
    }

    public String getCustomerName() {
        return customerName;
    }

    public void setCustomerName(String customerName) {
        this.customerName = customerName;
    }

    public String getCustomerEmail() {
        return customerEmail;
    }

    public void setCustomerEmail(String customerEmail) {
        this.customerEmail = customerEmail;
    }

    public String getCustomerPhone() {
        return customerPhone;
    }

    public void setCustomerPhone(String customerPhone) {
        this.customerPhone = customerPhone;
    }

    public String getCustomerNote() {
        return customerNote;
    }

    public void setCustomerNote(String customerNote) {
        this.customerNote = customerNote;
    }

    public Boolean getPreInfoAccepted() {
        return preInfoAccepted;
    }

    public void setPreInfoAccepted(Boolean preInfoAccepted) {
        this.preInfoAccepted = preInfoAccepted;
    }

    public Boolean getDistanceSalesAccepted() {
        return distanceSalesAccepted;
    }

    public void setDistanceSalesAccepted(Boolean distanceSalesAccepted) {
        this.distanceSalesAccepted = distanceSalesAccepted;
    }

    public PriceCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(PriceCurrency currency) {
        this.currency = currency;
    }

    public List<OrderItem> getItems() {
        return items;
    }

    public void setItems(List<OrderItem> items) {
        this.items = items;
    }

    /**
     * Sipariş toplamlarını para birimine göre gruplar (dönüşüm YOK, farklı para birimleri TOPLANMAZ).
     * Karışık para birimli siparişlerde ana toplam olarak BU metot kullanılmalı; totalAmount yalnızca
     * legacy/uyumluluk içindir. Sıra: EUR, USD, TRY.
     * Her kalem: currency = item.currencySnapshot ?: item.product.currency ?: order.currency ?: USD,
     *            lineTotal = (item.price ?: 0) * (quantity ?: 0).
     */
    @Transient
    public Map<PriceCurrency, BigDecimal> getTotalsByCurrency() {
        Map<PriceCurrency, BigDecimal> acc = new LinkedHashMap<>();
        if (items != null) {
            for (OrderItem item : items) {
                if (item == null) continue;

                PriceCurrency itemCurrency = item.getCurrencySnapshot();
                if (itemCurrency == null && item.getProduct() != null) {
                    itemCurrency = item.getProduct().getCurrency();
                }
                if (itemCurrency == null) itemCurrency = this.currency;
                if (itemCurrency == null) itemCurrency = PriceCurrency.USD;

                BigDecimal unitPrice = (item.getPrice() != null) ? item.getPrice() : BigDecimal.ZERO;
                int quantity = (item.getQuantity() != null) ? item.getQuantity() : 0;
                BigDecimal lineTotal = unitPrice.multiply(BigDecimal.valueOf(quantity));

                acc.merge(itemCurrency, lineTotal, BigDecimal::add);
            }
        }
        return Cart.orderByPreferred(acc);
    }
}
