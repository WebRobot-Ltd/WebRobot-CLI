package org.webrobot.cli.commands.models;

public class ErrorException {

    private String exception;
    private String errorType;

    public ErrorException(String msg,String errorType) {
        this.exception = msg;
        this.errorType = errorType;
    }

    public String getErrorType() {
        return errorType;
    }

    public void setErrorType(String errorType) {
        this.errorType = errorType;
    }

    public String getException() {
        return exception;
    }

    public void setException(String exception) {
        this.exception = exception;
    }

}