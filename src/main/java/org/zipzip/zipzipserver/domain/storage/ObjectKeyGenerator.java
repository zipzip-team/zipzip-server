package org.zipzip.zipzipserver.domain.storage;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class ObjectKeyGenerator {

    private static final DateTimeFormatter DATE_PATH_FORMAT =
            DateTimeFormatter.ofPattern("yyyy/MM/dd", Locale.ROOT);

    public String generateOriginalKey(String contentType) {
        String extension = extensionFor(contentType);
        return "photos/%s/%s.%s"
                .formatted(LocalDate.now().format(DATE_PATH_FORMAT), UUID.randomUUID(), extension);
    }

    public String thumbnailKeyFor(String originalObjectKey) {
        int lastDot = originalObjectKey.lastIndexOf('.');
        if (lastDot < 0) {
            return originalObjectKey + "-thumb";
        }
        return originalObjectKey.substring(0, lastDot)
                + "-thumb"
                + originalObjectKey.substring(lastDot);
    }

    private String extensionFor(String contentType) {
        int slash = contentType.indexOf('/');
        String subtype = slash >= 0 ? contentType.substring(slash + 1) : contentType;
        return subtype.equalsIgnoreCase("jpeg") ? "jpg" : subtype.toLowerCase(Locale.ROOT);
    }
}
