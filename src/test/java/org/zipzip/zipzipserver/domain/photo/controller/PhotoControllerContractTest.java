package org.zipzip.zipzipserver.domain.photo.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.zipzip.zipzipserver.domain.photo.code.PhotoSuccessCode;

class PhotoControllerContractTest {

    @Test
    void 사진_원본_단건_삭제_엔드포인트와_성공_코드를_제공하지_않는다() {
        Method[] methods = PhotoController.class.getDeclaredMethods();

        assertThat(methods).noneMatch(method -> method.isAnnotationPresent(DeleteMapping.class));
        assertThat(PhotoSuccessCode.values())
                .extracting(PhotoSuccessCode::getCode)
                .doesNotContain("PHOTO_DELETED");
    }
}
