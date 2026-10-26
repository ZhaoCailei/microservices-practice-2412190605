package com.zjgsu.zcl.model;

import jakarta.persistence.*;

@Entity
@Table(name = "lost_items")
public class LostItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String itemName; // 物品名称

    @Column(nullable = false)
    private String feature; // 物品特征

    @Column(nullable = false)
    private String status; // 状态（待审核/已认领）

    @ManyToOne
    @JoinColumn(name = "user_id", nullable = false)
    private User user; // 关联发布用户

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public User getUser() { return user; }
    public void setUser(User user) { this.user = user; }
}