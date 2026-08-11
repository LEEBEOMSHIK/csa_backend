package org.example.csa_backend.storycontent.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record StoryContentErrorResponse(boolean success, String code, String message, Object data) {
}
