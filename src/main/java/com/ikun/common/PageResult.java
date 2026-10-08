package com.ikun.common;

import com.baomidou.mybatisplus.core.metadata.IPage;
import lombok.Data;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 分页结果封装
 *
 * @param <T> 列表数据类型
 * @author smart-scenic
 */
@Data
public class PageResult<T> implements Serializable {

    /** 当前页数据 */
    private List<T> records;

    /** 总记录数 */
    private Long total;

    /** 当前页码 */
    private Long pageNum;

    /** 每页条数 */
    private Long pageSize;

    /** 总页数 */
    private Long pages;

    public PageResult() {
    }

    public PageResult(List<T> records, Long total, Long pageNum, Long pageSize) {
        this.records = records;
        this.total = total;
        this.pageNum = pageNum;
        this.pageSize = pageSize;
        this.pages = (pageSize == null || pageSize == 0) ? 0 : (total + pageSize - 1) / pageSize;
    }

    /** 直接由 MyBatis-Plus 分页对象转换 */
    public static <T> PageResult<T> of(IPage<T> page) {
        return new PageResult<>(page.getRecords(), page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 由 MyBatis-Plus 分页对象转换，并对记录做类型映射（Entity -> VO） */
    public static <E, T> PageResult<T> of(IPage<E> page, Function<E, T> mapper) {
        List<T> list = page.getRecords() == null
                ? Collections.emptyList()
                : page.getRecords().stream().map(mapper).collect(Collectors.toList());
        return new PageResult<>(list, page.getTotal(), page.getCurrent(), page.getSize());
    }

    /** 空分页 */
    public static <T> PageResult<T> empty() {
        return new PageResult<>(Collections.emptyList(), 0L, 1L, 10L);
    }
}
