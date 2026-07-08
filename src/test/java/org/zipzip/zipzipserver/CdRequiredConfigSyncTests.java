package org.zipzip.zipzipserver;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

/**
 * CD가 서버에 전달하는 env var 목록이 코드가 요구하는 필수 설정을 빠짐없이 커버하는지 검증한다.
 *
 * <p>CI는 테스트용 더미 프로퍼티로 {@code @ConfigurationProperties} 바인딩을 통과시키기 때문에, 새 필수 설정이 GitHub
 * Secrets/{@code cd.yml}/{@code cd-dev.yml}에 반영되지 않아도 PR check는 조용히 통과할 수 있다. 이 테스트는 두 목록(코드의
 * {@code @NotBlank}/{@code @NotNull} 필드, 워크플로우의 배포 heredoc)을 각자의 실제 목적으로부터 그대로 추출해 비교하므로, 새 설정이 생겨도
 * 이 파일 자체는 손댈 필요가 없다.
 */
class CdRequiredConfigSyncTests {

    private static final String BASE_PACKAGE = "org.zipzip.zipzipserver";

    private static final List<Class<? extends Annotation>> REQUIRED_ANNOTATIONS =
            List.of(NotBlank.class, NotNull.class, NotEmpty.class);

    private static final Set<String> DEPLOY_META_KEYS = Set.of("GIT_SHA", "DOCKERHUB_IMAGE");

    private static final Pattern HEREDOC_BLOCK =
            Pattern.compile("<<ENVEOF\\R(.*?)\\R\\s*ENVEOF", Pattern.DOTALL);

    private static final Pattern ENV_KEY_LINE =
            Pattern.compile("^\\s*([A-Z_][A-Z0-9_]*)=", Pattern.MULTILINE);

    @Test
    void everyRequiredConfigPropertyIsForwardedByBothCdWorkflows() throws IOException {
        Set<String> required = requiredConfigEnvKeys();

        Set<String> missingInProd =
                missingFrom(required, forwardedEnvKeys(Path.of(".github/workflows/cd.yml")));
        Set<String> missingInDev =
                missingFrom(required, forwardedEnvKeys(Path.of(".github/workflows/cd-dev.yml")));

        assertThat(missingInProd)
                .as(
                        "cd.yml의 deploy 스텝이 서버로 전달하지 않는 필수 설정이 있습니다. heredoc에 <KEY>=$<VAR>"
                                + " 줄을 추가하세요: %s",
                        missingInProd)
                .isEmpty();
        assertThat(missingInDev)
                .as(
                        "cd-dev.yml의 deploy 스텝이 서버로 전달하지 않는 필수 설정이 있습니다. heredoc에"
                                + " <KEY>=$<VAR> 줄을 추가하세요: %s",
                        missingInDev)
                .isEmpty();
    }

    private static Set<String> requiredConfigEnvKeys() {
        ClassPathScanningCandidateComponentProvider scanner =
                new ClassPathScanningCandidateComponentProvider(false);
        scanner.addIncludeFilter(new AnnotationTypeFilter(ConfigurationProperties.class));

        Set<String> envKeys = new TreeSet<>();
        for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
            Class<?> type = loadClass(candidate.getBeanClassName());
            String prefix = configurationPropertiesPrefix(type);

            for (Field field : type.getDeclaredFields()) {
                if (isRequired(field)) {
                    String propertyKey = prefix + "." + toKebabCase(field.getName());
                    envKeys.add(toEnvVarName(propertyKey));
                }
            }
        }
        return envKeys;
    }

    private static Class<?> loadClass(String className) {
        try {
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("클래스를 로드하지 못했습니다: " + className, e);
        }
    }

    private static String configurationPropertiesPrefix(Class<?> type) {
        ConfigurationProperties annotation = type.getAnnotation(ConfigurationProperties.class);
        return !annotation.prefix().isEmpty() ? annotation.prefix() : annotation.value();
    }

    private static boolean isRequired(Field field) {
        return Arrays.stream(field.getAnnotations())
                .map(Annotation::annotationType)
                .anyMatch(REQUIRED_ANNOTATIONS::contains);
    }

    private static String toKebabCase(String camelCase) {
        return camelCase.replaceAll("([A-Z])", "-$1").toLowerCase();
    }

    private static String toEnvVarName(String propertyKey) {
        return propertyKey.toUpperCase().replaceAll("[.\\-]", "_");
    }

    private static Set<String> forwardedEnvKeys(Path workflowFile) throws IOException {
        String content = Files.readString(workflowFile);
        Matcher blockMatcher = HEREDOC_BLOCK.matcher(content);
        if (!blockMatcher.find()) {
            throw new IllegalStateException(
                    workflowFile
                            + "에서 deploy 스텝의 <<ENVEOF heredoc 블록을 찾지 못했습니다. 워크플로우 형식이"
                            + " 바뀌었다면 이 테스트의 파싱 로직도 함께 갱신하세요.");
        }

        Set<String> keys = new TreeSet<>();
        Matcher keyMatcher = ENV_KEY_LINE.matcher(blockMatcher.group(1));
        while (keyMatcher.find()) {
            String key = keyMatcher.group(1);
            if (!DEPLOY_META_KEYS.contains(key)) {
                keys.add(key);
            }
        }
        return keys;
    }

    private static Set<String> missingFrom(Set<String> required, Set<String> forwarded) {
        Set<String> missing = new TreeSet<>(required);
        missing.removeAll(forwarded);
        return missing;
    }
}
