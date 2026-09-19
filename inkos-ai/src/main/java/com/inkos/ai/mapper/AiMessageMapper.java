package com.inkos.ai.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.ai.entity.AiMessage;
import org.apache.ibatis.annotations.Mapper;

/**
 * AI 消息 Mapper。
 */
@Mapper
public interface AiMessageMapper extends BaseMapper<AiMessage> {
}
