package com.vone.mq.domain;

public final class CallbackTaskState {
    public static final int PENDING = 0;
    public static final int SUCCESS = 1;
    public static final int RETRY_WAITING = 2;
    public static final int FINAL_FAILED = 3;
    public static final int CLAIMED = 4;

    private CallbackTaskState() {
    }
}
