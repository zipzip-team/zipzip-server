package org.zipzip.zipzipserver.domain.sharedgroup.service;

import org.springframework.stereotype.Component;
import org.zipzip.zipzipserver.domain.sharedgroup.code.SharedGroupErrorCode;
import org.zipzip.zipzipserver.global.exception.BusinessException;

@Component
public class SharedGroupNameValidator {

    private static final int MAX_NAME_LENGTH = 100;

    public String normalize(String name) {
        if (name == null) {
            throw new BusinessException(SharedGroupErrorCode.INVALID_SHARED_GROUP_NAME);
        }

        String normalizedName = name.strip();
        if (normalizedName.isBlank() || normalizedName.length() > MAX_NAME_LENGTH) {
            throw new BusinessException(SharedGroupErrorCode.INVALID_SHARED_GROUP_NAME);
        }

        return normalizedName;
    }
}
