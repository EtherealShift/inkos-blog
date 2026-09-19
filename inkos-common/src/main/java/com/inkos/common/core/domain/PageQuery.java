package com.inkos.common.core.domain;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;

/**
 * 分页查询基类。业务查询对象继承它即可获得分页与排序能力。
 */
@Getter
@Setter
@ToString
public class PageQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final int DEFAULT_PAGE_NUM = 1;
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 100;

    @Min(value = 1, message = "页码不能小于 1")
    private Integer pageNum = DEFAULT_PAGE_NUM;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = MAX_PAGE_SIZE, message = "每页条数不能超过 " + MAX_PAGE_SIZE)
    private Integer pageSize = DEFAULT_PAGE_SIZE;

    /** 模糊搜索关键字，语义由各业务自行定义 */
    private String keyword;

    /** 排序字段，必须由业务侧做白名单映射，严禁直接拼接进 SQL */
    private String orderBy;

    /** 是否升序，默认降序 */
    private Boolean asc = Boolean.FALSE;

    public int safePageNum() {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public int safePageSize() {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }
}
