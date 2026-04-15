package com.smartgrocery.backend.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.smartgrocery.backend.dto.ApiResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice;

@RestControllerAdvice(basePackages = "com.smartgrocery.backend.controller")
public class GlobalResponseWrapper implements ResponseBodyAdvice<Object> {

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    public boolean supports(MethodParameter returnType, Class<? extends HttpMessageConverter<?>> converterType) {
        // Do not wrap if it is already an ApiResponse or if it's Swagger/API Docs
        return !returnType.getParameterType().equals(ApiResponse.class);
    }

    @Override
    public Object beforeBodyWrite(
            Object body,
            MethodParameter returnType,
            MediaType selectedContentType,
            Class<? extends HttpMessageConverter<?>> selectedConverterType,
            ServerHttpRequest request,
            ServerHttpResponse response
    ) {
        if (body instanceof ApiResponse) {
            return body;
        }

        // Special handling for Strings to avoid ClassCastException in HttpMessageConverter
        if (body instanceof String) {
            try {
                return objectMapper.writeValueAsString(ApiResponse.success(body));
            } catch (JsonProcessingException e) {
                return ApiResponse.error(500, "Encoding error");
            }
        }

        // Do not wrap if the body is null (though we could)
        if (body == null) {
            return ApiResponse.success(null);
        }

        // Generic wrapping
        return ApiResponse.success(body);
    }
}
