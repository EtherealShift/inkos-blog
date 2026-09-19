package com.inkos.content.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.spring.service.impl.ServiceImpl;
import com.inkos.content.entity.Quote;
import com.inkos.content.dto.QuoteForm;
import com.inkos.content.mapper.QuoteMapper;
import com.inkos.content.service.QuoteService;
import com.inkos.content.vo.QuoteVO;
import com.inkos.content.vo.QuoteAdminVO;
import com.inkos.common.core.enums.ResultCode;
import com.inkos.common.exception.BusinessException;
import com.inkos.common.util.StrUtils;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class QuoteServiceImpl extends ServiceImpl<QuoteMapper, Quote> implements QuoteService {

    private static final int STATUS_ENABLED = 1;
    private static final int MAX_LIMIT = 20;

    @Override
    public List<QuoteVO> listPublic(int limit) {
        int safeLimit = Math.max(1, Math.min(limit, MAX_LIMIT));
        return list(new LambdaQueryWrapper<Quote>()
                .eq(Quote::getStatus, STATUS_ENABLED)
                .orderByAsc(Quote::getSortOrder)
                .orderByAsc(Quote::getId)
                .last("LIMIT " + safeLimit))
                .stream()
                .map(quote -> new QuoteVO(quote.getId(), quote.getContent(), quote.getAttribution()))
                .toList();
    }

    @Override
    public List<QuoteAdminVO> listAdmin() {
        return list(new LambdaQueryWrapper<Quote>()
                .orderByAsc(Quote::getSortOrder)
                .orderByAsc(Quote::getId))
                .stream()
                .map(quote -> new QuoteAdminVO(quote.getId(), quote.getContent(), quote.getAttribution(), quote.getSortOrder(), quote.getStatus()))
                .toList();
    }

    @Override
    public Long create(QuoteForm form) {
        Quote quote = new Quote();
        apply(form, quote);
        save(quote);
        return quote.getId();
    }

    @Override
    public void update(QuoteForm form) {
        Quote quote = getById(form.id());
        if (quote == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "语句不存在");
        }
        apply(form, quote);
        updateById(quote);
    }

    @Override
    public void delete(Long id) {
        if (id == null || getById(id) == null) {
            throw BusinessException.of(ResultCode.NOT_FOUND, "语句不存在");
        }
        removeById(id);
    }

    private void apply(QuoteForm form, Quote quote) {
        quote.setContent(StrUtils.trim(form.content()));
        quote.setAttribution(StrUtils.trim(form.attribution()));
        quote.setSortOrder(form.sortOrder() == null ? 0 : form.sortOrder());
        quote.setStatus(form.status() == null ? STATUS_ENABLED : form.status());
    }
}
