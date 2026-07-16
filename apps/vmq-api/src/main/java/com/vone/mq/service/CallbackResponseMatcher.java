package com.vone.mq.service;

final class CallbackResponseMatcher {
    private static final String SUCCESS_RESPONSE = "success";

    private CallbackResponseMatcher() {
    }

    static boolean isSuccess(String response) {
        return response != null && SUCCESS_RESPONSE.equalsIgnoreCase(response.trim());
    }
}
