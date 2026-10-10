package com.knowit.common;

import java.util.List;

/** 分页结果。页码从 1 开始。 */
public record PageResult<T>(List<T> items, long total, int page, int size) {

    public int totalPages() {
        if (size <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / size);
    }

    public boolean hasNext() {
        return page < totalPages();
    }
}
