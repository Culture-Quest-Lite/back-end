package org.sep490.backend.common.exception;

import lombok.Getter;
import org.slf4j.helpers.FormattingTuple;
import org.slf4j.helpers.MessageFormatter;
import org.springframework.http.HttpStatus;

@Getter
public class BusinessException extends RuntimeException {
    private final HttpStatus status;
    private final String errorCode;

    public BusinessException(String message, Object... args) {
        this(HttpStatus.BAD_REQUEST, message, args);
    }

    public BusinessException(HttpStatus status, String message, Object... args) {
        super(formatMessage(message, args));
        this.status = status;
        this.errorCode = status.name();
    }

    private static String formatMessage(String message, Object... args) {
        if (args == null || args.length == 0) {
            return message;
        }
        FormattingTuple formattingTuple = MessageFormatter.arrayFormat(message, args);
        return formattingTuple.getMessage();
    }
}
