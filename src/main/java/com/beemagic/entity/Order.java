package com.beemagic.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Double totalAmount;

    @Column(nullable = false)
    private String status = "PENDING";

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column
    private LocalDateTime packedAt;

    @Column
    private LocalDateTime shippedAt;

    @Column
    private LocalDateTime deliveredAt;

    @Column
    private LocalDateTime canceledAt;

    @Column(nullable = false)
    private Boolean canceledByCustomer = false;

    @Column
    private String shippingAddress;

    @Column
    private String paymentMethod;

    @Column
    private String paymentId;

    @Column
    private String razorpayOrderId;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL)
    private List<OrderItem> items;
}
