package com.inkos.admin.config;

import com.inkos.content.cache.ContentCache;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
@Order(Ordered.LOWEST_PRECEDENCE)
@ConditionalOnProperty(name = "inkos.content.default-quotes.enabled", havingValue = "true", matchIfMissing = true)
public class DefaultQuoteInitializer implements ApplicationRunner {
    private final DataSource dataSource;
    private final ContentCache contentCache;

    @Override
    public void run(ApplicationArguments args) throws SQLException {
        // MySQL advisory lock is connection-scoped and remains held through commit.
        try (Connection connection = dataSource.getConnection()) {
            String lockName = "inkos:quotes:" + java.util.UUID.nameUUIDFromBytes(connection.getCatalog().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            boolean locked = false;
            try (var lock = connection.prepareStatement("SELECT GET_LOCK(?, 30)")) {
                lock.setString(1, lockName);
                try (var result = lock.executeQuery()) { locked = result.next() && result.getInt(1) == 1; }
            }
            if (!locked) throw new SQLException("未能获取默认语句初始化锁");
            try {
                connection.setAutoCommit(false);
                // Raw count intentionally includes logically deleted rows.
                try (var query = connection.prepareStatement("SELECT COUNT(*) FROM cms_quote");
                     var result = query.executeQuery()) {
                    result.next();
                    if (result.getLong(1) == 0) {
                        List<String> quotes = List.of("把时间折进一页纸。", "读得慢一点，世界会显出更多纹理。", "写下所知，也为未知留白。", "思想落在纸上，才开始拥有方向。", "愿每一次阅读，都抵达更深处。");
                        List<String> headlines = List.of("写下所知，\n为未知留白。", "读得慢些，\n想得更远。", "把日常写下，\n让灵感生长。", "以文字为舟，\n向更深处去。", "一页一世界，\n一读一相逢。");
                        List<String> descriptions = List.of("记下技术的推敲、阅读的回响，\n也记下生活里，值得停笔的瞬间。", "让匆忙停在纸页之外，\n在字里行间，遇见新的理解。", "从一个问题，到一次实践，\n把微小的发现，写成自己的答案。", "整理走过的路，也记录新的起点，\n让每一次落笔，都有所回响。", "分享技术与生活的片段，\n让远处的思考，在这里相遇。");
                        try (var insert = connection.prepareStatement("INSERT INTO cms_quote(content, attribution, sort_order, status, deleted, headline, description) VALUES (?, '砚知', ?, 1, 0, ?, ?)")) {
                            for (int i = 0; i < quotes.size(); i++) {
                                insert.setString(1, quotes.get(i)); insert.setInt(2, i + 1); insert.setString(3, headlines.get(i)); insert.setString(4, descriptions.get(i)); insert.addBatch();
                            }
                            insert.executeBatch();
                        }
                        log.info("初始化 {} 条默认首页语句", quotes.size());
                    }
                }
                connection.commit();
                contentCache.invalidateQuoteLists();
            } catch (SQLException | RuntimeException failure) {
                connection.rollback(); throw failure;
            } finally {
                connection.setAutoCommit(true);
                try (var release = connection.prepareStatement("SELECT RELEASE_LOCK(?)")) {
                    release.setString(1, lockName); release.execute();
                }
            }
        }
    }
}
