package com.zjgsu.zcl.service;
import com.zjgsu.zcl.dto.CreateLostItemRequest;
import com.zjgsu.zcl.model.LostItem;
import java.util.List;

public interface LostItemService {
    LostItem createLostItem(CreateLostItemRequest request);
    List<LostItem> getAllLostItems();
    LostItem getLostItemById(Long id); // 新增查询
    void deleteLostItem(Long id);      // 新增删除
}