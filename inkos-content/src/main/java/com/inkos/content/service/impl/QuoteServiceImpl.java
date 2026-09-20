package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.content.entity.Quote;
import com.inkos.content.dto.QuoteForm;
import com.inkos.content.mapper.QuoteMapper;
import com.inkos.content.service.QuoteService;
import com.inkos.content.vo.QuoteVO;
import com.inkos.content.vo.QuoteAdminVO;
import com.inkos.common.core.constant.CacheConstants;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import com.inkos.content.cache.ContentCache;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class QuoteServiceImpl extends ServiceImpl<QuoteMapper, Quote> implements QuoteService {

    private static final int STATUS_ENABLED = 1;
    private static final int MAX_LIMIT = 20;

    /** 内容域缓存策略 */
    private final ContentCache contentCache;

    @Override
    public List<QuoteVO> listPublic(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        // 首页语句是每次打开首页都要读、几个月才改一次的典型字典数据
        return contentCache.getOrLoad(contentCache.quoteListKey(safeLimit), CacheConstants.DICTIONARY_TTL,
                () -> loadPublic(safeLimit));
    }

    private List<QuoteVO> loadPublic(int safeLimit) {
        // searchCount = false：首页只需要前 N 条，不需要总数。
        // 这样既省掉一次 count 查询，也不必把 limit 数字拼进 SQL。
        Page<Quote> page = new Page<>(1, safeLimit, false);
        baseMapper.selectPage(page, new LambdaQueryWrapper<Quote>()
                .eq(Quote::getStatus, STATUS_ENABLED)
                .orderByAsc(Quote::getSortOrder)
                .orderByAsc(Quote::getId));
        return page.getRecords().stream()
                .map(quote -> new QuoteVO(quote.getId(), quote.getContent(), quote.getAttribution(), quote.getHeadline(), quote.getDescription()))
                .toList();
    }

    @Override
    public List<QuoteAdminVO> listAdmin() {
        // 后台要看到自己的改动，刻意不走缓存
        return list(new LambdaQueryWrapper<Quote>()
                .orderByAsc(Quote::getSortOrder)
                .orderByAsc(Quote::getId))
                .stream()
                .map(quote -> new QuoteAdminVO(quote.getId(), quote.getContent(), quote.getAttribution(), quote.getSortOrder(), quote.getStatus(), quote.getHeadline(), quote.getDescription()))
                .toList();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public Long create(QuoteForm form) {
        Quote quote = new Quote();
        apply(form, quote);
        save(quote);
        contentCache.invalidateQuoteLists();
        return quote.getId();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void update(QuoteForm form) {
        Quote quote = getById(form.id());
        if (quote == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "语句不存在");
        }
        apply(form, quote);
        updateById(quote);
        contentCache.invalidateQuoteLists();
    }

    @Override
    @org.springframework.transaction.annotation.Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        if (id == null || getById(id) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "语句不存在");
        }
        removeById(id);
        contentCache.invalidateQuoteLists();
    }

    private void apply(QuoteForm form, Quote quote) {
        // Older clients omit both fields; preserve their existing hero copy.
        if (form.headline() != null || form.description() != null) {
            String headline = StrUtils.trimToEmpty(form.headline());
            String description = StrUtils.trimToEmpty(form.description());
            if (headline.isEmpty() != description.isEmpty())
                throw BusinessException.of(ResultCode.BAD_REQUEST, "首屏标题和说明请成组填写，或同时留空");
            quote.setHeadline(headline); quote.setDescription(description);
        }
        quote.setContent(StrUtils.trim(form.content()));
        quote.setAttribution(StrUtils.trim(form.attribution()));
        quote.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        quote.setStatus(form.status() == null ? STATUS_ENABLED : form.status());
    }
}
