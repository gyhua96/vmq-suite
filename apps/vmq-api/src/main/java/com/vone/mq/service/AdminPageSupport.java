package com.vone.mq.service;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AdminPageSupport {
    private static final int DEFAULT_PAGE = 1;
    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 100;

    private AdminPageSupport() {
    }

    static Pageable byIdDesc(Integer page, Integer limit) {
        int safePage = page == null || page < 1 ? DEFAULT_PAGE : page;
        int safeLimit = limit == null || limit < 1 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        return PageRequest.of(safePage - 1, safeLimit, Sort.Direction.DESC, "id");
    }
}
