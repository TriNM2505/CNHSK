package com.cnhsk.learning.reference;

import java.time.Clock;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.media.MediaProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Content editors upload a book cover from their computer straight to Cloudinary with a signature from here; the
 * picture never passes through or stays on this server, and the saved cover is only its delivery link.
 */
@Service
public class ReferenceCoverService {
    private final MediaProperties media;
    private final AuthApi auth;
    private final Clock clock;

    public ReferenceCoverService(MediaProperties media, AuthApi auth, Clock clock) {
        this.media = media;
        this.auth = auth;
        this.clock = clock;
    }

    public ReferenceCoverSignature signUpload() {
        if (!auth.currentUser().roles().contains(RoleCode.CONTENT_EDITOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        if (!media.configured()) {
            throw new ApiException(ErrorCode.MEDIA_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa bật tính năng tải ảnh lên.", Map.of());
        }
        long timestamp = clock.instant().getEpochSecond();
        String folder = media.getReferenceCoverFolder();
        return new ReferenceCoverSignature(media.getCloudName(), media.getApiKey(), folder, timestamp,
                media.signature(folder, timestamp), media.uploadUrl());
    }
}