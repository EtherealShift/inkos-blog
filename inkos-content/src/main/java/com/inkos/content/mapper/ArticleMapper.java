package com.inkos.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.content.entity.Article;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

/**
 * 文章 Mapper。
 */
@Mapper
public interface ArticleMapper extends BaseMapper<Article> {

    /**
     * 浏览量自增。
     *
     * <p>用 SQL 侧原子自增而非「查出来 +1 再写回」，避免并发下的丢失更新。
     *
     * <p>注意必须用 {@code @Update} 而不是 {@code @Select}：SQL 确实会被执行，
     * 但 {@code @Select} 会按查询处理取值，返回 null 会直接抛
     * {@code BindingException: attempted to return null from a method with a primitive return type}。
     *
     * @param id 文章 id
     * @return 受影响行数
     */
    @Update("UPDATE cms_article SET view_count = view_count + 1 WHERE id = #{id} AND deleted = 0")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 热门文章：已发布且未删除，按浏览量、发布时间倒序取前 N 篇。
     *
     * @param limit 条数上限
     * @return 文章列表
     */
    @Select("SELECT * FROM cms_article "
            + "WHERE status = 2 AND deleted = 0 "
            + "ORDER BY view_count DESC, published_at DESC "
            + "LIMIT #{limit}")
    List<Article> selectHotArticles(@Param("limit") int limit);
}
