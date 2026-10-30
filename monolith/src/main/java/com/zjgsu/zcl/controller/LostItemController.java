package com.zjgsu.zcl.controller;

import com.zjgsu.zcl.dto.CreateLostItemRequest;
import com.zjgsu.zcl.model.LostItem;
import com.zjgsu.zcl.service.LostItemService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/lost-items")
public class LostItemController {
    private final LostItemService lostItemService;

    public LostItemController(LostItemService lostItemService) {
        this.lostItemService = lostItemService;
    }

    // 接口1：查询全部
    @GetMapping
    public ResponseEntity<List<LostItem>> getAll() {
        return ResponseEntity.ok(lostItemService.getAllLostItems());
    }

    // 接口2：根据ID查询
    @GetMapping("/{id}")
    public ResponseEntity<LostItem> getById(@PathVariable Long id) {
        LostItem item = lostItemService.getLostItemById(id);
        return item != null ? ResponseEntity.ok(item) : ResponseEntity.notFound().build();
    }

    // 接口3：创建失物
    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLostItemRequest request) {
        try {
            LostItem created = lostItemService.createLostItem(request);
            return ResponseEntity.ok(created);
        } catch (IllegalArgumentException e) {
            // 如果触发特征为空的规则，返回 400 错误
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    // 接口4：删除失物
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        lostItemService.deleteLostItem(id);
        return ResponseEntity.ok().build();
    }
}