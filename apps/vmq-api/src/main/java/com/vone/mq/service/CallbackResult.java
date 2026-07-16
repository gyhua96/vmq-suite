package com.vone.mq.service;

public class CallbackResult {
    private final boolean success;
    private final String response;
    private final String errorMessage;

    private CallbackResult(boolean success, String response, String errorMessage) {
        this.success = success;
        this.response = response;
        this.errorMessage = errorMessage;
    }

    public static CallbackResult success(String response) {
        return new CallbackResult(true, response, null);
    }

    public static CallbackResult failure(String response, String errorMessage) {
        return new CallbackResult(false, response, errorMessage);
    }

    public boolean isSuccess() {
        return success;
    }

    public String getResponse() {
        return response;
    }

    public String getErrorMessage() {
        return errorMessage;
    }
}
