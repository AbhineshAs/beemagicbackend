package com.beemagic.controller;

import com.beemagic.entity.Product;
import com.beemagic.repository.ProductRepository;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @GetMapping
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Product> getProductById(@PathVariable Long id) {
        return productRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<?> addProduct(@RequestBody Product product, @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body("Access denied. Admin role required.");
        }
        return ResponseEntity.ok(productRepository.save(product));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateProduct(@PathVariable Long id, @RequestBody Product productDetails, @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body("Access denied. Admin role required.");
        }
        return productRepository.findById(id)
                .map(product -> {
                    product.setTitle(productDetails.getTitle());
                    product.setDescription(productDetails.getDescription());
                    product.setPrice(productDetails.getPrice());
                    product.setOldPrice(productDetails.getOldPrice());
                    product.setImage(productDetails.getImage());
                    product.setCollection(productDetails.getCollection());
                    product.setBadge(productDetails.getBadge());
                    product.setRating(productDetails.getRating());
                    product.setReviews(productDetails.getReviews());
                    product.setStock(productDetails.getStock());
                    return ResponseEntity.ok(productRepository.save(product));
                })
                .orElse(ResponseEntity.notFound().build());
    }


    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteProduct(@PathVariable Long id, @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body("Access denied. Admin role required.");
        }
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @PostConstruct
    public void seedData() {
        try {
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 0");
            jdbcTemplate.execute("TRUNCATE TABLE products");
            jdbcTemplate.execute("SET FOREIGN_KEY_CHECKS = 1");
        } catch (Exception e) {
            System.err.println("Failed to truncate products table: " + e.getMessage());
            productRepository.deleteAll();
        }

        Product p1 = new Product();
        p1.setTitle("Amla Infused Honey");
        p1.setPrice(499.00);
        p1.setOldPrice(649.00);
        p1.setCollection("INFUSED HONEY COLLECTION");
        p1.setImage("/images/amla_infused_honey.jpg");
        p1.setDescription("A powerful blend of raw organic honey and vitamin C-rich Amla (Indian Gooseberry). Strengthens immunity, aids digestion, and brings a delightful sweet-and-sour tang to your table.");
        p1.setBadge("IMMUNITY BOOSTER");

        Product p2 = new Product();
        p2.setTitle("Mango Infused Honey");
        p2.setPrice(499.00);
        p2.setOldPrice(649.00);
        p2.setCollection("INFUSED HONEY COLLECTION");
        p2.setImage("/images/mango_infused_honey.jpg");
        p2.setDescription("Indulge in the tropical richness of organic honey infused with sun-ripened, luscious mangoes. Perfect for sweetening desserts, spreading on warm toast, or drizzling over yogurt.");
        p2.setBadge("TROPICAL FAVORITE");

        Product p3 = new Product();
        p3.setTitle("Strawberry Infused Honey");
        p3.setPrice(499.00);
        p3.setOldPrice(649.00);
        p3.setCollection("INFUSED HONEY COLLECTION");
        p3.setImage("/images/strawberry_infused_honey.jpg");
        p3.setDescription("A delightful fusion of premium honey and juicy, ripe strawberries. It delivers a vibrant berry aroma and a sweet, fruity flavour that kids and adults alike will love.");
        p3.setBadge("NEW LAUNCH");

        Product p4 = new Product();
        p4.setTitle("Organic Honey");
        p4.setPrice(399.00);
        p4.setOldPrice(499.00);
        p4.setCollection("ARTISANAL PURE HONEY");
        p4.setImage("/images/organic_honey.jpg");
        p4.setDescription("100% pure, raw, and unpasteurized organic honey harvested from pristine wild forest apiaries. Naturally rich in enzymes, pollen, and antioxidants, it is nature's finest sweetener.");
        p4.setBadge("100% PURE");

        Product p5 = new Product();
        p5.setTitle("Turmeric Infused Honey");
        p5.setPrice(499.00);
        p5.setOldPrice(649.00);
        p5.setCollection("INFUSED HONEY COLLECTION");
        p5.setImage("/images/turmeric_infused_honey.jpg");
        p5.setDescription("The golden duo: raw honey combined with premium high-curcumin turmeric. Known for its potent anti-inflammatory and antioxidant benefits, with a warm, earthy flavor.");
        p5.setBadge("HEALING SPICE");

        Product p6 = new Product();
        p6.setTitle("Thulasi Honey");
        p6.setPrice(499.00);
        p6.setOldPrice(649.00);
        p6.setCollection("ARTISANAL PURE HONEY");
        p6.setImage("/images/thulasi_honey.jpg");
        p6.setDescription("Pure honey infused with the sacred herbs of Thulasi (Holy Basil). Relieves respiratory congestion, calms mind, and provides a soothing, herbal sweetness.");
        p6.setBadge("SACRED HERBAL");

        Product p7 = new Product();
        p7.setTitle("Chilli Infused Honey");
        p7.setPrice(799.00);
        p7.setOldPrice(999.00);
        p7.setCollection("INFUSED HONEY COLLECTION");
        p7.setImage("/images/chilli_infused_honey.jpg");
        p7.setDescription("A bold, fiery kick of dried red chillies balanced perfectly with the smooth sweetness of pure honey. Excellent for glazing meats, dressing pizzas, or adding a sweet-heat zip to snacks.");
        p7.setBadge("BEST SELLER");

        productRepository.saveAll(List.of(p1, p2, p3, p4, p5, p6, p7));
    }
}
