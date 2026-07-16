package org.zipzip.zipzipserver.global.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.responses.ApiResponse;
import io.swagger.v3.oas.models.responses.ApiResponses;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.web.method.HandlerMethod;
import org.zipzip.zipzipserver.domain.album.controller.SharedAlbumController;
import org.zipzip.zipzipserver.domain.album.controller.SharedGroupAlbumController;
import org.zipzip.zipzipserver.domain.auth.controller.AuthController;
import org.zipzip.zipzipserver.domain.chat.controller.ChatController;
import org.zipzip.zipzipserver.domain.photo.controller.PhotoController;
import org.zipzip.zipzipserver.domain.photo.controller.SharedAlbumPhotoController;
import org.zipzip.zipzipserver.domain.reaction.controller.ReactionController;
import org.zipzip.zipzipserver.domain.sharedgroup.controller.SharedGroupController;
import org.zipzip.zipzipserver.domain.sharedgroup.controller.SharedGroupInviteController;
import org.zipzip.zipzipserver.domain.sharedgroup.controller.SharedGroupMemberController;
import org.zipzip.zipzipserver.domain.user.controller.UserProfileController;

class SwaggerResponseExampleConfigTest {

    private final OperationCustomizer customizer =
            new SwaggerResponseExampleConfig().swaggerResponseExampleCustomizer();

    @Test
    void 모든_컨트롤러_응답에는_명시적_Swagger_예시가_생성된다() {
        controllers().forEach(this::assertResponseExamples);
    }

    @Test
    void 깊이_4단계_이상_중첩된_필드도_빈_배열이_아닌_예시_값을_채운다() throws Exception {
        Method method =
                SharedGroupAlbumController.class.getDeclaredMethod(
                        "listAlbums", UUID.class, UUID.class, String.class, Integer.class);
        io.swagger.v3.oas.annotations.responses.ApiResponses annotation =
                method.getAnnotation(io.swagger.v3.oas.annotations.responses.ApiResponses.class);
        Operation operation = new Operation().responses(new ApiResponses());
        for (io.swagger.v3.oas.annotations.responses.ApiResponse response : annotation.value()) {
            operation
                    .getResponses()
                    .addApiResponse(
                            response.responseCode(),
                            new ApiResponse().description(response.description()));
        }
        customizer.customize(
                operation, new HandlerMethod(SharedGroupAlbumController.class, method));

        @SuppressWarnings("unchecked")
        Map<String, Object> example =
                (Map<String, Object>)
                        operation
                                .getResponses()
                                .get("200")
                                .getContent()
                                .get("application/json")
                                .getExample();
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) example.get("data");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) data.get("items");

        assertThat(items.get(0).get("thumbnails"))
                .as("SharedAlbumListResponse.Item.thumbnails 예시(depth 4)")
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .as("thumbnails가 depth cap에 걸려 빈 배열로 잘리면 안 됨")
                .isNotEmpty();
    }

    private void assertResponseExamples(Class<?> controllerType) {
        for (Method method : controllerType.getDeclaredMethods()) {
            io.swagger.v3.oas.annotations.responses.ApiResponses annotation =
                    method.getAnnotation(
                            io.swagger.v3.oas.annotations.responses.ApiResponses.class);
            if (annotation == null) {
                continue;
            }

            Operation operation = new Operation().responses(new ApiResponses());
            for (io.swagger.v3.oas.annotations.responses.ApiResponse response :
                    annotation.value()) {
                operation
                        .getResponses()
                        .addApiResponse(
                                response.responseCode(),
                                new ApiResponse().description(response.description()));
            }
            customizer.customize(operation, new HandlerMethod(controllerType, method));

            operation
                    .getResponses()
                    .forEach(
                            (status, response) -> {
                                assertThat(response.getContent())
                                        .as("%s %s 응답 콘텐츠", method.getName(), status)
                                        .isNotNull();
                                MediaType mediaType = response.getContent().get("application/json");
                                assertThat(mediaType)
                                        .as("%s %s", method.getName(), status)
                                        .isNotNull();
                                if (Integer.parseInt(status) < 400) {
                                    assertThat(mediaType.getExample())
                                            .as("%s %s 성공 예시", method.getName(), status)
                                            .isInstanceOfSatisfying(
                                                    java.util.Map.class,
                                                    example ->
                                                            assertThat(example.get("status"))
                                                                    .isNotEqualTo(0));
                                } else {
                                    assertThat(mediaType.getExamples())
                                            .as("%s %s 오류 예시", method.getName(), status)
                                            .isNotEmpty();
                                    assertThat(mediaType.getSchema())
                                            .as("%s %s 오류 스키마", method.getName(), status)
                                            .isNotNull();
                                    assertThat(String.valueOf(mediaType.getSchema().get$ref()))
                                            .as("%s %s 오류 스키마 참조", method.getName(), status)
                                            .doesNotContain("BaseResponse");
                                }
                            });
        }
    }

    private List<Class<?>> controllers() {
        return List.of(
                SharedAlbumController.class,
                SharedGroupAlbumController.class,
                AuthController.class,
                ChatController.class,
                PhotoController.class,
                SharedAlbumPhotoController.class,
                ReactionController.class,
                SharedGroupController.class,
                SharedGroupInviteController.class,
                SharedGroupMemberController.class,
                UserProfileController.class);
    }
}
