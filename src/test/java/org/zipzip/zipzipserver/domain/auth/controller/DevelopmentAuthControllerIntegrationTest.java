package org.zipzip.zipzipserver.domain.auth.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.zipzip.zipzipserver.domain.auth.jwt.JwtTokenProvider;
import org.zipzip.zipzipserver.domain.auth.repository.RefreshTokenRepository;
import org.zipzip.zipzipserver.domain.auth.token.RefreshTokenHasher;
import org.zipzip.zipzipserver.domain.user.repository.AppUserRepository;

@ActiveProfiles("dev")
@AutoConfigureMockMvc
@SpringBootTest
@Testcontainers(disabledWithoutDocker = true)
class DevelopmentAuthControllerIntegrationTest {

    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private AppUserRepository appUserRepository;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private RefreshTokenHasher refreshTokenHasher;

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @BeforeAll
    static void migrateSchema() {
        Flyway.configure()
                .cleanDisabled(false)
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .clean();
        Flyway.configure()
                .dataSource(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }

    @Test
    void 개발_프로필에서_Apple_로그인_없이_Access_및_Refresh_Token을_발급한다() throws Exception {
        String body =
                mockMvc.perform(
                                post("/api/v1/dev/auth/tokens")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "testUserKey": "ios-tester-1",
                                                  "displayName": "iOS 테스트 사용자"
                                                }
                                                """))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$.status").value(200))
                        .andExpect(jsonPath("$.code").value("AUTH_DEVELOPMENT_TOKEN_ISSUED"))
                        .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                        .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                        .andExpect(jsonPath("$.data.isNewUser").value(true))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        String accessToken = JsonPath.read(body, "$.data.accessToken");
        String refreshToken = JsonPath.read(body, "$.data.refreshToken");
        String appUserId = JsonPath.read(body, "$.data.user.id");

        assertThat(jwtTokenProvider.verifyAccessToken(accessToken).toString()).isEqualTo(appUserId);
        assertThat(jwtTokenProvider.verifyRefreshToken(refreshToken).appUserId().toString())
                .isEqualTo(appUserId);
        assertThat(appUserRepository.findByAppleSubject("development:ios-tester-1")).isPresent();
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(refreshToken)))
                .isPresent();
    }

    @Test
    void 개발용_사용자를_삭제하면_Refresh_Token을_폐기하고_탈퇴_처리한다() throws Exception {
        String tokenResponse =
                mockMvc.perform(
                                post("/api/v1/dev/auth/tokens")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {
                                                  "testUserKey": "delete-tester-1",
                                                  "displayName": "삭제 테스트 사용자"
                                                }
                                                """))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        String refreshToken = JsonPath.read(tokenResponse, "$.data.refreshToken");

        mockMvc.perform(delete("/api/v1/dev/auth/users/delete-tester-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(200))
                .andExpect(jsonPath("$.code").value("AUTH_DEVELOPMENT_USER_DELETED"));

        assertThat(appUserRepository.findByAppleSubject("development:delete-tester-1"))
                .get()
                .extracting("deleted")
                .isEqualTo(true);
        assertThat(refreshTokenRepository.findByTokenHash(refreshTokenHasher.hash(refreshToken)))
                .get()
                .extracting("revokedAt")
                .isNotNull();
    }
}
