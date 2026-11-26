package com.cnhsk.learning.reference;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.CurrentUser;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.media.MediaProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReferenceCoverServiceTest {
    private static final Instant NOW = Instant.parse("2026-11-27T04:00:00Z");
    private final AuthApi auth = mock(AuthApi.class);

    private static MediaProperties media(boolean configured) {
        MediaProperties media = new MediaProperties();
        if (configured) {
            media.setCloudName("demo-cloud");
            media.setApiKey("key");
            media.setApiSecret("secret");
        }
        return media;
    }

    private ReferenceCoverService service(boolean configured, RoleCode role) {
        when(auth.currentUser()).thenReturn(new CurrentUser(7, Set.of(role), true));
        return new ReferenceCoverService(media(configured), auth, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void editorGetsASignatureForTheCoverFolderOnly() {
        var signature = service(true, RoleCode.CONTENT_EDITOR).signUpload();
        MediaProperties media = media(true);
        assertThat(signature.folder()).isEqualTo("cnhsk/reference-covers");
        assertThat(signature.timestamp()).isEqualTo(NOW.getEpochSecond());
        assertThat(signature.signature()).isEqualTo(media.signature("cnhsk/reference-covers", NOW.getEpochSecond()));
        assertThat(signature.uploadUrl()).isEqualTo(media.uploadUrl());
    }

    @Test
    void otherRolesCannotSignCoverUploads() {
        assertThatThrownBy(() -> service(true, RoleCode.LEARNER).signUpload()).isInstanceOfSatisfying(
                ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    void uploadsAreOffWithoutCloudinarySettings() {
        assertThatThrownBy(() -> service(false, RoleCode.CONTENT_EDITOR).signUpload()).isInstanceOfSatisfying(
                ApiException.class, error -> assertThat(error.getCode()).isEqualTo(ErrorCode.MEDIA_NOT_CONFIGURED));
    }
}