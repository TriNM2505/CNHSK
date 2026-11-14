package com.cnhsk.learning.api;

/** Reports whether an exam is still required by an active business workflow. */
public interface ExamUsageGuard {
    boolean isInUse(long examId);
}
