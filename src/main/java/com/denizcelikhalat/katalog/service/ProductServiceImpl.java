package com.denizcelikhalat.katalog.service;

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
        return productRepository.findById(id).orElse(null);
    }

    @Override
    @Transactional
    public void save(Product product, MultipartFile imageFile, MultipartFile tableImageFile) throws IOException {
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

    @Override
    public Page<Product> findAll(Pageable pageable) {
        return productRepository.findAll(pageable);
    }

    @Override
    public Page<Product> search(String keyword, Pageable pageable) {
        if (!StringUtils.hasText(keyword)) return productRepository.findAll(pageable);
        return productRepository.findByNameContainingIgnoreCase(keyword, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, Pageable pageable) {
        return productRepository.findByCategoryId(categoryId, pageable);
    }

    @Override
    public Page<Product> findByCategoryId(Long categoryId, int page, int size) {
        return findByCategoryId(categoryId, PageRequest.of(page, size));
    }
}
