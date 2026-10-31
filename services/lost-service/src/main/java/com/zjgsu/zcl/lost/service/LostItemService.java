package com.zjgsu.zcl.lost.service;

import com.zjgsu.zcl.lost.client.UserClient;
import com.zjgsu.zcl.lost.model.LostItem;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import org.springframework.stereotype.Service;

@Service
public class LostItemService {

    private final UserClient userClient;

    @PersistenceContext
    private EntityManager entityManager;

    public LostItemService(UserClient userClient) {
        this.userClient = userClient;
    }

    @Transactional
    public LostItem createLostItem(LostItem item) {
        // 严格校验：如果依赖服务不可用或用户不存在，直接抛异常中断，不写库
        if (!userClient.checkUserExists(item.getUserId())) {
            throw new IllegalArgumentException("发布失败：用户不存在或用户服务不可用（校验未通过）");
        }

        entityManager.persist(item);
        return item;
    }
}