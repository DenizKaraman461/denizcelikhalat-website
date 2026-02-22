package com.denizcelikhalat.katalog.controller;

import com.denizcelikhalat.katalog.model.Category;
import com.denizcelikhalat.katalog.model.Product;
import com.denizcelikhalat.katalog.service.CategoryService;
import com.denizcelikhalat.katalog.service.ProductService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Controller
public class ProductController {

    private final ProductService productService;
    private final CategoryService categoryService;

    @Autowired
    public ProductController(ProductService productService, CategoryService categoryService) {
        this.productService = productService;
        this.categoryService = categoryService;
    }

    @GetMapping("/products")
    public String listProducts(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size,
            @RequestParam(value = "keyword", required = false) String keyword,
            Model model,
            Authentication authentication
    ) {
        if (page < 0) page = 0;
        if (size < 1) size = 12;

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = StringUtils.hasText(keyword)
                ? productService.search(keyword, pageable)
                : productService.findAll(pageable);

        model.addAttribute("productPage", result);            // << products.html uses productPage.content
        model.addAttribute("productList", result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", null);
        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("pageNumbers", getPageNumbers(page, result.getTotalPages()));

        return "products";
    }

    @GetMapping("/category/{id}")
    public String productsByCategory(
            @PathVariable Long id,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "12") int size,
            Model model,
            Authentication authentication
    ) {
        if (page < 0) page = 0;
        if (size < 1) size = 12;

        Pageable pageable = PageRequest.of(page, size);
        Page<Product> result = productService.findByCategoryId(id, pageable);

        model.addAttribute("productPage", result);            // << products.html uses productPage.content
        model.addAttribute("productList", result.getContent());
        model.addAttribute("currentPage", page);
        model.addAttribute("totalPages", result.getTotalPages());
        model.addAttribute("keyword", null);

        Category category = categoryService.getById(id).orElse(null);
        model.addAttribute("category", category);

        model.addAttribute("isAdmin", isAdmin(authentication));
        model.addAttribute("pageNumbers", getPageNumbers(page, result.getTotalPages()));

        return "products";
    }

    private List<Integer> getPageNumbers(int currentPage, int totalPages) {
        List<Integer> pageNumbers = new ArrayList<>();
        if (totalPages > 0) {
            int start = Math.max(0, currentPage - 1);
            int end = Math.min(totalPages - 1, currentPage + 1);

            if (currentPage == 0) {
                end = Math.min(totalPages - 1, 2);
            } else if (currentPage == totalPages - 1) {
                start = Math.max(0, totalPages - 3);
            }

            for (int i = start; i <= end; i++) {
                pageNumbers.add(i);
            }
        }
        return pageNumbers;
    }

    @GetMapping("/add")
    public String showAddForm(Model model) {
        model.addAttribute("product", new Product());
        model.addAttribute("categories", categoryService.findAll());
        return "add_product";
    }

    @PostMapping("/add")
    public String addProduct(
            @ModelAttribute Product product,
            @RequestParam("imageFile") MultipartFile imageFile,
            @RequestParam("tableImageFile") MultipartFile tableImageFile
    ) throws IOException {
        productService.save(product, imageFile, tableImageFile);
        return "redirect:/products";
    }

    @GetMapping("/edit/{id}")
    public String showEditForm(@PathVariable Long id, Model model) {
        Product product = productService.getById(id);
        model.addAttribute("product", product);
        model.addAttribute("categories", categoryService.findAll());
        return "edit_product";
    }

    @PostMapping("/edit/{id}")
    public String editProduct(
            @PathVariable Long id,
            @ModelAttribute Product product,
            @RequestParam(value = "imageFile", required = false) MultipartFile imageFile,
            @RequestParam(value = "tableImageFile", required = false) MultipartFile tableImageFile
    ) throws IOException {
        productService.update(id, product, imageFile, tableImageFile);
        return "redirect:/products";
    }

    @GetMapping("/delete/{id}")
    public String deleteProduct(@PathVariable Long id) {
        productService.deleteById(id);
        return "redirect:/products";
    }

    @GetMapping("/products/{id}")
    public String productDetail(@PathVariable Long id, Model model, Authentication authentication) {
        Product product = productService.getById(id);
        if (product == null) {
            return "redirect:/products";
        }
        model.addAttribute("product", product);
        model.addAttribute("isAdmin", isAdmin(authentication));
        return "product_detail";
    }

    private boolean isAdmin(Authentication authentication) {
        return authentication != null &&
                authentication.getAuthorities().stream()
                        .anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
    }
}
