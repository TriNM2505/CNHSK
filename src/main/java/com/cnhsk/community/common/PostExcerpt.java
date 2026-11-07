package com.cnhsk.community.common;

public final class PostExcerpt {

    private PostExcerpt() {
    }

    public static String fromContent(String content) {
        int length = Math.min(200, content.codePointCount(0, content.length()));
        return content.substring(0, content.offsetByCodePoints(0, length));
    }
}
