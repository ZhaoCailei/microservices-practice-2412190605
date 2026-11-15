package com.zjgsu.zcl.admin.service;

import com.zjgsu.zcl.admin.model.AuditRecord;
import com.zjgsu.zcl.admin.repository.AuditRecordRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 审核服务业务层测试：覆盖三条规则（必填、状态白名单、不可重复审核）。
 * 不依赖真实 MySQL / Nacos，数据在用例结束后回滚。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:admindb_service;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@Transactional
class AuditServiceTest {

    @Autowired
    private AuditService auditService;

    @Autowired
    private AuditRecordRepository auditRecordRepository;

    @Test
    void audit_shouldSaveAndNormalizeStatus() {
        AuditRecord record = new AuditRecord();
        record.setLostItemId(1001L);
        record.setStatus(" approved "); // 脏输入：小写 + 空格

        AuditRecord saved = auditService.audit(record);

        assertNotNull(saved.getId());
        assertEquals("APPROVED", saved.getStatus());
        assertTrue(auditRecordRepository.findByLostItemId(1001L).isPresent());
    }

    @Test
    void audit_shouldRejectIllegalStatus() {
        AuditRecord record = new AuditRecord();
        record.setLostItemId(1002L);
        record.setStatus("DELETED");

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> auditService.audit(record));
        assertEquals("状态只能为 APPROVED 或 REJECTED", ex.getMessage());
        assertTrue(auditRecordRepository.findByLostItemId(1002L).isEmpty());
    }

    @Test
    void audit_shouldRejectMissingLostItemId() {
        AuditRecord record = new AuditRecord();
        record.setStatus("APPROVED");

        assertThrows(IllegalArgumentException.class, () -> auditService.audit(record));
    }

    @Test
    void audit_shouldRejectDuplicateAudit() {
        AuditRecord first = new AuditRecord();
        first.setLostItemId(1003L);
        first.setStatus("APPROVED");
        auditService.audit(first);

        AuditRecord second = new AuditRecord();
        second.setLostItemId(1003L);
        second.setStatus("REJECTED");

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> auditService.audit(second));
        assertTrue(ex.getMessage().contains("已审核过"));
    }

    @Test
    void findByLostItemId_shouldReturnEmptyWhenMissing() {
        assertTrue(auditService.findByLostItemId(8888L).isEmpty());
        assertTrue(auditService.findByLostItemId(null).isEmpty());
    }

    @Test
    void findByStatus_shouldFilterRecords() {
        AuditRecord approved = new AuditRecord();
        approved.setLostItemId(1004L);
        approved.setStatus("APPROVED");
        auditService.audit(approved);

        AuditRecord rejected = new AuditRecord();
        rejected.setLostItemId(1005L);
        rejected.setStatus("REJECTED");
        auditService.audit(rejected);

        assertEquals(1, auditService.findByStatus("APPROVED").size());
        assertEquals(1, auditService.findByStatus("REJECTED").size());
    }
}
