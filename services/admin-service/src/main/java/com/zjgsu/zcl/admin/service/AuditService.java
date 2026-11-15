package com.zjgsu.zcl.admin.service;

import com.zjgsu.zcl.admin.model.AuditRecord;
import com.zjgsu.zcl.admin.repository.AuditRecordRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 审核服务的业务层。
 *
 * <p>业务规则集中在这里（原先前散落在 Controller 里）：
 * <ol>
 *   <li>lostItemId 必填；</li>
 *   <li>结论只允许 APPROVED / REJECTED；</li>
 *   <li>同一条失物不允许重复审核（审核结论是终态）。</li>
 * </ol>
 */
@Service
public class AuditService {

    /** 允许的审核结论 */
    private static final Set<String> ALLOWED_STATUS = Set.of("APPROVED", "REJECTED");

    private final AuditRecordRepository auditRecordRepository;

    public AuditService(AuditRecordRepository auditRecordRepository) {
        this.auditRecordRepository = auditRecordRepository;
    }

    /**
     * 审核一条失物：校验通过后落库。
     *
     * @throws IllegalArgumentException 参数不合法（缺 lostItemId、状态不在允许集合）
     * @throws IllegalStateException    该失物已审核过（重复审核）
     */
    @Transactional
    public AuditRecord audit(AuditRecord record) {
        if (record == null) {
            throw new IllegalArgumentException("审核请求不能为空");
        }
        if (record.getLostItemId() == null || record.getLostItemId() <= 0) {
            throw new IllegalArgumentException("lostItemId 不能为空");
        }

        String status = normalize(record.getStatus());
        if (!ALLOWED_STATUS.contains(status)) {
            throw new IllegalArgumentException("状态只能为 APPROVED 或 REJECTED");
        }

        if (auditRecordRepository.existsByLostItemId(record.getLostItemId())) {
            throw new IllegalStateException("失物 " + record.getLostItemId() + " 已审核过，不允许重复审核");
        }

        record.setStatus(status);
        return auditRecordRepository.save(record);
    }

    /** 按失物 ID 查审核结论 */
    public Optional<AuditRecord> findByLostItemId(Long lostItemId) {
        if (lostItemId == null || lostItemId <= 0) {
            return Optional.empty();
        }
        return auditRecordRepository.findByLostItemId(lostItemId);
    }

    /** 查询全部审核记录 */
    public List<AuditRecord> findAll() {
        return auditRecordRepository.findAll();
    }

    /** 按结论查询（APPROVED / REJECTED） */
    public List<AuditRecord> findByStatus(String status) {
        return auditRecordRepository.findByStatus(normalize(status));
    }

    /** 统一转成大写并去掉首尾空白，避免 "approved " 这类脏输入 */
    private String normalize(String status) {
        return status == null ? null : status.trim().toUpperCase();
    }
}
