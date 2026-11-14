package com.cnhsk.learning.api;

/**
 * Reports whether an exam is still required by an active business workflow.
 *
 * <p>Archiving an exam and publishing/updating a contest that references it must
 * serialize within their write transaction using the same PostgreSQL lock:
 * {@code pg_advisory_xact_lock(hashtextextended('exam:usage:' || examId, 0))}.
 * Bind {@code examId} as a SQL parameter. Hold the lock through commit/rollback.
 * Any workflow taking both this advisory lock and a row lock on
 * {@code learning.exams} MUST take the advisory lock FIRST, including exam
 * archiving and contest publishing/updating. Exam publication takes only the
 * exam row lock. Read-only usage queries do not require the advisory lock.
 */
public interface ExamUsageGuard {
    boolean isInUse(long examId);
}
