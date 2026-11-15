package com.zjgsu.zcl;

import com.zjgsu.zcl.model.User;
import com.zjgsu.zcl.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * API 层测试：验证 HTTP 契约（路由、Content-Type、状态码、JSON 结构）。
 * MockMvc 不打真实端口，但会完整走一遍 DispatcherServlet -> Controller -> Service -> Repository。
 * 数据源为 H2 内存库（见 src/test/resources/application.yml），@Transactional 保证用例结束后回滚。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class LostItemApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    /** 正常路径：创建成功返回 200，响应体带 id、status */
    @Test
    void create_shouldReturn200WithGeneratedId() throws Exception {
        User user = saveUser("api_user");
        String body = "{\"userId\":" + user.getId()
                + ",\"itemName\":\"水杯\",\"feature\":\"底部有贴纸\"}";

        mockMvc.perform(post("/api/lost-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.itemName").value("水杯"))
                .andExpect(jsonPath("$.feature").value("底部有贴纸"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    /** 错误路径：特征为纯空格，业务规则拦截，返回 400 且不落库 */
    @Test
    void create_withBlankFeature_shouldReturn400() throws Exception {
        User user = saveUser("api_user_blank");
        String body = "{\"userId\":" + user.getId()
                + ",\"itemName\":\"校园卡\",\"feature\":\"   \"}";

        mockMvc.perform(post("/api/lost-items")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    /** 查询路径：列表接口返回 200 且响应体是 JSON 数组 */
    @Test
    void list_shouldReturnJsonArray() throws Exception {
        mockMvc.perform(get("/api/lost-items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    private User saveUser(String username) {
        User user = new User();
        user.setUsername(username);
        return userRepository.save(user);
    }
}
