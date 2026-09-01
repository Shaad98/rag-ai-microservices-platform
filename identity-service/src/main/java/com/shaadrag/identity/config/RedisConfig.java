package com.shaadrag.identity.config;

// import com.shaadrag.identity.dto.response.UserResponse;
import com.shaadrag.identity.model.RefreshTokenData;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, RefreshTokenData> refreshTokenRedisTemplate(
            RedisConnectionFactory connectionFactory) {

        RedisTemplate<String, RefreshTokenData> template =
                new RedisTemplate<>();

        StringRedisSerializer stringSerializer =
                new StringRedisSerializer();

        GenericJackson2JsonRedisSerializer jsonSerializer =
                new GenericJackson2JsonRedisSerializer();

        template.setConnectionFactory(connectionFactory);

        template.setKeySerializer(stringSerializer);
        template.setHashKeySerializer(stringSerializer);

        template.setValueSerializer(jsonSerializer);
        template.setHashValueSerializer(jsonSerializer);

        template.afterPropertiesSet();

        return template;
    }

    // @Bean
    // public RedisTemplate<String, UserResponse> userRedisTemplate(
    //         RedisConnectionFactory connectionFactory) {

    //     RedisTemplate<String, UserResponse> template =
    //             new RedisTemplate<>();

    //     StringRedisSerializer stringSerializer =
    //             new StringRedisSerializer();

    //     GenericJackson2JsonRedisSerializer jsonSerializer =
    //             new GenericJackson2JsonRedisSerializer();

    //     template.setConnectionFactory(connectionFactory);

    //     template.setKeySerializer(stringSerializer);
    //     template.setHashKeySerializer(stringSerializer);

    //     template.setValueSerializer(jsonSerializer);
    //     template.setHashValueSerializer(jsonSerializer);

    //     template.afterPropertiesSet();

    //     return template;
    // }
}