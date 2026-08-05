package com.beemagic.controller;

import com.beemagic.entity.Order;
import com.beemagic.entity.OrderItem;
import com.beemagic.entity.User;
import com.beemagic.repository.OrderRepository;
import com.beemagic.repository.UserRepository;
import com.beemagic.service.EmailService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private EmailService emailService;

    @PostMapping("/{userId}")
    public ResponseEntity<?> placeOrder(@PathVariable("userId") Long userId, @RequestBody OrderRequest request) {
        Optional<User> userOpt = userRepository.findById(userId);
        User user;
        if (userOpt.isPresent()) {
            user = userOpt.get();
        } else {
            List<User> users = userRepository.findAll();
            Optional<User> existingUser = users.stream().filter(u -> !"ADMIN".equals(u.getRole())).findFirst();
            if (existingUser.isPresent()) {
                user = existingUser.get();
            } else {
                user = new User();
                user.setName("Customer");
                user.setEmail("customer_" + System.currentTimeMillis() + "@beemagic.com");
                user.setPassword("guest123");
                user.setRole("USER");
                user = userRepository.save(user);
            }
        }

        Order order = new Order();
        order.setUser(user);
        order.setTotalAmount(request.getTotalAmount());
        order.setShippingAddress(request.getShippingAddress());
        order.setPaymentMethod(request.getPaymentMethod());
        order.setPaymentId(request.getPaymentId());
        order.setRazorpayOrderId(request.getRazorpayOrderId());

        List<OrderItem> items = request.getItems().stream().map(itemReq -> {
            OrderItem item = new OrderItem();
            item.setOrder(order);
            item.setProductId(itemReq.getProductId());
            item.setName(itemReq.getName());
            item.setQuantity(itemReq.getQuantity());
            item.setPrice(itemReq.getPrice());
            item.setImage(itemReq.getImage());
            return item;
        }).toList();

        order.setItems(items);
        Order savedOrder = orderRepository.save(order);

        // Send confirmation email in a background thread to keep response times fast
        try {
            new Thread(() -> {
                try {
                    emailService.sendOrderConfirmationEmail(savedOrder);
                } catch (Exception ex) {
                    // Fail silently in the thread
                }
            }).start();
        } catch (Exception ex) {
            // Fail silently
        }

        return ResponseEntity.ok(savedOrder);
    }

    @GetMapping("/user/{userId}")
    public List<Order> getUserOrders(@PathVariable("userId") Long userId) {
        return orderRepository.findByUserId(userId);
    }

    @GetMapping("/all")
    public ResponseEntity<?> getAllOrders(@RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body("Access denied.");
        }
        return ResponseEntity.ok(orderRepository.findAll());
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable("id") Long id) {
        return orderRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateOrderStatus(
            @PathVariable("id") Long id, 
            @RequestBody java.util.Map<String, String> body, 
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        if (!"ADMIN".equals(role)) {
            return ResponseEntity.status(403).body("Access denied. Admin role required.");
        }

        String newStatus = body.get("status");
        if (newStatus == null || newStatus.trim().isEmpty()) {
            return ResponseEntity.badRequest().body("Status is required");
        }

        newStatus = newStatus.toUpperCase();

        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        Order order = orderOpt.get();
        order.setStatus(newStatus);

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        if ("PENDING".equals(newStatus)) {
            order.setPackedAt(null);
            order.setShippedAt(null);
            order.setDeliveredAt(null);
            order.setCanceledAt(null);
            order.setCanceledByCustomer(false);
        } else if ("PACKED".equals(newStatus)) {
            if (order.getPackedAt() == null) {
                order.setPackedAt(now);
            }
            order.setShippedAt(null);
            order.setDeliveredAt(null);
            order.setCanceledAt(null);
            order.setCanceledByCustomer(false);
        } else if ("SHIPPED".equals(newStatus)) {
            if (order.getPackedAt() == null) {
                order.setPackedAt(now);
            }
            if (order.getShippedAt() == null) {
                order.setShippedAt(now);
            }
            order.setDeliveredAt(null);
            order.setCanceledAt(null);
            order.setCanceledByCustomer(false);
        } else if ("DELIVERED".equals(newStatus)) {
            if (order.getPackedAt() == null) {
                order.setPackedAt(now);
            }
            if (order.getShippedAt() == null) {
                order.setShippedAt(now);
            }
            if (order.getDeliveredAt() == null) {
                order.setDeliveredAt(now);
            }
            order.setCanceledAt(null);
            order.setCanceledByCustomer(false);
        } else if ("CANCELED".equals(newStatus)) {
            if (order.getCanceledAt() == null) {
                order.setCanceledAt(now);
            }
            order.setCanceledByCustomer(false);
            order.setPackedAt(null);
            order.setShippedAt(null);
            order.setDeliveredAt(null);
        }

        return ResponseEntity.ok(orderRepository.save(order));
    }

    @PutMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            @PathVariable("id") Long id,
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @RequestHeader(value = "X-User-Role", required = false) String role) {
        
        Optional<Order> orderOpt = orderRepository.findById(id);
        if (orderOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Order order = orderOpt.get();
        
        boolean isOwner = userId != null && order.getUser().getId().equals(userId);
        boolean isAdmin = "ADMIN".equals(role);
        
        if (!isAdmin && !isOwner) {
            return ResponseEntity.status(403).body("Access denied. You can only cancel your own orders.");
        }
        
        if (!isAdmin && !("PENDING".equals(order.getStatus()) || "PACKED".equals(order.getStatus()))) {
            return ResponseEntity.badRequest().body("Orders that have been shipped or delivered cannot be canceled.");
        }
        
        order.setStatus("CANCELED");
        order.setCanceledAt(java.time.LocalDateTime.now());
        if (!isAdmin) {
            order.setCanceledByCustomer(true);
        }
        order.setPackedAt(null);
        order.setShippedAt(null);
        order.setDeliveredAt(null);
        
        return ResponseEntity.ok(orderRepository.save(order));
    }
}

class OrderRequest {
    private Double totalAmount;
    private String shippingAddress;
    private List<OrderItemRequest> items;
    private String paymentMethod;
    private String paymentId;
    private String razorpayOrderId;

    public Double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(Double totalAmount) { this.totalAmount = totalAmount; }
    public String getShippingAddress() { return shippingAddress; }
    public void setShippingAddress(String shippingAddress) { this.shippingAddress = shippingAddress; }
    public List<OrderItemRequest> getItems() { return items; }
    public void setItems(List<OrderItemRequest> items) { this.items = items; }
    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public String getRazorpayOrderId() { return razorpayOrderId; }
    public void setRazorpayOrderId(String razorpayOrderId) { this.razorpayOrderId = razorpayOrderId; }
}

class OrderItemRequest {
    private String productId;
    private String name;
    private Integer quantity;
    private Double price;
    private String image;

    public String getProductId() { return productId; }
    public void setProductId(String productId) { this.productId = productId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Integer getQuantity() { return quantity; }
    public void setQuantity(Integer quantity) { this.quantity = quantity; }
    public Double getPrice() { return price; }
    public void setPrice(Double price) { this.price = price; }
    public String getImage() { return image; }
    public void setImage(String image) { this.image = image; }
}
