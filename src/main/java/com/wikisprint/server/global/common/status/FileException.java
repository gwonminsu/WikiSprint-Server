package com.wikisprint.server.global.common.status;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(value = HttpStatus.BAD_REQUEST, reason = "FILE_ERROR")
public class FileException extends RuntimeException {
    public FileException(String message) {
        super(message);
    }
}
