-- 测试账号（密码只存 BCrypt 哈希，不存明文）：
--   student_zhang / Student@123  → 角色 USER（普通用户）
--   admin_li      / Admin@123    → 角色 ADMIN（管理员）
-- 说明：这两条是本地/演示环境用的种子账号；新增账号时应走“哈希后入库”，不能明文写库。
INSERT INTO users (id, username, password_hash, role) VALUES
    (1, 'student_zhang', '$2a$10$lIQZ0JsPj0Nk9XjvEFH.Ee/i2uS0Iq2Ql/aSzLDSCyGikLG9d0y2q', 'USER')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = VALUES(role);

INSERT INTO users (id, username, password_hash, role) VALUES
    (2, 'admin_li', '$2a$10$0fYhI2iSQ/4VB8Woxe8NKuRiAxq8anXCH9.2BKD3nW026bypky3Xq', 'ADMIN')
ON DUPLICATE KEY UPDATE password_hash = VALUES(password_hash), role = VALUES(role);
