package com.shaadrag.identity.config;

import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

// import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class JwtConfig {

        // @Value("${jwt.private-key}")
        // private String privateKey;

        // @Value("${jwt.public-key}")
        // private String publicKey;

        private static final String PATH = "/home/shaad/shaadrag.online/rag-ai-microservices-platform/identity-service/src/main/resources/keys";

        @Bean
        public PrivateKey jwtPrivateKey() throws Exception {

                String path = PATH+"/private.pem";

                String privateKey = readKey(
                                new FileInputStream(path));

                String key = privateKey
                                .replace("-----BEGIN PRIVATE KEY-----", "")
                                .replace("-----END PRIVATE KEY-----", "")
                                .replaceAll("\\s+", "");

                byte[] keyBytes = Base64.getDecoder().decode(key);

                PKCS8EncodedKeySpec keySpec = new PKCS8EncodedKeySpec(keyBytes);

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                return keyFactory.generatePrivate(keySpec);
        }

        @Bean
        public PublicKey jwtPublicKey() throws Exception {

                String path = PATH+"/public.pem";

                String publicKey = readKey(
                                new FileInputStream(path));

                String key = publicKey
                                .replace("-----BEGIN PUBLIC KEY-----", "")
                                .replace("-----END PUBLIC KEY-----", "")
                                .replaceAll("\\s+", "");

                byte[] keyBytes = Base64.getDecoder().decode(key);

                X509EncodedKeySpec keySpec = new X509EncodedKeySpec(keyBytes);

                KeyFactory keyFactory = KeyFactory.getInstance("RSA");

                return keyFactory.generatePublic(keySpec);
        }

        private String readKey(InputStream inputStream)
                        throws Exception {

                return new String(
                                inputStream.readAllBytes(),
                                StandardCharsets.UTF_8);
        }
}