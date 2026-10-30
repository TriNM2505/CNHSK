package com.cnhsk.learning.pronunciation;

import java.util.List;

import com.cnhsk.learning.progress.UserProgress;
import org.springframework.data.repository.Repository;

public interface PronStageProgressRepository extends Repository<UserProgress, UserProgress.Key> {

    List<UserProgress> findByUserIdAndTargetType(long userId, String targetType);
}
