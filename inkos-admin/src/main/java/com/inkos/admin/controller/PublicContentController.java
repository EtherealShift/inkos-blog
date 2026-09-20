package com.inkos.admin.controller;

import com.inkos.common.core.domain.PageResult;
import com.inkos.common.core.domain.Result;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import com.inkos.content.dto.ArticleQuery;
import com.inkos.content.service.ArticleService;
import com.inkos.content.service.CategoryService;
import com.inkos.content.service.QuoteService;
import com.inkos.content.service.TagService;
import com.inkos.content.vo.ArticleListVO;
import com.inkos.content.vo.ArticleVO;
import com.inkos.content.vo.CategoryVO;
import com.inkos.content.vo.QuoteVO;
import com.inkos.content.vo.SearchResultVO;
import com.inkos.content.vo.TagVO;
import com.inkos.framework.security.SecurityUtils;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台内容接口（无需登录）。
 *
 * <p>路径统一挂在 {@code /api/v1/public/**} 白名单下。所有接口只暴露已发布内容，
 * 草稿与回收站文章不可能从这些入口泄漏。
 */
@Tag(name = "20. 内容 - 前台", description = "文章列表/详情、分类树、标签云")
@RestController
@RequestMapping("/api/v1/public")
@RequiredArgsConstructor
public class PublicContentController {

    private static final int DEFAULT_RELATED_LIMIT = 5;

    private final ArticleService articleService;
    private final CategoryService categoryService;
    private final TagService tagService;
    private final QuoteService quoteService;

    @Operation(summary = "文章分页列表", description = "仅返回已发布文章")
    @GetMapping("/articles")
    public Result<PageResult<ArticleListVO>> articles(ArticleQuery query) {
        return Result.ok(articleService.pagePublic(query));
    }

    @Operation(summary = "文章详情", description = "按 slug 查询，并累加浏览量（同一访客在去重窗口内只计一次）")
    @GetMapping("/articles/{slug}")
    public Result<ArticleVO> article(@PathVariable String slug) {
        return Result.ok(articleService.getBySlug(slug, viewerKey()));
    }

    @Operation(summary = "相关文章", description = "同分类下的其它已发布文章")
    @GetMapping("/articles/{id}/related")
    public Result<List<ArticleListVO>> related(@PathVariable Long id,
                                               @RequestParam(defaultValue = "" + DEFAULT_RELATED_LIMIT) int limit) {
        return Result.ok(articleService.listRelated(id, limit));
    }

    @Operation(summary = "分类树")
    @GetMapping("/categories")
    public Result<List<CategoryVO>> categories() {
        return Result.ok(categoryService.tree());
    }

    @Operation(summary = "标签云")
    @GetMapping("/tags")
    public Result<List<TagVO>> tags() {
        return Result.ok(tagService.cloud());
    }

    @Operation(summary = "首页语句", description = "返回启用的首页轮播语句")
    @GetMapping("/quotes")
    public Result<List<QuoteVO>> quotes(@RequestParam(defaultValue = "8") int limit) {
        return Result.ok(quoteService.listPublic(limit));
    }

    @Operation(summary = "检索文章",
            description = "关键字匹配标题 / 摘要 / 正文，可叠加分类、标签过滤；命中正文时返回上下文片段")
    @GetMapping("/search")
    public Result<PageResult<SearchResultVO>> search(ArticleQuery query) {
        if (StrUtils.isBlank(query.getKeyword())) {
            // 没关键字的「检索」等于全量列表，直接拒绝比悄悄退化成列表更有用
            throw BusinessException.of(ResultCode.BAD_REQUEST, "检索关键字不能为空");
        }
        return Result.ok(articleService.search(query));
    }

    /**
     * 阅读计数的去重维度。
     *
     * <p>登录用户按用户去重、游客按 IP 去重 —— 与限流用的是同一套思路：
     * 一个 IP 后面可能是整栋楼的人，所以只要拿得到用户身份就优先按用户算。
     *
     * <p>这是 Web 层的关注点（只有这里同时看得到请求头与登录态），因此在控制器里算出
     * 标识、以参数交给服务层，内容层不必依赖 servlet API。
     */
    private String viewerKey() {
        Long userId = SecurityUtils.getUserIdOrNull();
        return userId != null ? "u:" + userId : "ip:" + SecurityUtils.getClientIp();
    }
}
