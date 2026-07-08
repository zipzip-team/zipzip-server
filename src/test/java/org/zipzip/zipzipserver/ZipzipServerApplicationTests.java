package org.zipzip.zipzipserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "jwt.issuer=zipzip-server-test",
            "jwt.secret=test-jwt-secret-must-be-at-least-32-characters",
            "jwt.access-token-expiration=30m",
            "jwt.refresh-token-expiration=14d"
        })
class ZipzipServerApplicationTests {

    @Test
    void contextLoads() {}
}
