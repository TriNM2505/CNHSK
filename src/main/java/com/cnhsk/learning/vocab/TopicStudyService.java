package com.cnhsk.learning.vocab;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.practice.ItemType;
import com.cnhsk.learning.practice.PracticeService;
import com.cnhsk.learning.practice.PracticeService.SessionView;
import com.cnhsk.learning.practice.PracticeService.StartPracticeCommand;
import com.cnhsk.learning.practice.PracticeSource;
import com.cnhsk.learning.practice.ServedItem;
import com.cnhsk.learning.practice.Skill;
import com.cnhsk.shared.exception.ApiException;
import com.cnhsk.shared.exception.ErrorCode;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TopicStudyService {

    private final TopicStudyRepository repository;
    private final PracticeService practice;

    public TopicStudyService(TopicStudyRepository repository, PracticeService practice) {
        this.repository = repository;
        this.practice = practice;
    }

    @Transactional
    public StudyStart start(long userId, long topicId) {
        if (!repository.hasWords(topicId)) {
            throw new ApiException(ErrorCode.TOPIC_NOT_FOUND, HttpStatus.NOT_FOUND,
                    "Không tìm thấy chủ đề hoặc chủ đề chưa có từ.", Map.of());
        }
        repository.lock(userId, topicId);
        SessionView active = practice.findActive(userId, PracticeSource.TOPIC_STUDY, topicId);
        if (active != null) return new StudyStart(active, false);

        List<Lexeme> words = repository.unlearned(userId, topicId);
        if (words.isEmpty()) return new StudyStart(null, false);
        var items = new ArrayList<ServedItem>();
        for (Lexeme word : words) items.add(card(items.size() + 1, word));
        SessionView session = practice.start(new StartPracticeCommand(userId, PracticeSource.TOPIC_STUDY,
                topicId, null, items, List.of(), null, Map.of()));
        return new StudyStart(session, true);
    }

    private static ServedItem card(int no, Lexeme word) {
        List<ServedItem.Example> examples = word.getExtra().examples() == null ? List.of()
                : word.getExtra().examples().stream()
                        .map(example -> new ServedItem.Example(example.cn(), example.pinyin(), example.vi())).toList();
        var prompt = new ServedItem.Prompt(word.getText(), word.getPinyin(), word.getSinoViet(), word.getMeaningVi(),
                word.getAudioUrl(), null, null, examples, word.getKind().name());
        // FR-021's threshold is applied by the source hook only to LEARNED/KNOWN, not skip/audio failures.
        return new ServedItem(no, ItemType.STUDY_CARD, Skill.VOCAB, word.getId(), prompt,
                null, null, null, null, new ServedItem.Rules(0, 3, word.getAudioUrl() != null));
    }

    public record StudyStart(SessionView session, boolean created) {
    }
}
