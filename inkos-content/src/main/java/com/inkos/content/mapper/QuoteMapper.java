package com.inkos.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.content.entity.Quote;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface QuoteMapper extends BaseMapper<Quote> {
}
