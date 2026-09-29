package com.natwest.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {
    private String errorCode;
    private String message;
    private Long timestamp;
    private String path;
    private int status;
    private String error;
    
    public static ErrorResponse of(String errorCode, String message, String path, int status) {
        return ErrorResponse.builder()
                .errorCode(errorCode)
                .message(message)
                .path(path)
                .status(status)
                .timestamp(System.currentTimeMillis())
                .build();
    }
}