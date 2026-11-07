package com.zjgsu.zcl.lost.controller;

import com.zjgsu.zcl.lost.client.UserServiceUnavailableException;
import com.zjgsu.zcl.lost.model.LostItem;
import com.zjgsu.zcl.lost.service.LostItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {

    private final LostItemService lostItemService;

    public LostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    @PostMapping
    public ResponseEntity<?> createLostItem(@RequestBody LostItem item) {
        try {
            LostItem saved = lostItemService.createLostItem(item);
            return ResponseEntity.ok("发布成功，失物ID：" + saved.getId());
        } catch (UserServiceUnavailableException e) {
            // 依赖服务不可用（例如 Nacos 中 user-service 实例列表为空）
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body("依赖服务不可用：" + e.getMessage());
        } catch (IllegalArgumentException e) {
            // 用户不存在：保留原有业务响应
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}
