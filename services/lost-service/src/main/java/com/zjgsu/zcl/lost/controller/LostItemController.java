package com.zjgsu.zcl.lost.controller;

import com.zjgsu.zcl.lost.model.LostItem;
import com.zjgsu.zcl.lost.service.LostItemService;
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
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}