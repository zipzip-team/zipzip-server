package org.zipzip.zipzipserver;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(
        properties = {
            "jwt.issuer=zipzip-server-test",
            "jwt.access-secret=test-access-secret-must-be-at-least-32-characters",
            "jwt.refresh-secret=test-refresh-secret-must-be-at-least-32-characters",
            "jwt.access-token-expiration=30m",
            "jwt.refresh-token-expiration=14d",
            "apple.team-id=test-apple-team-id",
            "apple.client-id=test-apple-client-id",
            "apple.key-id=test-apple-key-id",
            "apple.private-key=test-apple-private-key"
        })
class ZipzipServerApplicationTests {

    @Test
    void contextLoads() {}
}
