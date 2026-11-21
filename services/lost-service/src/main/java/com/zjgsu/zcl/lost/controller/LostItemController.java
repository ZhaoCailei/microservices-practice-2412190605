package com.zjgsu.zcl.lost.controller;

import com.zjgsu.zcl.lost.client.UserServiceUnavailableException;
import com.zjgsu.zcl.lost.model.LostItem;
import com.zjgsu.zcl.lost.service.LostItemService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {

    private final LostItemService lostItemService;

    public LostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    /**
     * 三种结果统一返回 JSON：
     * <ul>
     *   <li>成功：200 {"id": n, "message": "发布成功"}</li>
     *   <li>业务拒绝（用户不存在）：400 {"error": "发布失败：用户 x 不存在，无法发布失物"}</li>
     *   <li>依赖不可用：503 {"error": "依赖服务不可用：..."}</li>
     * </ul>
     */
    @PostMapping
    public ResponseEntity<Map<String, Object>> createLostItem(@RequestBody LostItem item) {
        try {
            LostItem saved = lostItemService.createLostItem(item);
            // 用 LinkedHashMap 固定字段顺序，响应体稳定为 {"id": n, "message": "发布成功"}
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", saved.getId());
            body.put("message", "发布成功");
            return ResponseEntity.ok(body);
        } catch (UserServiceUnavailableException e) {
            // 依赖服务不可用（例如 Nacos 中 user-service 实例列表为空）
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "依赖服务不可用：" + e.getMessage()));
        } catch (IllegalArgumentException e) {
            // 用户不存在：保留原有业务语义，改为 JSON 响应
            return ResponseEntity.badRequest()
                    .body(Map.of("error", e.getMessage()));
        }
    }
}
