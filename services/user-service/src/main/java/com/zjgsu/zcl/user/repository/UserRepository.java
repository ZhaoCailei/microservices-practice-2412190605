package com.zjgsu.zcl.user.repository;

import com.zjgsu.zcl.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {
}