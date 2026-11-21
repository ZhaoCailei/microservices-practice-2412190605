package com.zjgsu.zcl.user.service;

import com.zjgsu.zcl.user.model.User;
import com.zjgsu.zcl.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 用户服务业务层测试：验证 Service 层的查询口径，不依赖真实 MySQL / Nacos。
 */
@SpringBootTest(properties = {
        "spring.cloud.nacos.discovery.enabled=false",
        "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:userdb_service;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never",
        "app.jwt.secret=MDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDAwMDA="
})
@Transactional
class UserServiceTest {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void findById_shouldReturnSavedUser() {
        User user = new User();
        user.setUsername("svc_test_user");
        User saved = userRepository.save(user);

        assertTrue(userService.findById(saved.getId()).isPresent());
        assertEquals("svc_test_user", userService.findById(saved.getId()).orElseThrow().getUsername());
    }

    @Test
    void findById_shouldReturnEmptyWhenMissing() {
        assertTrue(userService.findById(999L).isEmpty());
    }

    @Test
    void findById_shouldReturnEmptyForIllegalId() {
        assertTrue(userService.findById(null).isEmpty());
        assertTrue(userService.findById(0L).isEmpty());
    }

    @Test
    void exists_shouldReflectRepositoryState() {
        User user = new User();
        user.setUsername("svc_exists_user");
        User saved = userRepository.save(user);

        assertTrue(userService.exists(saved.getId()));
        assertFalse(userService.exists(123456L));
        assertFalse(userService.exists(null));
    }

    @Test
    void findAll_shouldReturnList() {
        User user = new User();
        user.setUsername("svc_list_user");
        userRepository.save(user);

        List<User> users = userService.findAll();
        assertNotNull(users);
        assertTrue(users.stream().anyMatch(u -> "svc_list_user".equals(u.getUsername())));
    }
}
