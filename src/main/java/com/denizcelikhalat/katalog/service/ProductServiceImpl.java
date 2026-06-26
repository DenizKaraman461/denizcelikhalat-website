package com.denizcelikhalat.katalog.service;

import com.denizcelikhalat.katalog.model.MeasurementMode;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.repository.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class ProductServiceImpl implements ProductService {

    private final ProductRepository productRepository;

    // application.properties -> upload.path=uploads
    @Value("${upload.path:uploads}")
    private String uploadBase;

    @Autowired
    public ProductServiceImpl(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ---------------- helpers ----------------
    private Path resolveUploadDir() {
        Path base = Paths.get(uploadBase);
        return base.isAbsolute() ? base : Paths.get(System.getProperty("user.dir")).resolve(base);
    }

    private String saveFile(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) throw new IllegalArgumentException("Dosya boş.");

        String ct = file.getContentType();
        if (ct == null || !ct.startsWith("image/")) {
            throw new IllegalArgumentException("Sadece görsel yükleyebilirsiniz.");
        }

        String original = file.getOriginalFilename();
        String cleaned = StringUtils.cleanPath(original != null ? original : "");
        int dot = cleaned.lastIndexOf('.');
        if (dot < 0 || dot == cleaned.length() - 1) throw new IllegalArgumentException("Geçersiz dosya adı/uzantı.");

        String ext = cleaned.substring(dot).toLowerCase(Locale.ROOT);
        if (!(ext.equals(".png") || ext.equals(".jpg") || ext.equals(".jpeg") || ext.equals(".webp"))) {
            throw new IllegalArgumentException("İzin verilen uzantılar: .png .jpg .jpeg .webp");
        }

        String unique = UUID.randomUUID() + ext;
        Path uploadDir = resolveUploadDir();
        if (!Files.exists(uploadDir)) Files.createDirectories(uploadDir);
        file.transferTo(uploadDir.resolve(unique).toFile());
        return unique;
    }

    // ---------------- service api ----------------
    @Override
    public List<Product> listAll(String keyword) {
        if (StringUtils.hasText(keyword)) {
            return productRepository.findByNameContainingIgnoreCase(keyword, Pageable.unpaged()).getContent();
        }
        return productRepository.findAll();
    }

    @Override
    public Product getById(Long id) {
        // Admin düzenleme/görüntüleme için pasif ürünler dahil tümünü getirir.
        return productRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void save(Product product, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException {
        // Güvenli varsayılanlar (basit boolean stok)
        if (product.getActive() == null) product.setActive(Boolean.TRUE);
        if (product.getInStock() == null) product.setInStock(Boolean.TRUE);
        if (product.getCurrency() == null) product.setCurrency(com.denizcelikhalat.katalog.model.PriceCurrency.USD);

        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = saveFile(imageFile);
            product.setImagePath("/uploads/" + imagePath);
        }
        if (tableImageFile != null && !tableImageFile.isEmpty()) {
            String tablePath = saveFile(tableImageFile);
            product.setTableImagePath("/uploads/" + tablePath);
        }
        productRepository.save(product);
    }

    @Override
    @Transactional
    public void update(Long id, Product updatedProduct, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException {
        Product existing = productRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Product not found: " + id));

        if (StringUtils.hasText(updatedProduct.getName())) {
            existing.setName(updatedProduct.getName());
        }
        if (updatedProduct.getPrice() != null) {
            existing.setPrice(updatedProduct.getPrice());
        }
        // null da set edilsin istiyorsan aşağıyı koru:
        existing.setDescription(updatedProduct.getDescription());

        if (updatedProduct.getCategory() != null) {
            existing.setCategory(updatedProduct.getCategory());
        }

        // Ölçü / fiyatlandırma alanlarını da güncelle (DEĞİŞMEDİ)
        existing.setMeasurementMode(updatedProduct.getMeasurementMode() != null
                ? updatedProduct.getMeasurementMode() : MeasurementMode.NONE);
        existing.setMeasurementUnitLabel(updatedProduct.getMeasurementUnitLabel());
        existing.setMeasurementOptionsText(updatedProduct.getMeasurementOptionsText());

        // Yayın & basit stok (sayısal stok artık kullanılmıyor; stockQuantity'ye dokunulmaz)
        existing.setActive(updatedProduct.getActive() != null
                ? updatedProduct.getActive() : Boolean.TRUE);
        existing.setInStock(updatedProduct.getInStock() != null
                ? updatedProduct.getInStock() : Boolean.TRUE);

        // Para birimi (dönüşüm yok). Gelmezse mevcut korunur, o da yoksa USD.
        if (updatedProduct.getCurrency() != null) {
            existing.setCurrency(updatedProduct.getCurrency());
        } else if (existing.getCurrency() == null) {
            existing.setCurrency(com.denizcelikhalat.katalog.model.PriceCurrency.USD);
        }

        if (imageFile != null && !imageFile.isEmpty()) {
            String imagePath = saveFile(imageFile);
            existing.setImagePath("/uploads/" + imagePath);
        }
        if (tableImageFile != null && !tableImageFile.isEmpty()) {
            String tablePath = saveFile(tableImageFile);
            existing.setTableImagePath("/uploads/" + tablePath);
        }

        productRepository.save(existing);
    }

    @Override
    @Transactional
    public void deleteById(Long id) {
        productRepository.deleteById(id);
    }

    // ===== Herkese açık listelemeler: yalnızca YAYINDA olan ürünler =====
    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findByActiveTrue(pageable);
    }

    @Override
    public Page<Product> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) return productRepository.findByActiveTrue(pageable);
        return productRepository.findByNameContainingIgnoreCaseAndActiveTrue(keyword, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryIdAndActiveTrue(categoryId, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, int page, int size) {
        return findByCategoryId(categoryId, PageRequest.of(page, size));
    }
}
