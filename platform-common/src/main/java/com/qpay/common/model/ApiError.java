package com.qpay.common.model;

import java.time.Instant;
import java.util.List;

public record ApiError(String code, String message, String requestId, Instant timestamp, List<Detail> details) {
    public record Detail(String field, String issue) {
    }
}

