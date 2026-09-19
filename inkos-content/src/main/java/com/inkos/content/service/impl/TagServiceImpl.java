package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.common.util.StrUtils;
import com.inkos.content.entity.Tag;
import com.inkos.content.mapper.TagMapper;
import com.inkos.content.service.TagService;
import com.inkos.content.vo.TagVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    @Override
    public List<TagVO> cloud() {
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

        for (String name : toCreate) {
            Tag tag = new Tag();
            tag.setName(name);
            tag.setSlug(uniqueSlug(name));
            tag.setArticleCount(0);
            save(tag);
            ids.add(tag.getId());
        }
        return new ArrayList<>(ids);
    }

    // ==================== 内部工具 ====================

    /**
     * 生成唯一 slug。表上没有对 slug 建唯一索引，
     * 但名称与 slug 一对一更利于 URL 稳定，这里仍做冲突规避。
     */
    private String uniqueSlug(String name) {
        String base = StrUtils.slugify(name);
        String candidate = base;
        int suffix = 2;
        while (existsSlug(candidate)) {
            String tail = "-" + suffix++;
            String head = base.length() + tail.length() > SLUG_MAX_LENGTH
                    ? base.substring(0, SLUG_MAX_LENGTH - tail.length())
                    : base;
            candidate = head + tail;
        }
        return candidate;
    }

    private boolean existsSlug(String slug) {
        return count(new LambdaQueryWrapper<Tag>().eq(Tag::getSlug, slug)) > 0;
    }
}
