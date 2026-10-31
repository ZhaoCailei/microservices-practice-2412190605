package com.zjgsu.zcl.lost.repository;

import com.zjgsu.zcl.lost.model.LostItem;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LostItemRepository extends JpaRepository<LostItem, Long> {
}
