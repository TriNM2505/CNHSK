package com.cnhsk.community.common;

public enum PostCategory {
    EXPERIENCE,
    QUESTION,
    STUDY_BUDDY,
    CULTURE,
    JOB,
    /** Only moderators post under these two: contest guides and announcements. */
    CONTEST,
    ANNOUNCEMENT;

    public boolean moderatorOnly() {
        return this == CONTEST || this == ANNOUNCEMENT;
    }
}
