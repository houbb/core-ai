package io.coreplatform.ai.application.exception;

public class ProviderProbeException extends RuntimeException {

    private final String errorCode;
    private final String userMessage;
    private final Integer statusCode;

    public ProviderProbeException(String errorCode, String message, String userMessage, Integer statusCode) {
        super(message);
        this.errorCode = errorCode;
        this.userMessage = userMessage;
        this.statusCode = statusCode;
    }

    public String errorCode() {
        return errorCode;
    }

    public String userMessage() {
        return userMessage;
    }

    public Integer statusCode() {
        return statusCode;
    }
}
