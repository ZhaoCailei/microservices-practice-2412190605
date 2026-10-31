package com.zjgsu.zcl.admin.model;

import jakarta.persistence.*;

@Entity
@Table(name = "audit_records")
public class AuditRecord {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long lostItemId; // 关联的失物ID，仅存ID，不建外键

    @Column(nullable = false)
    private String status; // APPROVED, REJECTED

    // Getters and Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getLostItemId() { return lostItemId; }
    public void setLostItemId(Long lostItemId) { this.lostItemId = lostItemId; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}