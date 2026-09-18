package com.shaadrag.gateway.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shaadrag.gateway.dto.response.ErrorResponse;
import com.shaadrag.gateway.exception.IdentityServiceException;

import feign.Response;
import feign.codec.ErrorDecoder;

import lombok.RequiredArgsConstructor;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;

@RequiredArgsConstructor
public class FeignErrorDecoder implements ErrorDecoder {

    private final ObjectMapper objectMapper;

    @Override
    public Exception decode(
            String methodKey,
            Response response) {

        System.out.println(
                "========== FEIGN ERROR DECODER =========="
        );

        System.out.println(
                "METHOD: " + methodKey
        );

        System.out.println(
                "STATUS: " + response.status()
        );

        System.out.println(
                "BODY EXISTS: " + (response.body() != null)
        );

        try {

            if (response.body() != null) {

                String body = new String(
                        response.body()
                                .asInputStream()
                                .readAllBytes(),
                        StandardCharsets.UTF_8
                );

                System.out.println(
                        "BODY: [" + body + "]"
                );

                System.out.println(
                        "========================================="
                );

                if (!body.isBlank()) {

                    ErrorResponse errorResponse =
                            objectMapper.readValue(
                                    body,
                                    ErrorResponse.class
                            );

                    return new IdentityServiceException(
                            response.status(),
                            errorResponse
                    );
                }
            }

        } catch (IOException e) {

            e.printStackTrace();
        }

        // =====================================================
        // 401 LOGIN
        // =====================================================

        if (response.status() == 401
                && methodKey.contains("IdentityClient#login")) {

            ErrorResponse errorResponse =
                    new ErrorResponse(
                            LocalDateTime.now(),
                            401,
                            "INVALID_CREDENTIALS",
                            "Invalid email or password",
                            "/auth/login"
                    );

            return new IdentityServiceException(
                    401,
                    errorResponse
            );
        }


        // =====================================================
        // FALLBACK
        // =====================================================

        return new IdentityServiceException(
                response.status(),
                null
        );
    }
}