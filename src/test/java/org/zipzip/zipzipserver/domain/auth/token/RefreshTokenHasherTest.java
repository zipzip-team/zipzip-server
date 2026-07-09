package org.zipzip.zipzipserver.domain.auth.token;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class RefreshTokenHasherTest {

    private final RefreshTokenHasher hasher = new RefreshTokenHasher();

    @Test
    void 같은_Refresh_Token은_같은_해시를_반환한다() {
        String refreshToken = "refresh-token";

        String firstHash = hasher.hash(refreshToken);
        String secondHash = hasher.hash(refreshToken);

        assertThat(firstHash).isEqualTo(secondHash);
    }

    @Test
    void 다른_Refresh_Token은_다른_해시를_반환한다() {
        String firstHash = hasher.hash("refresh-token-1");
        String secondHash = hasher.hash("refresh-token-2");

        assertThat(firstHash).isNotEqualTo(secondHash);
    }

    @Test
    void 비어있는_Refresh_Token은_예외가_발생한다() {
        assertThatThrownBy(() -> hasher.hash(" ")).isInstanceOf(IllegalArgumentException.class);
    }
}
