package com.zjgsu.zcl.admin.controller;

import com.zjgsu.zcl.admin.model.AuditRecord;
import com.zjgsu.zcl.admin.service.AuditService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 审核服务的对外接口。Controller 只负责 HTTP：
 * 接参数 → 调 Service → 把异常翻译成状态码。业务规则全部在 {@link AuditService}。
 */
@RestController
@RequestMapping("/api/admin/audits")
public class AdminController {

    private final AuditService auditService;

    public AdminController(AuditService auditService) {
        this.auditService = auditService;
    }

    /** 审核失物：成功 200；参数不合法 400；重复审核 409 */
    @PostMapping
    public ResponseEntity<?> auditItem(@RequestBody AuditRecord record) {
        try {
            AuditRecord saved = auditService.audit(record);
            return ResponseEntity.ok(saved);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (IllegalStateException e) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
        }
    }

    /** 查询全部审核记录 */
    @GetMapping
    public ResponseEntity<List<AuditRecord>> list() {
        return ResponseEntity.ok(auditService.findAll());
    }

    /** 按失物 ID 查审核结论：没有记录返回 404 */
    @GetMapping("/{lostItemId}")
    public ResponseEntity<AuditRecord> getByLostItemId(@PathVariable Long lostItemId) {
        return auditService.findByLostItemId(lostItemId)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }
}
