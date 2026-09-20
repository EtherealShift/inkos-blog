package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.core.constant.CacheConstants;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import com.inkos.content.cache.ContentCache;
import com.inkos.content.entity.Tag;
import com.inkos.content.dto.TagForm;
import com.inkos.content.entity.Article;
import com.inkos.content.entity.ArticleTag;
import com.inkos.content.mapper.ArticleMapper;
import com.inkos.content.mapper.ArticleTagMapper;
import com.inkos.content.mapper.TagMapper;
import com.inkos.content.service.TagService;
import com.inkos.content.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 标签服务实现。
 */
@Service
@RequiredArgsConstructor
public class TagServiceImpl extends ServiceImpl<TagMapper, Tag> implements TagService {

    /** slug 列长度上限 */
    private static final int SLUG_MAX_LENGTH = 64;

    /** 标签名单次最多接受的数量，防止恶意批量建标 */
    private static final int MAX_RESOLVE_SIZE = 100;

    /** slug 冲突探测的最大轮次。唯一索引在数据库上，这里是防止空转的兜底 */
    private static final int MAX_SLUG_ROUNDS = 200;

    /** 内容域缓存策略 */
    private final ContentCache contentCache;
    private final ArticleTagMapper articleTagMapper;
    private final ArticleMapper articleMapper;

