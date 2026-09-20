package com.inkos.admin;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 端到端冒烟测试。
 *
 * <p>覆盖三层真实链路：默认拒绝的鉴权 → 登录签发令牌 → 带令牌访问受保护接口。
 * 运行在本机 MySQL 的 {@code inkos_test} 库上（见 application-test.yml），
 * 因此需要本地有一个可连的 MySQL，口令与开发环境走同一份 application-local.yml。
 */
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
@SpringBootTest
class InkosApplicationTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("应用上下文可以正常启动")
    void contextLoads() {
        // 上下文启动成功即验证了：包扫描、MapperScan、MyBatis-Plus 插件、
        // Sa-Token 装配、建表脚本执行、种子数据初始化全部正常
        assertThat(mockMvc).isNotNull();
    }

    @Test
    @DisplayName("白名单接口无需登录即可访问")
    void publicPingIsAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/public/ping"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("UP"));
    }

    @Test
    @DisplayName("未登录访问受保护接口返回 401 业务码")
    void protectedEndpointRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("登录 → 获取用户信息 → 访问受保护接口，全链路打通")
    void loginThenAccessProtectedResource() throws Exception {
        String token = login("admin", "admin123");

        mockMvc.perform(get("/api/v1/auth/info").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.username").value("admin"))
                // 超管应拿到通配权限（单个 * 才能匹配任意段数的权限码）
                .andExpect(jsonPath("$.data.permissions[0]").value("*"));

        mockMvc.perform(get("/api/v1/admin/users").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        mockMvc.perform(get("/api/v1/admin/quotes").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(5));
    }

    @Test
    @DisplayName("密码错误返回 40101 而不是 500")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"admin","password":"wrong-password"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("种子数据中的已发布文章可以被前台检索到")
    void seededArticlesArePubliclyVisible() throws Exception {
        mockMvc.perform(get("/api/v1/public/articles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.total").value(3))
                // 作者昵称通过 admin 层的 AuthorNameResolver 适配器解析，不应为 null
                .andExpect(jsonPath("$.data.records[0].authorName").value("砚知管理员"));
    }

    @Test
    @DisplayName("首页语句库可公开读取")
    void publicQuotesAreAccessible() throws Exception {
        mockMvc.perform(get("/api/v1/public/quotes").param("limit", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].content").value("把时间折进一页纸。"));
    }

    @Test
    @DisplayName("后台列表返回状态与更新时间，搜索和分页可组合")
    void adminArticleListSupportsWorkspace() throws Exception {
        String token = login("admin", "admin123");
        mockMvc.perform(get("/api/v1/admin/articles")
                        .header("Authorization", "Bearer " + token)
                        .param("status", "2").param("keyword", "模块化")
                        .param("orderBy", "updateTime").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(2))
                .andExpect(jsonPath("$.data.records[0].updateTime").isNotEmpty());
        mockMvc.perform(get("/api/v1/admin/articles")
                        .header("Authorization", "Bearer " + token)
                        .param("keyword", "模块化").param("pageSize", "1").param("pageNum", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    @DisplayName("公开列表叠加分类标签过滤，无匹配时返回空分页")
    void publicArticleFiltersCompose() throws Exception {
        mockMvc.perform(get("/api/v1/public/articles")
                        .param("keyword", "模块化").param("categoryId", "2")
                        .param("orderBy", "viewCount").param("pageSize", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].categoryId").value("2"));
        mockMvc.perform(get("/api/v1/public/articles")
                        .param("keyword", "模块化").param("categoryId", "2").param("tagId", "999999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.records").isEmpty());
    }

    @Test
    @DisplayName("开发来源的预检和真实登录均被接受，陌生来源被拒绝")
    void corsAllowsFrontendOriginsOnly() throws Exception {
        for (String origin : new String[]{"http://localhost:4173", "http://127.0.0.1:4173"}) {
            mockMvc.perform(options("/api/v1/auth/login")
                            .header("Origin", origin)
                            .header("Access-Control-Request-Method", "POST")
                            .header("Access-Control-Request-Headers", "content-type"))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin));
            mockMvc.perform(post("/api/v1/auth/login").header("Origin", origin)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"username":"admin","password":"admin123","rememberMe":false}
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(header().string("Access-Control-Allow-Origin", origin))
                    .andExpect(jsonPath("$.data.tokenValue").isNotEmpty());
        }
        mockMvc.perform(options("/api/v1/auth/login")
                        .header("Origin", "https://untrusted.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    /** 登录取令牌。 */
    private String login(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"%s","password":"%s"}
                                """.formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        String token = body.path("data").path("tokenValue").asText();
        assertThat(token).isNotBlank();
        return token;
    }
}
