package com.shaadrag.gateway.security;

import com.shaadrag.gateway.handler.ErrorResponseWriter;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@RequiredArgsConstructor
public class GatewayAccessDeniedHandler
        implements AccessDeniedHandler {

    private final ErrorResponseWriter errorResponseWriter;

    @Override
    public void handle(
            HttpServletRequest request,
            HttpServletResponse response,
            AccessDeniedException exception
    ) throws IOException {

        errorResponseWriter.write(
                request,
                response,
                HttpServletResponse.SC_FORBIDDEN,
                "FORBIDDEN",
                "You do not have permission to access this resource"
        );
    }
}