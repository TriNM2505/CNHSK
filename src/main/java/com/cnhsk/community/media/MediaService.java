package com.cnhsk.community.media;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.media.web.MediaItem;
import com.cnhsk.community.media.web.UploadSignature;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import com.cnhsk.shared.media.MediaProperties;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Browsers upload files straight to Cloudinary with a short-lived signature from here, so the API secret never leaves
 * the server and file bytes never pass through it. Saved posts may only point at files of this account and folder.
 */
@Service
public class MediaService {
    public static final int MAX_ITEMS = 4;

    private final MediaProperties properties;
    private final AuthApi authApi;
    private final Clock clock;

    public MediaService(MediaProperties properties, AuthApi authApi, Clock clock) {
        this.properties = properties;
        this.authApi = authApi;
        this.clock = clock;
    }

    public UploadSignature signUpload() {
        return signUpload(false);
    }

    /** Avatars go to their own sub-folder so post attachments never point at someone's profile picture. */
    public UploadSignature signUpload(boolean avatar) {
        var roles = authApi.currentUser().roles();
        if (!roles.contains(RoleCode.LEARNER) && !roles.contains(RoleCode.MODERATOR)) {
            throw new ApiException(ErrorCode.FORBIDDEN, HttpStatus.FORBIDDEN,
                    "Bạn không có quyền thực hiện thao tác này.", Map.of());
        }
        requireConfigured();
        long timestamp = clock.instant().getEpochSecond();
        String folder = avatar ? properties.getFolder() + "-avatars" : properties.getFolder();
        return new UploadSignature(properties.getCloudName(), properties.getApiKey(), folder,
                timestamp, properties.signature(folder, timestamp), properties.uploadUrl());
    }

    /** Checks attachments before they are stored with a post; {@code null} means none. */
    public List<MediaItem> validate(List<MediaItem> media) {
        if (media == null || media.isEmpty()) return List.of();
        requireConfigured();
        if (media.size() > MAX_ITEMS) throw invalid("Mỗi bài có tối đa " + MAX_ITEMS + " ảnh hoặc video.");
        String prefix = properties.deliveryPrefix();
        String folder = properties.getFolder() + "/";
        for (MediaItem item : media) {
            if (item == null || item.kind() == null || item.url() == null || item.publicId() == null
                    || !item.url().startsWith(prefix) || !item.publicId().startsWith(folder)
                    || item.publicId().length() > 255 || item.url().length() > 2_000) {
                throw invalid("Tệp đính kèm không hợp lệ, hãy tải lên lại.");
            }
        }
        return List.copyOf(media);
    }

    private void requireConfigured() {
        if (!properties.configured()) {
            throw new ApiException(ErrorCode.MEDIA_NOT_CONFIGURED, HttpStatus.SERVICE_UNAVAILABLE,
                    "Chưa bật tính năng đính kèm ảnh, video.", Map.of());
        }
    }

    private static ApiException invalid(String message) {
        return new ApiException(ErrorCode.VALIDATION_FAILED, HttpStatus.UNPROCESSABLE_ENTITY, "Dữ liệu không hợp lệ.",
                Map.of("details", List.of(Map.of("field", "media", "message", message))));
    }
}
