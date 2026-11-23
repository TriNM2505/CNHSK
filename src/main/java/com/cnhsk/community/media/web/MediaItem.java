package com.cnhsk.community.media.web;

import io.swagger.v3.oas.annotations.media.Schema;

/** One image or video attached to a post, as returned by Cloudinary after upload. */
@Schema(name = "MediaItem", requiredProperties = {"kind", "url", "publicId"})
public record MediaItem(MediaKind kind, String url, String publicId) {
    public enum MediaKind { IMAGE, VIDEO }
}
