package org.zipzip.zipzipserver.domain.storage;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ObjectKeyGeneratorTest {

    private final ObjectKeyGenerator generator = new ObjectKeyGenerator();

    @Test
    void 원본_키는_photos_경로와_확장자를_포함한다() {
        String key = generator.generateOriginalKey("image/jpeg");

        assertThat(key).startsWith("photos/").endsWith(".jpg");
    }

    @Test
    void 서로_다른_호출은_서로_다른_키를_생성한다() {
        String first = generator.generateOriginalKey("image/jpeg");
        String second = generator.generateOriginalKey("image/jpeg");

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    void 썸네일_키는_원본_키에_thumb_접미사를_붙인다() {
        String thumbnailKey = generator.thumbnailKeyFor("photos/2026/07/08/uuid.jpg");

        assertThat(thumbnailKey).isEqualTo("photos/2026/07/08/uuid-thumb.jpg");
    }
}
