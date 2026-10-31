package com.zjgsu.zcl.admin.controller;

import com.zjgsu.zcl.admin.model.AuditRecord;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/audits")
public class AdminController {

    @PersistenceContext
    private EntityManager entityManager;

    // 模拟管理员审核失物帖子
    @PostMapping
    @Transactional
    public ResponseEntity<String> auditItem(@RequestBody AuditRecord record) {
        // 在实际生产中，这里可能需要通过 RestTemplate 调用 lost-service 去确认帖子是否存在
        // 为了演示，我们直接保存审核记录
        if (!"APPROVED".equals(record.getStatus()) && !"REJECTED".equals(record.getStatus())) {
            return ResponseEntity.badRequest().body("状态只能为 APPROVED 或 REJECTED");
        }

        entityManager.persist(record);
        return ResponseEntity.ok("审核完成！记录ID：" + record.getId());
    }
}