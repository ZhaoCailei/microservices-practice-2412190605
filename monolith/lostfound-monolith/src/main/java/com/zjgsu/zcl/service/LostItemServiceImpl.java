package com.zjgsu.zcl.service.impl;

import com.zjgsu.zcl.dto.CreateLostItemRequest;
import com.zjgsu.zcl.model.LostItem;
import com.zjgsu.zcl.model.User;
import com.zjgsu.zcl.repository.LostItemRepository;
import com.zjgsu.zcl.repository.UserRepository;
import com.zjgsu.zcl.service.LostItemService;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LostItemServiceImpl implements LostItemService {
    private final LostItemRepository lostItemRepository;
    private final UserRepository userRepository;

    public LostItemServiceImpl(LostItemRepository lostItemRepository, UserRepository userRepository) {
        this.lostItemRepository = lostItemRepository;
        this.userRepository = userRepository;
    }

    @Override
    public LostItem createLostItem(CreateLostItemRequest request) {
        // 【核心业务规则】：必须填写物品特征，否则拒绝发布
        if (request.getFeature() == null || request.getFeature().trim().isEmpty()) {
            throw new IllegalArgumentException("发布失败：物品特征不能为空");
        }

        User user = userRepository.findById(request.getUserId())
                .orElseThrow(() -> new RuntimeException("用户不存在"));

        LostItem item = new LostItem();
        item.setItemName(request.getItemName());
        item.setFeature(request.getFeature());
        item.setUser(user);
        item.setStatus("PENDING"); // 默认待审核
        return lostItemRepository.save(item);
    }

    @Override
    public List<LostItem> getAllLostItems() {
        return lostItemRepository.findAll();
    }

    @Override
    public LostItem getLostItemById(Long id) {
        return lostItemRepository.findById(id).orElse(null);
    }

    @Override
    public void deleteLostItem(Long id) {
        lostItemRepository.deleteById(id);
    }
}