package com.wikisprint.server.global.common.status;

public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
