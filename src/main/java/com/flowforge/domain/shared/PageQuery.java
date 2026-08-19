package com.flowforge.domain.shared;

public record PageQuery(int page, int size) {

    public static final int MAX_SIZE = 100;
    public static final int DEFAULT_SIZE = 20;
    public static final int MAX_PAGE = Integer.MAX_VALUE / MAX_SIZE;

    public PageQuery {
        if (page < 0 || page > MAX_PAGE) {
            throw new IllegalArgumentException("page must be between 0 and " + MAX_PAGE);
        }
        if (size < 1 || size > MAX_SIZE) {
            throw new IllegalArgumentException("size must be between 1 and " + MAX_SIZE);
        }
    }

    public static PageQuery of(int page, int size) {
        return new PageQuery(page, size);
    }

    public static PageQuery defaults() {
        return new PageQuery(0, DEFAULT_SIZE);
    }

    public int offset() {
        return page * size;
    }
}
