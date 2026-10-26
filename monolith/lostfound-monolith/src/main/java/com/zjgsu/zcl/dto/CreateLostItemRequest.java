package com.zjgsu.zcl.dto;

public class CreateLostItemRequest {
    private Long userId;
    private String itemName;
    private String feature;

    // Getters and Setters
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getItemName() { return itemName; }
    public void setItemName(String itemName) { this.itemName = itemName; }
    public String getFeature() { return feature; }
    public void setFeature(String feature) { this.feature = feature; }
}