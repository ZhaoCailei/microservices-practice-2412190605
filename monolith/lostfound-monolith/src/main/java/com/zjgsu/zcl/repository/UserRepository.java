package com.zjgsu.zcl.repository;
import com.zjgsu.zcl.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
public interface UserRepository extends JpaRepository<User, Long> {}