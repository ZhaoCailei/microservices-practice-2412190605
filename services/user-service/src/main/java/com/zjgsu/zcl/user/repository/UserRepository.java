package com.zjgsu.zcl.user.repository;

import com.zjgsu.zcl.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    /** 登录用：按用户名查账号（用户名有唯一约束，所以最多一条） */
    Optional<User> findByUsername(String username);
}
