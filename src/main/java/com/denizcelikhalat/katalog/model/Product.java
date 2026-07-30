package com.denizcelikhalat.katalog.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Entity
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    private Double price;

    private String description;

    private String imagePath;

    private String tableImagePath;  // Yeni alan eklendi

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    // ===== Ölçü / Fiyatlandırma =====

    @Enumerated(EnumType.STRING)
    @Column(name = "measurement_mode", nullable = false, length = 20)
    private MeasurementMode measurementMode = MeasurementMode.NONE;

    // CUSTOM modunda gösterilecek birim etiketi: metre, cm, kg ...
    @Column(name = "measurement_unit_label", length = 50)
    private String measurementUnitLabel;

    // PRESET modunda her satır "etiket | fiyat" formatında. Örn:
    // 1 metre | 100
    // 5 metre | 450
    @Column(name = "measurement_options_text", columnDefinition = "TEXT")
    private String measurementOptionsText;

    // ===== Stok & Yayın durumu =====

    // Metre/cm/kg gibi satılan ürünler olabildiği için BigDecimal.
    // NONE -> adet, CUSTOM -> toplam ölçü miktarı (örn. toplam metre), PRESET -> satılabilir adet.
    @Column(name = "stock_quantity", nullable = false, precision = 10, scale = 2)
    private BigDecimal stockQuantity = BigDecimal.ZERO;

    // Yayında mı? false ise herkese açık sayfalarda görünmez/satılmaz (admin görebilir/düzenleyebilir).
    @Column(name = "active", nullable = false)
    private Boolean active = true;

    // Satın alınabilir mi? (basit stok: sayısal takip yok). false -> "Stokta yok".
    @Column(name = "in_stock", nullable = false)
    private Boolean inStock = true;

    // ===== Para birimi =====
    // Ürün, resmi fiyat listesindeki kendi para biriminde satılır (dönüşüm yok). Varsayılan USD.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private PriceCurrency currency = PriceCurrency.USD;

    // ===== Kargo (AŞAMA 1: yalnızca veri modeli — kargo HESAPLAMASI burada yapılmaz) =====
    // Bir metre ürünün kilogram karşılığı (örn. 6mm çelik halat: 0.14, 10mm: 0.40).
    // Eski ürünler için NULL olabilir. Negatif değer kabul edilmez (bkz. ProductServiceImpl).
    @Column(name = "shipping_weight_per_meter", precision = 10, scale = 4)
    private BigDecimal shippingWeightPerMeter;

    public Product() {
    }

    public Product(Long id, String name, Double price, String description, String imagePath, String tableImagePath, Category category) {
        this.id = id;
        this.name = name;
        this.price = price;
        this.description = description;
        this.imagePath = imagePath;
        this.tableImagePath = tableImagePath;  // Constructor güncellendi
        this.category = category;
    }

    // ===== PRESET seçeneklerini ayrıştıran yardımcı (DB'ye yazılmaz) =====
    @Transient
    public List<PresetOption> getMeasurementOptions() {
        List<PresetOption> options = new ArrayList<>();
        if (measurementOptionsText == null || measurementOptionsText.isBlank()) {
            return options;
        }
        for (String raw : measurementOptionsText.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty()) continue;
            int sep = line.indexOf('|');
            if (sep < 0) continue;
            String label = line.substring(0, sep).trim();
            String priceStr = line.substring(sep + 1).trim().replace(",", ".");
            if (label.isEmpty() || priceStr.isEmpty()) continue;
            try {
                options.add(new PresetOption(label, new BigDecimal(priceStr)));
            } catch (NumberFormatException ignored) {
                // Hatalı satırı yok say
            }
        }
        return options;
    }

    /** Şablon ve servis için basit (entity olmayan) seçenek taşıyıcı. */
    public static class PresetOption {
        private final String label;
        private final BigDecimal price;

        public PresetOption(String label, BigDecimal price) {
            this.label = label;
            this.price = price;
        }

        public String getLabel() {
            return label;
        }

        public BigDecimal getPrice() {
            return price;
        }
    }

    // Getter & Setter
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getImagePath() {
        return imagePath;
    }

    public void setImagePath(String imagePath) {
        this.imagePath = imagePath;
    }

    public String getTableImagePath() {
        return tableImagePath;
    }

    public void setTableImagePath(String tableImagePath) {
        this.tableImagePath = tableImagePath;
    }

    public Category getCategory() {
        return category;
    }

    public void setCategory(Category category) {
        this.category = category;
    }

    public MeasurementMode getMeasurementMode() {
        return measurementMode;
    }

    public void setMeasurementMode(MeasurementMode measurementMode) {
        this.measurementMode = measurementMode;
    }

    public String getMeasurementUnitLabel() {
        return measurementUnitLabel;
    }

    public void setMeasurementUnitLabel(String measurementUnitLabel) {
        this.measurementUnitLabel = measurementUnitLabel;
    }

    public String getMeasurementOptionsText() {
        return measurementOptionsText;
    }

    public void setMeasurementOptionsText(String measurementOptionsText) {
        this.measurementOptionsText = measurementOptionsText;
    }

    public BigDecimal getStockQuantity() {
        return stockQuantity;
    }

    public void setStockQuantity(BigDecimal stockQuantity) {
        this.stockQuantity = stockQuantity;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public Boolean getInStock() {
        return inStock;
    }

    public void setInStock(Boolean inStock) {
        this.inStock = inStock;
    }

    public PriceCurrency getCurrency() {
        return currency;
    }

    public void setCurrency(PriceCurrency currency) {
        this.currency = currency;
    }

    public BigDecimal getShippingWeightPerMeter() {
        return shippingWeightPerMeter;
    }

    public void setShippingWeightPerMeter(BigDecimal shippingWeightPerMeter) {
        this.shippingWeightPerMeter = shippingWeightPerMeter;
    }
}
