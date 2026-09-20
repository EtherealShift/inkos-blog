package com.inkos.admin;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import com.inkos.admin.config.DefaultQuoteInitializer;
import com.inkos.content.cache.ContentCache;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import javax.sql.DataSource;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest
@ActiveProfiles({"dev", "test"})
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class WorkspaceIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired JdbcTemplate jdbc;
    @Autowired DataSource dataSource;
    @Autowired ContentCache cache;
    String admin, author;
    final List<Long> articles = new ArrayList<>(), categories = new ArrayList<>(), tags = new ArrayList<>(), quotes = new ArrayList<>();
    final String prefix = "workspace-" + UUID.randomUUID().toString().substring(0, 8);

    @BeforeAll void prepare() throws Exception {
        ResourceDatabasePopulator upgrade = new ResourceDatabasePopulator(new ClassPathResource("db/workspace-upgrade.sql"));
        upgrade.execute(dataSource); upgrade.execute(dataSource);
        admin = call("POST", "/api/v1/auth/login", Map.of("username","admin","password","admin123"), null, 200).path("data").path("tokenValue").asText();
        author = call("POST", "/api/v1/auth/login", Map.of("username","author","password","author123"), null, 200).path("data").path("tokenValue").asText();
    }
    @AfterAll void cleanup() {
        for (Long id : articles) { jdbc.update("DELETE FROM cms_article_tag WHERE article_id=?", id); jdbc.update("DELETE FROM cms_article WHERE id=?", id); }
        for (Long id : tags) jdbc.update("DELETE FROM cms_tag WHERE id=?", id);
        for (Long id : categories) jdbc.update("DELETE FROM cms_category WHERE id=?", id);
        for (Long id : quotes) jdbc.update("DELETE FROM cms_quote WHERE id=?", id);
        cache.invalidateArticleLists(); cache.evictCategoryTree(); cache.evictTagCloud(); cache.invalidateQuoteLists();
    }
    JsonNode call(String method, String url, Object body, String token, int expected) throws Exception {
        var builder = request(HttpMethod.valueOf(method), url);
        if (token != null) builder.header("Authorization", "Bearer " + token);
        if (body != null) builder.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        var response = mvc.perform(builder).andReturn().getResponse();
        assertThat(response.getStatus()).as(method + " " + url + ": " + response.getContentAsString()).isEqualTo(expected);
        return json.readTree(response.getContentAsString());
    }
    Map<String,Object> form(Long id, String slug, Long category, List<Long> tagIds, int visibility) {
        Map<String,Object> form = new LinkedHashMap<>();
        if (id != null) form.put("id", id);
        form.put("title", slug); form.put("slug", slug); form.put("contentMd", "# 正文\n\n测试内容"); form.put("summary", "摘要"); form.put("categoryId", category);
        form.put("tagIds", tagIds); form.put("visibility", visibility); return form;
    }
    long create(String slug, String token, Long category, List<Long> tagIds, int visibility) throws Exception {
        long id = call("POST","/api/v1/admin/articles", form(null,slug,category,tagIds,visibility),token,200).path("data").asLong();
        articles.add(id); return id;
    }
    @Test void completeLifecycleAndRealCacheInvalidation() throws Exception {
        String slug = prefix + "-lifecycle";
        long category = call("POST","/api/v1/admin/categories",Map.of("name",prefix,"slug",prefix),admin,200).path("data").asLong(); categories.add(category);
        long tag = call("POST","/api/v1/admin/tags",Map.of("name",prefix,"slug",prefix),admin,200).path("data").asLong(); tags.add(tag);
        long id = create(slug, author, category, List.of(tag),0);
        assertThat(call("GET","/api/v1/admin/articles/"+id,null,author,200).path("data").path("tagIds").get(0).asLong()).isEqualTo(tag);
        call("GET","/api/v1/public/articles/"+slug,null,null,404);
        call("PUT","/api/v1/admin/articles/"+id+"/publish",null,author,200);
        call("GET","/api/v1/public/articles/"+slug,null,null,200);
        call("PUT","/api/v1/admin/tags",Map.of("id",tag,"name",prefix+"-renamed","slug",prefix+"-renamed"),admin,200);
        assertThat(call("GET","/api/v1/public/articles/"+slug,null,null,200).path("data").path("tags").get(0).asText()).isEqualTo(prefix+"-renamed");
        call("DELETE","/api/v1/admin/categories/"+category,null,admin,409);
        call("DELETE","/api/v1/admin/tags/"+tag,null,admin,409);
        var update = form(id,slug,null,List.of(tag),0); update.put("title","线上新标题");
        call("PUT","/api/v1/admin/articles",update,author,200);
        JsonNode detail = call("GET","/api/v1/public/articles/"+slug,null,null,200).path("data");
        assertThat(detail.path("title").asText()).isEqualTo("线上新标题");
        assertThat(jdbc.queryForObject("SELECT category_id FROM cms_article WHERE id=?", Long.class, id)).isNull();
        call("PUT","/api/v1/admin/articles/"+id+"/offline",null,author,200);
        call("GET","/api/v1/public/articles/"+slug,null,null,404);
        call("PUT","/api/v1/admin/articles/"+id+"/recycle",null,author,200);
        assertThat(call("GET","/api/v1/admin/articles/"+id,null,author,200).path("data").path("status").asInt()).isEqualTo(4);
        call("PUT","/api/v1/admin/articles/"+id+"/publish",null,author,403);
        call("PUT","/api/v1/admin/articles/"+id+"/restore",null,author,200);
        assertThat(call("GET","/api/v1/admin/articles/"+id,null,author,200).path("data").path("status").asInt()).isZero();
        call("GET","/api/v1/public/articles/"+slug,null,null,404);
    }
    @Test void ownershipPrivateVisibilityAndInputValidation() throws Exception {
        long id = create(prefix+"-private",admin,null,List.of(),1);
        for (String operation : List.of("publish","offline","recycle","restore"))
            call("PUT","/api/v1/admin/articles/"+id+"/"+operation,null,author,403);
        call("GET","/api/v1/admin/articles/"+id,null,author,403);
        call("PUT","/api/v1/admin/articles",form(id,prefix+"-private",null,List.of(),1),author,403);
        call("DELETE","/api/v1/admin/articles/"+id,null,author,403);
        call("PUT","/api/v1/admin/articles/"+id+"/publish",null,admin,200);
        call("GET","/api/v1/public/articles/"+prefix+"-private",null,null,404);
        assertThat(call("GET","/api/v1/public/articles?keyword="+prefix+"-private",null,null,200).path("data").path("total").asLong()).isZero();
        assertThat(call("GET","/api/v1/admin/articles?keyword="+prefix+"-private",null,author,200).path("data").path("total").asLong()).isZero();
        call("GET","/api/v1/admin/tags",null,null,401);
        call("POST","/api/v1/admin/articles",Map.of("title"," ","contentMd",""),admin,400);
        call("POST","/api/v1/admin/tags",Map.of("name"," "),admin,400);
    }
    @Test void categoryCyclesAndQuoteEditing() throws Exception {
        long parent = call("POST","/api/v1/admin/categories",Map.of("name",prefix+"parent","slug",prefix+"parent"),admin,200).path("data").asLong(); categories.add(parent);
        long child = call("POST","/api/v1/admin/categories",Map.of("name",prefix+"child","slug",prefix+"child","parentId",parent),admin,200).path("data").asLong(); categories.add(child);
        call("PUT","/api/v1/admin/categories",Map.of("id",parent,"name",prefix+"parent","parentId",child),admin,409);
        call("DELETE","/api/v1/admin/categories/"+parent,null,admin,409);
        long quote = call("POST","/api/v1/admin/quotes",Map.of("content",prefix+"-standalone","status",1,"sortOrder",0),admin,200).path("data").asLong(); quotes.add(quote);
        assertThat(call("GET","/api/v1/public/quotes?limit=50",null,null,200).toString()).contains(prefix+"-standalone");
        call("PUT","/api/v1/admin/quotes",Map.of("id",quote,"content",prefix+"-standalone","status",0),admin,200);
        assertThat(call("GET","/api/v1/public/quotes?limit=50",null,null,200).toString()).doesNotContain(prefix+"-standalone");
    }
    @Test void heroCopyValidationCompatibilityAndPublicCache() throws Exception {
        Map<String,Object> form = new LinkedHashMap<>(Map.of("content",prefix+"hero","headline","第一行，\n第二行。","description","说明文字。","sortOrder",0,"status",1));
        long id = call("POST","/api/v1/admin/quotes",form,admin,200).path("data").asLong(); quotes.add(id); form.put("id",id);
        assertThat(call("GET","/api/v1/public/quotes?limit=20",null,null,200).toString()).contains("第二行。", "说明文字。");
        form.put("description", "更新后的说明");
        call("PUT","/api/v1/admin/quotes",form,admin,200);
        assertThat(call("GET","/api/v1/public/quotes?limit=20",null,null,200).toString()).contains("更新后的说明").doesNotContain("说明文字。");
        call("PUT","/api/v1/admin/quotes",Map.of("id",id,"content",prefix+"hero"),admin,200);
        assertThat(jdbc.queryForObject("SELECT description FROM cms_quote WHERE id=?",String.class,id)).isEqualTo("更新后的说明");
        form.put("headline", " ");
        call("PUT","/api/v1/admin/quotes",form,admin,400);
        assertThat(jdbc.queryForObject("SELECT headline FROM cms_quote WHERE id=?",String.class,id)).contains("第二行。");
        form.put("headline", "文".repeat(81));
        call("PUT","/api/v1/admin/quotes",form,admin,400);
        form.put("headline", ""); form.put("description", "");
        call("PUT","/api/v1/admin/quotes",form,admin,200);
        assertThat(call("GET","/api/v1/public/quotes?limit=20",null,null,200).toString()).doesNotContain("第二行。", "更新后的说明");
        assertThat(jdbc.queryForObject("SELECT headline FROM cms_quote WHERE id=?",String.class,id)).isEmpty();
    }
    @Test void concurrentEmptyDatabaseSeedAndDeletedRowsArePreserved() throws Exception {
        String database = "inkos_seed_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE DATABASE " + database);
        try {
            jdbc.execute("CREATE TABLE " + database + ".cms_quote LIKE cms_quote");
            DataSource isolated = new AbstractDataSource() {
                public Connection getConnection() throws SQLException {
                    Connection c = dataSource.getConnection(); String previous = c.getCatalog(); c.setCatalog(database);
                    return (Connection) java.lang.reflect.Proxy.newProxyInstance(Connection.class.getClassLoader(), new Class<?>[]{Connection.class}, (proxy, method, args) -> {
                        if (method.getName().equals("close")) { try { c.setCatalog(previous); } finally { c.close(); } return null; }
                        try { return method.invoke(c, args); } catch (java.lang.reflect.InvocationTargetException e) { throw e.getCause(); }
                    });
                }
                public Connection getConnection(String u, String p) throws SQLException { return getConnection(); }
            };
            DefaultQuoteInitializer initializer = new DefaultQuoteInitializer(isolated, cache);
            try (var executor = Executors.newFixedThreadPool(4)) {
                List<Callable<Void>> tasks = new ArrayList<>();
                for (int i=0;i<4;i++) tasks.add(() -> { initializer.run(null); return null; });
                for (var result : executor.invokeAll(tasks)) result.get();
            }
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+database+".cms_quote",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+database+".cms_quote WHERE headline IS NOT NULL AND description IS NOT NULL",Integer.class)).isEqualTo(5);
            // Exercise an old populated schema, then repeat the migration without overwriting edits.
            jdbc.execute("ALTER TABLE "+database+".cms_quote DROP COLUMN headline, DROP COLUMN description");
            ResourceDatabasePopulator heroUpgrade = new ResourceDatabasePopulator(new ClassPathResource("db/hero-copy-upgrade.sql"));
            heroUpgrade.execute(isolated); heroUpgrade.execute(isolated);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+database+".cms_quote WHERE headline IS NOT NULL AND description IS NOT NULL",Integer.class)).isEqualTo(5);
            jdbc.update("UPDATE "+database+".cms_quote SET headline='', description='' WHERE sort_order=1");
            jdbc.update("UPDATE "+database+".cms_quote SET headline='自定义', description='保留修改' WHERE sort_order=2");
            heroUpgrade.execute(isolated);
            assertThat(jdbc.queryForObject("SELECT headline FROM "+database+".cms_quote WHERE sort_order=1",String.class)).isEmpty();
            assertThat(jdbc.queryForObject("SELECT headline FROM "+database+".cms_quote WHERE sort_order=2",String.class)).isEqualTo("自定义");
            jdbc.update("UPDATE "+database+".cms_quote SET deleted=1");
            initializer.run(null);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+database+".cms_quote",Integer.class)).isEqualTo(5);
            assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM "+database+".cms_quote WHERE deleted=0",Integer.class)).isZero();
        } finally { jdbc.execute("DROP DATABASE " + database); }
    }
}
