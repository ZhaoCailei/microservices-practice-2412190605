package com.zjgsu.zcl.user.service;

import com.zjgsu.zcl.user.model.User;
import com.zjgsu.zcl.user.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * 用户服务的业务层。
 *
 * <p>存在的意义：Controller 只负责 HTTP 协议转换，数据访问细节收敛到 Repository，
 * 中间这一层放"业务语义"（用户是否存在、列表查询口径等），也便于被其他服务或内部任务复用。
 */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** 按 ID 查询用户；查不到返回 {@link Optional#empty()}，由调用方决定映射成 404 还是别的语义 */
    public Optional<User> findById(Long id) {
        if (id == null || id <= 0) {
            return Optional.empty();
        }
        return userRepository.findById(id);
    }

    /** 用户是否存在：给跨服务校验这类"只关心布尔结果"的场景用 */
    public boolean exists(Long id) {
        return id != null && id > 0 && userRepository.existsById(id);
    }

    /** 查询全部用户 */
    public List<User> findAll() {
        return userRepository.findAll();
    }
}
