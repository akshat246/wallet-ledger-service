package com.natwest.wallet.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class SuccessResponse<T> {
    private T data;
    private String message;
    private Long timestamp;
    
    public static <T> SuccessResponse<T> ok(T data, String message) {
        return SuccessResponse.<T>builder()
                .data(data)
                .message(message)
                .timestamp(System.currentTimeMillis())
                .build();
    }
    
    public static <T> SuccessResponse<T> ok(T data) {
        return SuccessResponse.<T>builder()
                .data(data)
                .message("Success")
                .timestamp(System.currentTimeMillis())
                .build();
    }
}