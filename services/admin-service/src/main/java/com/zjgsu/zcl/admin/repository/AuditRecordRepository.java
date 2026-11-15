package com.zjgsu.zcl.admin.repository;

import com.zjgsu.zcl.admin.model.AuditRecord;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

/**
 * 审核记录的数据访问层。
 *
 * <p>此前审核服务直接用 {@code EntityManager.persist()}，既没有 Repository 也没有查询能力，
 * 属于"数据访问不完整"。改为标准 JPA 接口后，读写都有明确入口。
 */
public interface AuditRecordRepository extends JpaRepository<AuditRecord, Long> {

    /** 按失物 ID 查审核记录：一条失物只允许有一条审核结论 */
    Optional<AuditRecord> findByLostItemId(Long lostItemId);

    /** 幂等判断用：该失物是否已存在审核记录 */
    boolean existsByLostItemId(Long lostItemId);

    /** 按状态查（例如查所有已通过/已驳回的记录） */
    List<AuditRecord> findByStatus(String status);
}
