package com.inkos.content.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.inkos.content.entity.ArticleTag;
import org.apache.ibatis.annotations.Mapper;

/**
 * 文章标签关联 Mapper。
 */
@Mapper
public interface ArticleTagMapper extends BaseMapper<ArticleTag> {
}
