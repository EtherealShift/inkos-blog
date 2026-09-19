package com.inkos.common.core.domain;

import lombok.Getter;
import lombok.ToString;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * 分页结果。
 *
 * <p>刻意不依赖 MyBatis-Plus 的 {@code IPage}，基础设施细节不应泄漏到基础层；
 * 由各业务模块的 Service 负责把 {@code IPage} 转换为本对象。
 *
 * @param <T> 行数据类型
 */
@Getter
@ToString
public class PageResult<T> implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private long total;

    /** 当前页码，从 1 开始 */
    private long pageNum;

    /** 每页条数 */
    private long pageSize;

    /** 总页数 */
    private long pages;

    protected PageResult() {
    }

    private PageResult(List<T> records, long total, long pageNum, long pageSize) {
        this.records = records == null ? Collections.emptyList() : records;
        this.total = Math.max(total, 0);
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = pageSize <= 0 ? 0 : (this.total + pageSize - 1) / pageSize;
    }

    public static <T> PageResult<T> of(List<T> records, long total, long pageNum, long pageSize) {
        return new PageResult<>(records, total, pageNum, pageSize);
    }

    public static <T> PageResult<T> empty(long pageNum, long pageSize) {
        return new PageResult<>(Collections.emptyList(), 0, pageNum, pageSize);
    }

    /** 在保留分页元信息的前提下替换行数据（Entity -> VO 转换用） */
    public <R> PageResult<R> map(List<R> mapped) {
        return new PageResult<>(mapped, this.total, this.pageNum, this.pageSize);
    }
}
