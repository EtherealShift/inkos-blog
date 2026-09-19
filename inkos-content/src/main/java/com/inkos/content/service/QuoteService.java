package com.inkos.content.service;

import com.inkos.content.vo.QuoteVO;
import com.inkos.content.vo.QuoteAdminVO;
import com.inkos.content.dto.QuoteForm;

import java.util.List;

/** 首页语句库服务。 */
public interface QuoteService {

    /** 返回启用语句，按编辑排序输出。 */
    List<QuoteVO> listPublic(int limit);

    List<QuoteAdminVO> listAdmin();

    Long create(QuoteForm form);

    void update(QuoteForm form);

    void delete(Long id);
}
