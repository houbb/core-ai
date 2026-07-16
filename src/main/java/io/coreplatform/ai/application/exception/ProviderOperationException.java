package io.coreplatform.ai.application.exception;

public class ProviderOperationException extends RuntimeException {

    private final String errorCode;
    private final int httpStatus;

    public ProviderOperationException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }

    public String errorCode() {
        return errorCode;
    }

    public int httpStatus() {
        return httpStatus;
    }
}
