package com.beemagic.controller;

import com.beemagic.entity.Product;
import com.beemagic.entity.Review;
import com.beemagic.repository.ProductRepository;
import com.beemagic.repository.ReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    @Autowired
    private ReviewRepository reviewRepository;

    @Autowired
    private ProductRepository productRepository;

    @PostMapping
    public ResponseEntity<?> addReview(@RequestBody Review review) {
        if (review.getProductId() == null) {
            return ResponseEntity.badRequest().body("Product ID is required");
        }
        if (review.getRating() == null || review.getRating() < 1 || review.getRating() > 5) {
            return ResponseEntity.badRequest().body("Rating must be between 1 and 5");
        }
        if (review.getUserName() == null || review.getUserName().trim().isEmpty()) {
            return ResponseEntity.badRequest().body("User name is required");
        }

        // Save review
        Review savedReview = reviewRepository.save(review);

        // Recalculate average rating and total review count
        Optional<Product> prodOpt = productRepository.findById(review.getProductId());
        if (prodOpt.isPresent()) {
            Product product = prodOpt.get();
            List<Review> productReviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(review.getProductId());
            int totalReviews = productReviews.size();
            double sumRatings = productReviews.stream().mapToDouble(Review::getRating).sum();
            int avgRating = (int) Math.round(sumRatings / totalReviews);

            product.setRating(avgRating);
            product.setReviews(totalReviews);
            productRepository.save(product);
        }

        return ResponseEntity.ok(savedReview);
    }

    @GetMapping
    public List<Review> getAllReviews() {
        return reviewRepository.findAllByOrderByCreatedAtDesc();
    }

    @GetMapping("/product/{productId}")
    public List<Review> getProductReviews(@PathVariable Long productId) {
        return reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteReview(@PathVariable Long id, @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body("Access denied. Admin role required.");
        }

        Optional<Review> reviewOpt = reviewRepository.findById(id);
        if (!reviewOpt.isPresent()) {
            return ResponseEntity.notFound().build();
        }

        Review review = reviewOpt.get();
        Long productId = review.getProductId();

        // Delete the review
        reviewRepository.deleteById(id);

        // Recalculate average rating and total review count
        if (productId != null) {
            Optional<Product> prodOpt = productRepository.findById(productId);
            if (prodOpt.isPresent()) {
                Product product = prodOpt.get();
                List<Review> productReviews = reviewRepository.findByProductIdOrderByCreatedAtDesc(productId);
                int totalReviews = productReviews.size();
                if (totalReviews > 0) {
                    double sumRatings = productReviews.stream().mapToDouble(Review::getRating).sum();
                    int avgRating = (int) Math.round(sumRatings / totalReviews);
                    product.setRating(avgRating);
                    product.setReviews(totalReviews);
                } else {
                    product.setRating(5); // Default back to 5
                    product.setReviews(0);
                }
                productRepository.save(product);
            }
        }

        return ResponseEntity.ok().build();
    }
}
