package org.zipzip.zipzipserver.domain.sharedgroup.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

class SharedGroupNameValidatorTest {

    private final SharedGroupNameValidator validator = new SharedGroupNameValidator();

    @Test
    void 공유_그룹_이름을_trim해서_반환한다() {
        assertThat(validator.normalize(" 우리 집 ")).isEqualTo("우리 집");
    }

    @Test
    void 공유_그룹_이름이_공백이면_예외가_발생한다() {
        assertThatThrownBy(() -> validator.normalize("   "))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(SharedGroupErrorCode.INVALID_SHARED_GROUP_NAME));
    }

    @Test
    void 공유_그룹_이름이_100자를_초과하면_예외가_발생한다() {
        assertThatThrownBy(() -> validator.normalize("가".repeat(101)))
                .isInstanceOfSatisfying(
                        BusinessException.class,
                        exception ->
                                assertThat(exception.getErrorCode())
                                        .isEqualTo(SharedGroupErrorCode.INVALID_SHARED_GROUP_NAME));
    }
}
