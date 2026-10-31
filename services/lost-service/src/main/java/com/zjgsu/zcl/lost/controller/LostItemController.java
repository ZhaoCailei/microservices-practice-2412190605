package com.zjgsu.zcl.lost.controller;

import com.zjgsu.zcl.lost.client.UserClient;
import com.zjgsu.zcl.lost.client.UserServiceUnavailableException;
import com.zjgsu.zcl.lost.model.LostItem;
import com.zjgsu.zcl.lost.repository.LostItemRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {

    private final LostItemRepository lostItemRepository;
    private final UserClient userClient;

    public LostItemController(LostItemRepository lostItemRepository, UserClient userClient) {
        this.lostItemRepository = lostItemRepository;
        this.userClient = userClient;
    }

    @PostMapping
    public ResponseEntity<String> createLostItem(@RequestBody LostItem item) {
        // 1. 参数校验（表上是 nullable=false，这里提前拦住，避免直接抛 500）
        if (item.getUserId() == null) {
            return badRequest("发布失败：userId 不能为空");
        }
        if (item.getItemName() == null || item.getItemName().isBlank()) {
            return badRequest("发布失败：itemName 不能为空");
        }
        if (item.getFeature() == null || item.getFeature().isBlank()) {
            return badRequest("发布失败：feature 不能为空");
        }

        // 2. 核心业务规则：跨服务调用检查用户是否存在
        try {
            if (!userClient.checkUserExists(item.getUserId())) {
                return badRequest("发布失败：用户不存在（跨服务校验未通过）");
            }
        } catch (UserServiceUnavailableException e) {
            // 对方服务挂了属于依赖故障，返回 503，而不是误导成“用户不存在”
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("发布失败：user-service 暂时不可用，请稍后重试");
        }

        // 3. 入库（JpaRepository.save 自带事务，不需要 @Transactional / EntityManager）
        LostItem saved = lostItemRepository.save(item);
        return ResponseEntity.ok("发布成功，失物ID：" + saved.getId());
    }

    /** 方便验证：查看已发布的失物列表 */
    @GetMapping
    public List<LostItem> listLostItems() {
        return lostItemRepository.findAll();
    }

    private static ResponseEntity<String> badRequest(String message) {
        return ResponseEntity.badRequest().body(message);
    }
}
