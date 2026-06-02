package com.beemagic.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "products")
@Data
@NoArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 1000)
    private String description;

    @Column(nullable = false)
    private Double price;

    @Column
    private Double oldPrice;

    @Column
    private String image;

    @Column
    private String collection;

    @Column
    private String badge;

    @Column
    private Integer rating = 5;

    @Column
    private Integer reviews = 0;

    @Column
    private Integer stock = 100;
}
