package com.beemagic.controller;

import com.beemagic.entity.CartItem;
import com.beemagic.entity.User;
import com.beemagic.repository.CartItemRepository;
import com.beemagic.repository.UserRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/cart")
public class CartController {

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @GetMapping("/{userId}")
    public ResponseEntity<List<CartItem>> getCart(@PathVariable Long userId) {
        return ResponseEntity.ok(cartItemRepository.findByUserId(userId));
    }

    @PostMapping("/{userId}")
    public ResponseEntity<?> addToCart(@PathVariable Long userId, @RequestBody CartItemRequest request) {
        Optional<User> userOpt = userRepository.findById(userId);
        if (userOpt.isEmpty()) {
            return ResponseEntity.badRequest().body("User not found");
        }

        Optional<CartItem> existingItemOpt = cartItemRepository.findByUserIdAndProductId(userId, request.getProductId());

        if (existingItemOpt.isPresent()) {
            CartItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(existingItem.getQuantity() + request.getQuantity());
            cartItemRepository.save(existingItem);
            return ResponseEntity.ok(existingItem);
        } else {
            CartItem newItem = new CartItem();
            newItem.setUser(userOpt.get());
            newItem.setProductId(request.getProductId());
            newItem.setName(request.getName());
            newItem.setPrice(request.getPrice());
            newItem.setImage(request.getImage());
            newItem.setQuantity(request.getQuantity());
            cartItemRepository.save(newItem);
            return ResponseEntity.ok(newItem);
        }
    }

    @PutMapping("/{userId}/{itemId}")
    public ResponseEntity<?> updateQuantity(@PathVariable Long userId, @PathVariable Long itemId, @RequestBody QuantityRequest request) {
        Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
        if (itemOpt.isPresent() && itemOpt.get().getUser().getId().equals(userId)) {
            CartItem item = itemOpt.get();
            item.setQuantity(request.getQuantity());
            cartItemRepository.save(item);
            return ResponseEntity.ok(item);
        }
        return ResponseEntity.notFound().build();
    }

    @DeleteMapping("/{userId}/{itemId}")
    public ResponseEntity<?> removeFromCart(@PathVariable Long userId, @PathVariable Long itemId) {
        Optional<CartItem> itemOpt = cartItemRepository.findById(itemId);
        if (itemOpt.isPresent() && itemOpt.get().getUser().getId().equals(userId)) {
            cartItemRepository.delete(itemOpt.get());
            return ResponseEntity.ok().build();
        }
        return ResponseEntity.notFound().build();
    }

    @Transactional
    @DeleteMapping("/{userId}/clear")
    public ResponseEntity<?> clearCart(@PathVariable Long userId) {
        cartItemRepository.deleteByUserId(userId);
        return ResponseEntity.ok().build();
    }
}

class CartItemRequest {
    private String productId;
    private String name;
    private Double price;
    private String image;
    private Integer quantity;

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}

class QuantityRequest {
    private Integer quantity;
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
}