    @Override
    public List<TagVO> listAdmin() { return buildCloud(); }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long create(TagForm form) {
        Tag tag = new Tag(); applyForm(tag, form); tag.setArticleCount(0); save(tag);
        contentCache.evictTagCloud(); return tag.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(TagForm form) {
        Tag tag = requireTag(form.id()); applyForm(tag, form); updateById(tag);
        contentCache.evictTagCloud(); contentCache.invalidateArticleLists();
        for (ArticleTag relation : articleTagMapper.selectList(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, tag.getId()))) {
            Article article = articleMapper.selectById(relation.getArticleId());
            if (article != null) contentCache.evictArticleDetail(article.getSlug());
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTag(id);
        if (articleTagMapper.selectCount(new LambdaQueryWrapper<ArticleTag>().eq(ArticleTag::getTagId, id)) > 0)
            throw BusinessException.of(ResultCode.CONFLICT, "标签仍被文章引用，请先移除关联");
        removeById(id); contentCache.evictTagCloud(); contentCache.invalidateArticleLists();
    }

    private Tag requireTag(Long id) {
        Tag tag = getById(id);
        if (tag == null) throw BusinessException.of(ResultCode.NOT_FOUND, "标签不存在");
        return tag;
    }

    private void applyForm(Tag tag, TagForm form) {
        String name = StrUtils.trim(form.name());
        String slug = StrUtils.slugify(StrUtils.isBlank(form.slug()) ? name : form.slug());
        if (StrUtils.isBlank(name) || StrUtils.isBlank(slug)) throw BusinessException.of(ResultCode.BAD_REQUEST, "请填写标签名称和有效标识");
        if (count(new LambdaQueryWrapper<Tag>().ne(tag.getId() != null, Tag::getId, tag.getId())
                .and(w -> w.eq(Tag::getName, name).or().eq(Tag::getSlug, slug))) > 0)
            throw BusinessException.of(ResultCode.CONFLICT, "标签名称或标识已存在");
        tag.setName(name); tag.setSlug(slug);
    }


    @Override
    public List<TagVO> cloud() {
        return contentCache.getOrLoad(CacheConstants.TAG_CLOUD_KEY, CacheConstants.DICTIONARY_TTL,
                this::buildCloud);
    }

    private List<TagVO> buildCloud() {
        return list(new LambdaQueryWrapper<Tag>()
                .orderByDesc(Tag::getArticleCount)
                .orderByAsc(Tag::getId))
                .stream()
                .map(tag -> new TagVO(tag.getId(), tag.getName(), tag.getSlug(), tag.getArticleCount()))
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public List<Long> resolveTagIds(List<String> names) {
        if (names == null || names.isEmpty()) {
            return Collections.emptyList();
        }

        // 归一化：去空白、丢弃空名、按名去重，最多保留 MAX_RESOLVE_SIZE 个
        Map<String, String> byName = new LinkedHashMap<>();
        for (String name : names) {
            if (StrUtils.isBlank(name)) {
                continue;
            }
            String trimmed = StrUtils.trim(name);
            byName.putIfAbsent(trimmed, trimmed);
            if (byName.size() >= MAX_RESOLVE_SIZE) {
                break;
            }
        }
        if (byName.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Long> existing = new LinkedHashMap<>();
        List<Tag> found = list(new LambdaQueryWrapper<Tag>().in(Tag::getName, byName.keySet()));
        for (Tag tag : found) {
            existing.putIfAbsent(tag.getName(), tag.getId());
        }

        Set<Long> ids = new LinkedHashSet<>();
        List<String> toCreate = new ArrayList<>();
        for (String name : byName.keySet()) {
            Long id = existing.get(name);
            if (id != null) {
                ids.add(id);
            } else {
                toCreate.add(name);
            }
        }

        if (!toCreate.isEmpty()) {
            List<String> slugs = allocateSlugs(toCreate);
            for (int index = 0; index < toCreate.size(); index++) {
                Tag tag = new Tag();
                tag.setName(toCreate.get(index));
                tag.setSlug(slugs.get(index));
                tag.setArticleCount(0);
                save(tag);
                ids.add(tag.getId());
            }
            // 标签集合变了，标签云缓存整体作废
            contentCache.evictTagCloud();
        }
        return new ArrayList<>(ids);
    }

    // ==================== 内部工具 ====================

    /**
     * 成批为新标签分配唯一 slug。
     *
     * <p>原来是「建一个查一次、冲突了再查一次」—— 一次提交 10 个新标签就是 10 次以上
     * 存在性查询，典型的 N+1。这里改成<b>按轮次批量探测</b>：第 1 轮用一条 {@code IN}
     * 查询取出全部基础 slug 的占用情况，只有真正冲突的才进入第 2 轮。
     * 绝大多数情况下总共只有一次查询。
     *
     * <p>同一批里两个不同的名字可能归一化成同一个 slug（例如 {@code "Spring Boot"}
     * 与 {@code "spring-boot"}），因此除了数据库里已占用的，还要排除本批已经分配掉的。
     *
     * @param names 待创建的标签名，顺序与返回值一一对应
     * @return 与 {@code names} 等长的唯一 slug 列表
     */
    private List<String> allocateSlugs(List<String> names) {
        List<String> bases = names.stream().map(StrUtils::slugify).toList();
        List<String> allocated = new ArrayList<>(Collections.nCopies(names.size(), null));
        Set<String> claimed = new LinkedHashSet<>();
        List<Integer> pending = new ArrayList<>(names.size());
        for (int index = 0; index < names.size(); index++) {
            pending.add(index);
        }

        for (int round = 1; round <= MAX_SLUG_ROUNDS && !pending.isEmpty(); round++) {
            Map<Integer, String> candidates = new LinkedHashMap<>();
            for (int index : pending) {
                candidates.put(index, round == 1 ? bases.get(index) : withSuffix(bases.get(index), round));
            }

            Set<String> taken = existingSlugs(candidates.values());
            List<Integer> stillPending = new ArrayList<>();
            for (Map.Entry<Integer, String> entry : candidates.entrySet()) {
                String candidate = entry.getValue();
                if (taken.contains(candidate) || !claimed.add(candidate)) {
                    stillPending.add(entry.getKey());
                } else {
                    allocated.set(entry.getKey(), candidate);
                }
            }
            pending = stillPending;
        }

        if (!pending.isEmpty()) {
            throw BusinessException.of(ResultCode.CONFLICT, "标签 slug 冲突过多，请检查 cms_tag 表数据");
        }
        return allocated;
    }

    /** 第 n 轮候选：基础 slug 追加 {@code -n}，并预留后缀长度避免超出列宽 */
    private String withSuffix(String base, int round) {
        String tail = "-" + round;
        String head = base.length() + tail.length() > SLUG_MAX_LENGTH
                ? base.substring(0, SLUG_MAX_LENGTH - tail.length())
                : base;
        return head + tail;
    }

    /** 一次查询取出候选 slug 里已被占用的那些 */
    private Set<String> existingSlugs(Collection<String> candidates) {
        if (candidates.isEmpty()) {
            return Set.of();
        }
        return list(new LambdaQueryWrapper<Tag>().in(Tag::getSlug, candidates)).stream()
                .map(Tag::getSlug)
                .collect(Collectors.toSet());
    }
}
