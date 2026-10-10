package com.knowit.common;

/**
 * 分页查询入参。页码从 1 开始。
 *
 * @param page 页码，1 起
 * @param size 每页条数，上限由调用方校验
 */
public record PageQuery(int page, int size) {

    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 200;

    public PageQuery {
        page = page <= 0 ? DEFAULT_PAGE : page;
        size = size <= 0 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
    }

    public static PageQuery of(Integer page, Integer size) {
        return new PageQuery(
                page == null ? DEFAULT_PAGE : page, size == null ? DEFAULT_SIZE : size);
    }

    public int offset() {
        return (page - 1) * size;
    }
}
