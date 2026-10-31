package com.zjgsu.zcl.lost.model;

import jakarta.persistence.*;

@Entity
@Table(name = "lost_items")
public class LostItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String itemName;

    @Column(nullable = false)
    private String feature;

    @Column(nullable = false)
    private Long userId; // 注意：这里只存 ID，不再用 @ManyToOne

    // Getters and Setters ...
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
}