package com.inkos.content.service;

import com.inkos.common.core.domain.PageResult;
import com.inkos.content.dto.ArticleForm;
import com.inkos.content.dto.ArticleQuery;
import com.inkos.content.vo.ArticleListVO;
import com.inkos.content.vo.ArticleVO;
import com.inkos.content.vo.SearchResultVO;

import java.util.Collection;
import java.util.List;

/**
 * 文章服务。
 *
 * <p>读写分离：{@code pagePublic} / {@code getBySlug} / {@code listRelated} 面向访客，
 * 只暴露已发布内容；{@code pageAdmin} 及其余写操作面向后台。
 */
public interface ArticleService {

    /**
     * 前台分页：仅返回已发布文章，忽略入参中的 status。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ArticleListVO> pagePublic(ArticleQuery query);

    /**
     * 后台分页：返回全部状态的文章。
     *
     * @param query 查询条件
     * @return 分页结果
     */
    PageResult<ArticleListVO> pageAdmin(ArticleQuery query);

    /**
     * 按 slug 获取已发布文章详情，并累加浏览量。
     *
     * @param slug URL 标识
     * @return 文章详情
     */
    ArticleVO getBySlug(String slug);

    /**
     * 后台编辑态读取，不限状态（回收站文章仍可查看，但不可编辑）。
     *
     * @param id 文章 id
     * @return 文章详情
     */
    ArticleVO getByIdForEdit(Long id);

    /**
     * 新增文章，初始状态为草稿，固定归属默认作者。
     *
     * @param form 表单
     * @return 新文章 id
     */
    Long create(ArticleForm form);

    /**
     * 修改文章。
     *
     * @param form 表单，必须携带 id
     */
    void update(ArticleForm form);

    /**
     * 发布文章：状态置为已发布并记录发布时间。
     *
     * @param id 文章 id
     */
    void publish(Long id);

    /**
     * 下线文章。
     *
     * @param id 文章 id
     */
    void offline(Long id);

    /**
     * 逻辑删除文章。
     *
     * @param id 文章 id
     */
    void delete(Long id);

    /**
     * 相关文章：同分类下的其它已发布文章。
     *
     * @param articleId 当前文章 id
     * @param limit     条数上限
     * @return 文章列表
     */
    List<ArticleListVO> listRelated(Long articleId, int limit);

    /**
     * 检索已发布文章。
     *
     * <p>与 {@link #pagePublic} 的区别：会匹配正文，并为每条结果附带命中片段。
     * 同样强制只看已发布，忽略入参 status。
     *
     * @param query 查询条件，keyword 为必填（由上层校验）
     * @return 带命中片段的检索结果
     */
    PageResult<SearchResultVO> search(ArticleQuery query);

    /**
     * 按 id 批量取已发布文章的列表视图，保持传入顺序。
     *
     * <p>供「我的收藏」这类先由别的表定序、再回表取内容的场景使用：
     * 顺序由调用方的业务语义决定（收藏时间倒序），不能让 SQL 重新排序。
     *
     * @param ids 文章 id 集合
     * @return 已发布文章的列表视图，顺序与入参一致；不存在或未发布的会被跳过
     */
    List<ArticleListVO> listPublishedByIds(Collection<Long> ids);
}
