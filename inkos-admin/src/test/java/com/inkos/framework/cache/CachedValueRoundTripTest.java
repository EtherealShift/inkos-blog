package com.inkos.framework.cache;

import com.inkos.common.core.domain.PageResult;
import com.inkos.content.vo.ArticleListVO;
import com.inkos.content.vo.CategoryVO;
import com.inkos.content.vo.QuoteVO;
import com.inkos.framework.config.RedisConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJacksonJsonRedisSerializer;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 缓存值的往返测试：<b>真正被写进 Redis 的那些形状</b>能不能原样读回来。
 *
 * <p>{@code RedisConfigTest} 验证的是序列化器本身的契约（record / ArrayList / 不可变集合边界）。
 * 本类验证的是它的<b>使用者</b>：内容域实际缓存的是
 * {@code PageResult<ArticleListVO>}、分类树、标签云、首页语句，
 * 这些形状一旦读不回来，故障会表现为「接口偶发返回空数据」——
 * 而那是最难排查的一类问题。
 *
 * <p>不连 Redis：验证的是序列化契约，不是网络。
 */
class CachedValueRoundTripTest {

    private final GenericJacksonJsonRedisSerializer serializer = new RedisConfig().redisValueSerializer();

    @Test
    @DisplayName("文章列表分页结果：分页元信息与行数据必须一起活下来")
    void pageResultRoundTrip() {
        PageResult<ArticleListVO> original = PageResult.of(new ArrayList<>(List.of(article())), 42, 2, 10);

        Object restored = serializer.deserialize(serializer.serialize(original));

        assertThat(restored).isInstanceOf(PageResult.class);
        PageResult<?> page = (PageResult<?>) restored;
        // 元信息是这里的关键：PageResult 没有 setter，若靠 getter 推断可变性，
        // 只有 records 会被回填，total / pages 会静默变成 0 —— 前端分页直接失效
        assertThat(page.getTotal()).isEqualTo(42);
        assertThat(page.getPageNum()).isEqualTo(2);
        assertThat(page.getPageSize()).isEqualTo(10);
        assertThat(page.getPages()).isEqualTo(5);
        assertThat(page.getRecords()).hasSize(1);
        assertThat(page.getRecords().get(0)).isInstanceOf(ArticleListVO.class);
        assertThat((ArticleListVO) page.getRecords().get(0)).usingRecursiveComparison().isEqualTo(article());
    }

    @Test
    @DisplayName("空结果页也要能往返：没有文章时列表接口同样会写缓存")
    void emptyPageResultRoundTrip() {
        // 这正是 toListVos 在无数据时返回的 Collections.emptyList()
        PageResult<ArticleListVO> original = PageResult.of(Collections.emptyList(), 0, 1, 10);

        PageResult<?> page = (PageResult<?>) serializer.deserialize(serializer.serialize(original));

        assertThat(page.getTotal()).isZero();
        assertThat(page.getRecords()).isEmpty();
    }

    @Test
    @DisplayName("分类树：嵌套 children 与元素类型都要保留")
    void categoryTreeRoundTrip() {
        CategoryVO child = new CategoryVO(2L, 1L, "后端", "backend", "服务端开发", 1, 3, List.of());
        CategoryVO root = new CategoryVO(1L, 0L, "技术", "tech", "技术相关", 1, 5, List.of(child));

        Object restored = serializer.deserialize(serializer.serialize(new ArrayList<>(List.of(root))));

        assertThat(restored).isInstanceOf(ArrayList.class);
        List<?> list = (List<?>) restored;
        assertThat(list).hasSize(1);
        assertThat(list.get(0)).isInstanceOf(CategoryVO.class);
        CategoryVO restoredRoot = (CategoryVO) list.get(0);
        assertThat(restoredRoot.children()).hasSize(1);
        assertThat(restoredRoot.children().get(0)).isInstanceOf(CategoryVO.class);
        assertThat(restoredRoot).usingRecursiveComparison().isEqualTo(root);
    }

    @Test
    @DisplayName("首页语句：Stream.toList() 的结果必须先包成 ArrayList 才写缓存")
    void quoteListRoundTrip() {
        List<QuoteVO> fromStream = List.of(new QuoteVO(1L, "把时间折进一页纸。", "砚知")).stream().toList();

        // 直接写 toList() 的返回值（JDK 不可变集合）作为顶层值是读不回来的
        assertThatThrownBy(() -> serializer.deserialize(serializer.serialize(fromStream)))
                .isInstanceOf(RuntimeException.class);

        // RedisCacheService.put 在落盘前做的就是这一步复制
        Object restored = serializer.deserialize(serializer.serialize(new ArrayList<>(fromStream)));
        assertThat(restored).isInstanceOf(ArrayList.class);
        List<?> restoredList = (List<?>) restored;
        assertThat(restoredList).hasSize(1);
        assertThat(restoredList.get(0)).isEqualTo(new QuoteVO(1L, "把时间折进一页纸。", "砚知"));
    }

    private ArticleListVO article() {
        return new ArticleListVO(
                7L,
                "为什么我把博客系统做成了模块化单体",
                "why-modular-monolith",
                "从分层边界、依赖方向到拆分时机，讲清楚这个后端骨架的取舍。",
                null,
                2L,
                "后端",
                1L,
                "砚知管理员",
                List.of("Java", "Spring Boot"),
                128L,
                3,
                1,
                6,
                LocalDateTime.of(2026, 9, 19, 16, 30, 0),
                2,
                LocalDateTime.of(2026, 9, 19, 17, 0, 0), 0);
    }
}
