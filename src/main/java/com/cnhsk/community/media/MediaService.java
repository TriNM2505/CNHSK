package com.cnhsk.community.media;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import com.cnhsk.auth.api.AuthApi;
import com.cnhsk.auth.api.RoleCode;
import com.cnhsk.community.media.web.MediaItem;
import com.cnhsk.community.media.web.UploadSignature;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
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
        // Cloudinary signs the alphabetically sorted parameters joined with '&', followed by the secret.
        String folder = avatar ? properties.getFolder() + "-avatars" : properties.getFolder();
        String toSign = "folder=" + folder + "&timestamp=" + timestamp;
        return new UploadSignature(properties.getCloudName(), properties.getApiKey(), folder,
                timestamp, sha1Hex(toSign + properties.getApiSecret()),
                "https://api.cloudinary.com/v1_1/" + properties.getCloudName() + "/auto/upload");
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

    static String sha1Hex(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
