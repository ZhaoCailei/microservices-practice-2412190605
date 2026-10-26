package com.zjgsu.zcl;

import com.zjgsu.zcl.dto.CreateLostItemRequest;
import com.zjgsu.zcl.model.LostItem;
import com.zjgsu.zcl.model.User;
import com.zjgsu.zcl.repository.LostItemRepository;
import com.zjgsu.zcl.repository.UserRepository;
import com.zjgsu.zcl.service.LostItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional // 保证每次测试后数据回滚，可重复运行
class LostItemIntegrationTest {

    @Autowired
    private LostItemService lostItemService;
    @Autowired
    private UserRepository userRepository;
    @Autowired
    private LostItemRepository lostItemRepository;

    @Test
    void testRepositoryAndServiceRules() {
        // 1. 准备数据 (保存一个 User)
        User user = new User();
        user.setUsername("test_user");
        user = userRepository.save(user);

        // 2. 验证业务规则：特征不能为空
        CreateLostItemRequest badRequest = new CreateLostItemRequest();
        badRequest.setUserId(user.getId());
        badRequest.setItemName("校园卡");
        badRequest.setFeature(""); // 故意传空

        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            lostItemService.createLostItem(badRequest);
        });
        assertEquals("发布失败：物品特征不能为空", exception.getMessage());

        // 3. 验证正常创建与 Repository 读写
        CreateLostItemRequest goodRequest = new CreateLostItemRequest();
        goodRequest.setUserId(user.getId());
        goodRequest.setItemName("蓝牙耳机");
        goodRequest.setFeature("右耳有划痕");

        LostItem savedItem = lostItemService.createLostItem(goodRequest);

        assertNotNull(savedItem.getId());
        // 验证能否从数据库读出来
        assertTrue(lostItemRepository.findById(savedItem.getId()).isPresent());
    }
}