package org.zipzip.zipzipserver.global.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.examples.Example;
import io.swagger.v3.oas.models.media.ComposedSchema;
import io.swagger.v3.oas.models.media.Content;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.MapSchema;
import io.swagger.v3.oas.models.media.MediaType;
import io.swagger.v3.oas.models.media.Schema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.responses.ApiResponse;
import java.lang.reflect.Method;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.Type;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.ResponseEntity;
import org.springframework.web.method.HandlerMethod;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumErrorCode;
import org.zipzip.zipzipserver.domain.album.code.SharedAlbumSuccessCode;
import org.zipzip.zipzipserver.domain.auth.code.AuthErrorCode;
import org.zipzip.zipzipserver.domain.auth.code.AuthSuccessCode;
import org.zipzip.zipzipserver.domain.chat.code.ChatErrorCode;
import org.zipzip.zipzipserver.domain.chat.code.ChatSuccessCode;
import org.zipzip.zipzipserver.domain.photo.code.PhotoErrorCode;
import org.zipzip.zipzipserver.domain.photo.code.PhotoSuccessCode;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionErrorCode;
import org.zipzip.zipzipserver.domain.reaction.code.ReactionSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupSuccessCode;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.CreateSharedGroupResponse;
import org.zipzip.zipzipserver.domain.sharedgroup.dto.response.SharedGroupJoinResponse;
import org.zipzip.zipzipserver.domain.user.code.UserErrorCode;
import org.zipzip.zipzipserver.domain.user.code.UserSuccessCode;
import org.zipzip.zipzipserver.global.code.ErrorCode;
import org.zipzip.zipzipserver.global.code.GlobalErrorCode;
import org.zipzip.zipzipserver.global.code.SuccessCode;
import org.zipzip.zipzipserver.global.response.BaseResponse;

@Configuration
public class SwaggerResponseExampleConfig {

    private static final String APPLICATION_JSON = "application/json";
    private static final String ERROR_RESPONSE_SCHEMA = "#/components/schemas/OpenApiErrorResponse";
    private static final String VALIDATION_ERROR_RESPONSE_SCHEMA =
            "#/components/schemas/OpenApiValidationErrorResponse";
    private static final Pattern ERROR_CODE_PATTERN = Pattern.compile("[A-Z][A-Z0-9_]+");
    private static final Map<String, SuccessCode> SUCCESS_CODES = successCodes();
    private static final Map<String, ErrorCode> ERROR_CODES = errorCodes();
    private static final Map<String, List<String>> FALLBACK_ERROR_CODES = fallbackErrorCodes();
    private static final Map<String, Type> RESPONSE_DATA_TYPES =
            Map.of(
                    "SharedGroupController#createSharedGroup", CreateSharedGroupResponse.class,
                    "SharedGroupInviteController#join", SharedGroupJoinResponse.class);

    @Bean
    public OpenApiCustomizer openApiResponseSchemasCustomizer() {
        return openAPI -> {
            Components components = openAPI.getComponents();
            if (components == null) {
                components = new Components();
                openAPI.setComponents(components);
            }
            components.addSchemas("OpenApiErrorResponse", errorResponseSchema());
            components.addSchemas(
                    "OpenApiValidationErrorResponse", validationErrorResponseSchema());
        };
    }

