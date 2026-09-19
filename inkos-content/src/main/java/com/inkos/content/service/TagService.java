package com.inkos.content.service;

import com.inkos.content.vo.TagVO;

import java.util.List;

/**
 * 标签服务。
 */
public interface TagService {

    /**
     * 标签云：按关联文章数倒序返回全部标签。
     *
     * @return 标签列表
     */
    List<TagVO> cloud();

    /**
     * 按名称批量解析标签 id，不存在的标签自动创建。
     *
     * @param names 标签名称列表
     * @return 去重后的标签 id 列表
     */
    List<Long> resolveTagIds(List<String> names);
}
