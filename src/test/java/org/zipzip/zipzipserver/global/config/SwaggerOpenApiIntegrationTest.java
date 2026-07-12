package org.zipzip.zipzipserver.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Iterator;
import java.util.Map.Entry;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class SwaggerOpenApiIntegrationTest {

    @Autowired private MockMvc mockMvc;

    @Autowired private ObjectMapper objectMapper;

    @Test
    void Swagger_UI에_전달되는_OpenAPI_문서는_오류_응답에_성공_DTO를_재사용하지_않는다() throws Exception {
        String body =
                mockMvc.perform(get("/v3/api-docs"))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        JsonNode document = objectMapper.readTree(body);

        JsonNode inviteCodeResponses =
                document.path("paths")
                        .path("/api/v1/shared-groups/{sharedGroupId}/invite-code")
                        .path("get")
                        .path("responses");
        JsonNode successExample =
                inviteCodeResponses
                        .path("200")
                        .path("content")
                        .path("application/json")
                        .path("example");
        JsonNode unauthorizedExample =
                inviteCodeResponses
                        .path("401")
                        .path("content")
                        .path("application/json")
                        .path("examples")
                        .path("UNAUTHORIZED")
                        .path("value");

        assertThat(successExample.path("status").asInt()).isEqualTo(200);
        assertThat(successExample.path("code").asText()).isEqualTo("INVITE_CODE_FOUND");
        assertThat(unauthorizedExample.path("status").asInt()).isEqualTo(401);
        assertThat(unauthorizedExample.path("code").asText()).isEqualTo("UNAUTHORIZED");
        assertThat(unauthorizedExample.path("data").isNull()).isTrue();
        assertThat(inviteCodeResponses.path("200").path("content").has("*/*")).isFalse();
        assertThat(inviteCodeResponses.path("401").path("content").has("*/*")).isFalse();
        assertThat(inviteCodeResponses.path("404").path("content").has("*/*")).isFalse();
        assertThat(
                        inviteCodeResponses
                                .path("401")
                                .path("content")
                                .path("application/json")
                                .path("schema")
                                .path("$ref")
                                .asText())
                .isEqualTo("#/components/schemas/OpenApiErrorResponse");
        assertThat(
                        document.path("paths")
                                .has("/api/v1/shared-albums/{sharedAlbumId}/photos/bulk-delete"))
                .isFalse();

        JsonNode detachOperation =
                document.path("paths")
                        .path("/api/v1/shared-albums/{sharedAlbumId}/photos/detach")
                        .path("post");
        assertThat(detachOperation.path("summary").asText()).isEqualTo("공유집(앨범)에서 사진 제거");
        assertThat(detachOperation.path("responses").has("200")).isTrue();
        assertThat(detachOperation.path("parameters"))
                .anySatisfy(
                        parameter -> {
                            assertThat(parameter.path("name").asText())
                                    .isEqualTo("Idempotency-Key");
                            assertThat(parameter.path("in").asText()).isEqualTo("header");
                            assertThat(parameter.path("required").asBoolean()).isTrue();
                        });
        assertNoDefaultStatusExample(document.path("paths"));
    }

    private void assertNoDefaultStatusExample(JsonNode node) {
        if (node.isObject()) {
            Iterator<Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Entry<String, JsonNode> field = fields.next();
                if (field.getKey().equals("status") && field.getValue().isInt()) {
                    assertThat(field.getValue().asInt()).isNotZero();
                }
                assertNoDefaultStatusExample(field.getValue());
            }
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                assertNoDefaultStatusExample(item);
            }
        }
    }
}