    @Bean
    public OperationCustomizer swaggerResponseExampleCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getResponses() == null) {
                return operation;
            }

            String methodKey = methodKey(handlerMethod);
            operation
                    .getResponses()
                    .forEach(
                            (responseCode, response) -> {
                                if (isSuccess(responseCode)) {
                                    applySuccessExample(
                                            response, handlerMethod, methodKey, responseCode);
                                } else {
                                    applyErrorExamples(
                                            response, handlerMethod, methodKey, responseCode);
                                }
                            });
            return operation;
        };
    }

    private static void applySuccessExample(
            ApiResponse response,
            HandlerMethod handlerMethod,
            String methodKey,
            String responseCode) {
        SuccessCode successCode = SUCCESS_CODES.get(methodKey);
        if (successCode == null) {
            return;
        }

        MediaType mediaType = jsonMediaType(response);
        mediaType.setExample(
                envelope(
                        successCode.getHttpStatus().value(),
                        successCode.getCode(),
                        successCode.getMessage(),
                        sampleData(handlerMethod, methodKey)));
        if (response.getDescription() == null || response.getDescription().isBlank()) {
            response.setDescription(successCode.getCode());
        }
    }

    private static void applyErrorExamples(
            ApiResponse response,
            HandlerMethod handlerMethod,
            String methodKey,
            String responseCode) {
        MediaType mediaType = jsonMediaType(response);
        mediaType.setSchema(errorSchema(responseCode));

        Map<String, Example> examples = new LinkedHashMap<>();
        documentedErrorCodes(response.getDescription(), methodKey, responseCode)
                .forEach(
                        code -> {
                            ErrorCode errorCode = ERROR_CODES.get(code);
                            if (errorCode != null) {
                                examples.put(code, new Example().value(errorEnvelope(errorCode)));
                            }
                        });
        if ("400".equals(responseCode) && hasValidationInput(handlerMethod.getMethod())) {
            examples.put("INVALID_REQUEST", new Example().value(validationErrorEnvelope()));
        }
        if (examples.isEmpty()) {
            ErrorCode fallback =
                    "401".equals(responseCode)
                            ? GlobalErrorCode.UNAUTHORIZED
                            : GlobalErrorCode.INVALID_REQUEST;
            examples.put(fallback.getCode(), new Example().value(errorEnvelope(fallback)));
        }
        mediaType.setExamples(examples);
    }

    private static MediaType jsonMediaType(ApiResponse response) {
        Content content = response.getContent();
        if (content == null) {
            content = new Content();
            response.setContent(content);
        }
        MediaType mediaType = content.get(APPLICATION_JSON);
        if (mediaType == null) {
            mediaType = content.remove("*/*");
            if (mediaType == null) {
                mediaType = new MediaType();
            }
            content.addMediaType(APPLICATION_JSON, mediaType);
        }
        return mediaType;
    }

    private static Schema<?> errorSchema(String responseCode) {
        if (!"400".equals(responseCode)) {
            return reference(ERROR_RESPONSE_SCHEMA);
        }
        return new ComposedSchema()
                .addOneOfItem(reference(ERROR_RESPONSE_SCHEMA))
                .addOneOfItem(reference(VALIDATION_ERROR_RESPONSE_SCHEMA));
    }

    private static Schema<?> reference(String reference) {
        return new Schema<>().$ref(reference);
    }

    private static List<String> documentedErrorCodes(
            String description, String methodKey, String responseCode) {
        Set<String> codes = new LinkedHashSet<>();
        if (description != null) {
            Matcher matcher = ERROR_CODE_PATTERN.matcher(description);
            while (matcher.find()) {
                String code = matcher.group();
                if (ERROR_CODES.containsKey(code)) {
                    codes.add(code);
                }
            }
        }
        if (codes.isEmpty()) {
            codes.addAll(
                    FALLBACK_ERROR_CODES.getOrDefault(methodKey + "#" + responseCode, List.of()));
        }
        if (codes.isEmpty() && "401".equals(responseCode)) {
            codes.add(GlobalErrorCode.UNAUTHORIZED.getCode());
        }
        return List.copyOf(codes);
    }

    private static boolean hasValidationInput(Method method) {
        return Arrays.stream(method.getParameters())
                .anyMatch(
                        parameter ->
                                Arrays.stream(parameter.getAnnotations())
                                                .anyMatch(
                                                        annotation ->
                                                                annotation
                                                                        .annotationType()
                                                                        .getName()
                                                                        .equals(
                                                                                "jakarta.validation.Valid"))
                                        || Arrays.stream(parameter.getAnnotations())
                                                .anyMatch(
                                                        annotation ->
                                                                annotation
                                                                        .annotationType()
                                                                        .getPackageName()
                                                                        .equals(
                                                                                "jakarta.validation.constraints")));
    }

    private static Map<String, Object> envelope(
            int status, String code, String message, Object data) {
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("status", status);
        response.put("code", code);
        response.put("message", message);
        response.put("data", data);
        return response;
    }

    private static Map<String, Object> errorEnvelope(ErrorCode errorCode) {
        return envelope(
                errorCode.getHttpStatus().value(),
                errorCode.getCode(),
                errorCode.getMessage(),
                null);
    }

    private static Map<String, Object> validationErrorEnvelope() {
        return envelope(
                400,
                GlobalErrorCode.INVALID_REQUEST.getCode(),
                GlobalErrorCode.INVALID_REQUEST.getMessage(),
                Map.of("field", List.of("값이 올바르지 않습니다.")));
    }

    private static Object sampleData(HandlerMethod handlerMethod, String methodKey) {
        Type dataType =
                RESPONSE_DATA_TYPES.getOrDefault(
                        methodKey,
                        responseDataType(handlerMethod.getMethod().getGenericReturnType()));
        return sampleValue(dataType, 0);
    }

    private static Type responseDataType(Type returnType) {
        if (!(returnType instanceof ParameterizedType parameterizedType)) {
            return Void.class;
        }
        if (parameterizedType.getRawType().equals(ResponseEntity.class)) {
            return responseDataType(parameterizedType.getActualTypeArguments()[0]);
        }
        if (parameterizedType.getRawType().equals(BaseResponse.class)) {
            return parameterizedType.getActualTypeArguments()[0];
        }
        return Void.class;
    }

    private static Object sampleValue(Type type, int depth) {
        if (depth > 3 || type == Void.class) {
            return null;
        }
        if (type instanceof ParameterizedType parameterizedType) {
            Type rawType = parameterizedType.getRawType();
            if (rawType instanceof Class<?> rawClass
                    && Collection.class.isAssignableFrom(rawClass)) {
                return List.of(
                        sampleValue(parameterizedType.getActualTypeArguments()[0], depth + 1));
            }
            if (rawType instanceof Class<?> rawClass && Map.class.isAssignableFrom(rawClass)) {
                return Map.of(
                        "key",
                        sampleValue(parameterizedType.getActualTypeArguments()[1], depth + 1));
            }
            return sampleValue(rawType, depth + 1);
        }
        if (!(type instanceof Class<?> typeClass)) {
            return Map.of();
        }
        if (typeClass.equals(String.class)) {
            return "예시 문자열";
        }
        if (typeClass.equals(UUID.class)) {
            return "11111111-1111-1111-1111-111111111111";
        }
        if (typeClass.equals(Instant.class)) {
            return "2026-07-11T00:00:00Z";
        }
        if (typeClass.equals(Boolean.class) || typeClass.equals(boolean.class)) {
            return true;
        }
        if (Number.class.isAssignableFrom(typeClass)
                || typeClass.equals(int.class)
                || typeClass.equals(long.class)
                || typeClass.equals(double.class)) {
            return 1;
        }
        if (typeClass.isEnum()) {
            return ((Enum<?>) typeClass.getEnumConstants()[0]).name();
        }
        if (typeClass.isRecord()) {
            Map<String, Object> data = new LinkedHashMap<>();
            for (RecordComponent component : typeClass.getRecordComponents()) {
                data.put(component.getName(), sampleValue(component.getGenericType(), depth + 1));
            }
            return data;
        }
        return Map.of();
    }

    private static Schema<?> errorResponseSchema() {
        return new Schema<>()
                .type("object")
                .addProperty("status", new IntegerSchema().example(401))
                .addProperty("code", new StringSchema().example("UNAUTHORIZED"))
                .addProperty("message", new StringSchema().example("인증이 필요합니다."))
                .addProperty("data", new Schema<>().nullable(true));
    }

    private static Schema<?> validationErrorResponseSchema() {
        return new Schema<>()
                .type("object")
                .addProperty("status", new IntegerSchema().example(400))
                .addProperty("code", new StringSchema().example("INVALID_REQUEST"))
                .addProperty("message", new StringSchema().example("잘못된 요청입니다."))
                .addProperty(
                        "data",
                        new MapSchema()
                                .additionalProperties(
                                        new io.swagger.v3.oas.models.media.ArraySchema()
                                                .items(new StringSchema())));
    }

    private static String methodKey(HandlerMethod handlerMethod) {
        return handlerMethod.getMethod().getDeclaringClass().getSimpleName()
                + "#"
                + handlerMethod.getMethod().getName();
    }

    private static boolean isSuccess(String responseCode) {
        try {
            return Integer.parseInt(responseCode) < 400;
        } catch (NumberFormatException ignored) {
            return false;
        }
    }

    private static Map<String, SuccessCode> successCodes() {
        return Map.ofEntries(
                Map.entry(
                        "SharedAlbumController#getAlbum",
                        SharedAlbumSuccessCode.SHARED_ALBUM_FOUND),
                Map.entry(
                        "SharedAlbumController#renameAlbum",
                        SharedAlbumSuccessCode.SHARED_ALBUM_UPDATED),
                Map.entry(
                        "SharedAlbumController#deleteAlbum",
                        SharedAlbumSuccessCode.SHARED_ALBUM_DELETED),
                Map.entry(
                        "SharedGroupAlbumController#listAlbums",
                        SharedAlbumSuccessCode.SHARED_ALBUM_LIST_FOUND),
                Map.entry(
                        "SharedGroupAlbumController#createAlbum",
                        SharedAlbumSuccessCode.SHARED_ALBUM_CREATED),
                Map.entry("AuthController#loginWithApple", AuthSuccessCode.AUTH_LOGIN_SUCCESS),
                Map.entry("AuthController#logout", AuthSuccessCode.AUTH_LOGOUT_SUCCESS),
                Map.entry("AuthController#refreshTokens", AuthSuccessCode.AUTH_TOKEN_REFRESHED),
                Map.entry(
                        "ChatController#getTimeline",
                        ChatSuccessCode.SHARED_GROUP_CHAT_TIMELINE_FOUND),
                Map.entry(
                        "ChatController#createMessage",
                        ChatSuccessCode.SHARED_GROUP_CHAT_MESSAGE_CREATED),
                Map.entry("PhotoController#updateMetadata", PhotoSuccessCode.PHOTO_UPDATED),
                Map.entry("PhotoController#deletePhoto", PhotoSuccessCode.PHOTO_DELETED),
                Map.entry(
                        "SharedAlbumPhotoController#listPhotos", PhotoSuccessCode.PHOTO_LIST_FOUND),
                Map.entry(
                        "SharedAlbumPhotoController#issueUploadUrls",
                        PhotoSuccessCode.PHOTO_UPLOAD_URLS_ISSUED),
                Map.entry(
                        "SharedAlbumPhotoController#completeUpload",
                        PhotoSuccessCode.PHOTOS_CREATED),
                Map.entry("SharedAlbumPhotoController#bulkDelete", PhotoSuccessCode.PHOTOS_DELETED),
                Map.entry(
                        "SharedAlbumPhotoController#attachPhotos",
                        PhotoSuccessCode.PHOTOS_ATTACHED),
                Map.entry(
                        "SharedAlbumPhotoController#detachPhotos",
                        PhotoSuccessCode.PHOTOS_DETACHED),
                Map.entry("ReactionController#getPhotoDetail", ReactionSuccessCode.PHOTO_FOUND),
                Map.entry("ReactionController#likePhoto", ReactionSuccessCode.PHOTO_LIKED),
                Map.entry("ReactionController#unlikePhoto", ReactionSuccessCode.PHOTO_UNLIKED),
                Map.entry(
                        "ReactionController#listComments",
                        ReactionSuccessCode.PHOTO_COMMENT_LIST_FOUND),
                Map.entry(
                        "ReactionController#createComment",
                        ReactionSuccessCode.PHOTO_COMMENT_CREATED),
                Map.entry(
                        "SharedGroupController#findMySharedGroups",
                        SharedGroupSuccessCode.SHARED_GROUP_LIST_FOUND),
                Map.entry(
                        "SharedGroupController#createSharedGroup",
                        SharedGroupSuccessCode.SHARED_GROUP_CREATED),
                Map.entry(
                        "SharedGroupController#findSharedGroup",
                        SharedGroupSuccessCode.SHARED_GROUP_FOUND),
                Map.entry(
                        "SharedGroupController#updateName",
                        SharedGroupSuccessCode.SHARED_GROUP_UPDATED),
                Map.entry(
                        "SharedGroupController#delete",
                        SharedGroupSuccessCode.SHARED_GROUP_DELETED),
                Map.entry(
                        "SharedGroupInviteController#findInviteCode",
                        SharedGroupSuccessCode.INVITE_CODE_FOUND),
                Map.entry(
                        "SharedGroupInviteController#join",
                        SharedGroupSuccessCode.SHARED_GROUP_JOINED),
                Map.entry(
                        "SharedGroupInviteController#leave",
                        SharedGroupSuccessCode.SHARED_GROUP_LEFT),
                Map.entry(
                        "SharedGroupMemberController#findMembers",
                        SharedGroupSuccessCode.SHARED_GROUP_MEMBER_LIST_FOUND),
                Map.entry("UserProfileController#getMyProfile", UserSuccessCode.USER_PROFILE_FOUND),
                Map.entry(
                        "UserProfileController#updateMyProfile",
                        UserSuccessCode.USER_PROFILE_UPDATED),
                Map.entry("UserProfileController#withdraw", UserSuccessCode.USER_WITHDRAWN));
    }

    private static Map<String, ErrorCode> errorCodes() {
        Map<String, ErrorCode> codes = new LinkedHashMap<>();
        register(codes, GlobalErrorCode.values());
        register(codes, AuthErrorCode.values());
        register(codes, SharedAlbumErrorCode.values());
        register(codes, ChatErrorCode.values());
        register(codes, PhotoErrorCode.values());
        register(codes, ReactionErrorCode.values());
        register(codes, SharedGroupErrorCode.values());
        register(codes, UserErrorCode.values());
        return Map.copyOf(codes);
    }

    private static void register(Map<String, ErrorCode> target, ErrorCode[] errorCodes) {
        for (ErrorCode errorCode : errorCodes) {
            target.putIfAbsent(errorCode.getCode(), errorCode);
        }
    }

    private static Map<String, List<String>> fallbackErrorCodes() {
        return Map.ofEntries(
                Map.entry("AuthController#logout#400", List.of("INVALID_REQUEST")),
                Map.entry(
                        "AuthController#logout#401",
                        List.of("UNAUTHORIZED", "INVALID_REFRESH_TOKEN")),
                Map.entry(
                        "SharedGroupController#findMySharedGroups#400", List.of("INVALID_CURSOR")),
                Map.entry("SharedGroupController#findMySharedGroups#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupController#createSharedGroup#400", List.of("INVALID_REQUEST")),
                Map.entry("SharedGroupController#createSharedGroup#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupController#createSharedGroup#409",
                        List.of("IDEMPOTENCY_KEY_REUSED", "IDEMPOTENCY_REQUEST_IN_PROGRESS")),
                Map.entry("SharedGroupController#findSharedGroup#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupController#findSharedGroup#404",
                        List.of("SHARED_GROUP_NOT_FOUND")),
                Map.entry(
                        "SharedGroupController#updateName#400",
                        List.of("INVALID_SHARED_GROUP_NAME")),
                Map.entry("SharedGroupController#updateName#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupController#updateName#403",
                        List.of("ONLY_HOST_CAN_UPDATE_SHARED_GROUP")),
                Map.entry(
                        "SharedGroupController#updateName#404", List.of("SHARED_GROUP_NOT_FOUND")),
                Map.entry("SharedGroupController#delete#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupController#delete#403",
                        List.of("ONLY_HOST_CAN_DELETE_SHARED_GROUP")),
                Map.entry("SharedGroupController#delete#404", List.of("SHARED_GROUP_NOT_FOUND")),
                Map.entry(
                        "SharedGroupInviteController#findInviteCode#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupInviteController#findInviteCode#404",
                        List.of("SHARED_GROUP_NOT_FOUND")),
                Map.entry(
                        "SharedGroupInviteController#join#400",
                        List.of("INVALID_REQUEST", "INVALID_INVITE_CODE")),
                Map.entry("SharedGroupInviteController#join#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupInviteController#join#409",
                        List.of(
                                "ALREADY_JOINED_SHARED_GROUP",
                                "IDEMPOTENCY_KEY_REUSED",
                                "IDEMPOTENCY_REQUEST_IN_PROGRESS")),
                Map.entry("SharedGroupInviteController#leave#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupInviteController#leave#403",
                        List.of("HOST_CANNOT_LEAVE_SHARED_GROUP")),
                Map.entry(
                        "SharedGroupInviteController#leave#404", List.of("SHARED_GROUP_NOT_FOUND")),
                Map.entry("SharedGroupMemberController#findMembers#400", List.of("INVALID_CURSOR")),
                Map.entry("SharedGroupMemberController#findMembers#401", List.of("UNAUTHORIZED")),
                Map.entry(
                        "SharedGroupMemberController#findMembers#404",
                        List.of("SHARED_GROUP_NOT_FOUND")));
    }
}
