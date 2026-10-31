package com.cnhsk.learning.hanzi;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.random.RandomGenerator;

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
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WritingChallengeService {
    private static final int REQUIRED_CHARACTERS = 10;

    private final CharacterQueryRepository characters;
    private final PracticeService practice;
    private final RandomGenerator random;

    public WritingChallengeService(CharacterQueryRepository characters, PracticeService practice,
            @Qualifier("practiceChoiceRandom") RandomGenerator random) {
        this.characters = characters;
        this.practice = practice;
        this.random = random;
    }

    @Transactional
    public ChallengeStart start(long userId) {
        characters.lockChallengeStart(userId);
        SessionView active = practice.findActive(userId, PracticeSource.WRITING_CHALLENGE, null);
        if (active != null) return new ChallengeStart(active, false);

        List<CharacterQueryRepository.RankedCharacter> ranked = characters.eligibleChallengeCharacters(userId);
        if (ranked.size() < REQUIRED_CHARACTERS) {
            throw new ApiException(ErrorCode.NOT_ENOUGH_CHARACTERS, HttpStatus.UNPROCESSABLE_ENTITY,
                    "Cần hoàn thành viết theo nét ít nhất 10 chữ trước khi bắt đầu thử thách.",
                    Map.of("have", ranked.size(), "need", REQUIRED_CHARACTERS));
        }

        List<Long> chosenIds = randomizedByTies(ranked).subList(0, REQUIRED_CHARACTERS).stream()
                .map(CharacterQueryRepository.RankedCharacter::lexemeId).toList();
        Map<Long, Lexeme> byId = new HashMap<>();
        characters.charactersByIds(java.util.Set.copyOf(chosenIds)).forEach(lexeme -> byId.put(lexeme.getId(), lexeme));

        List<ServedItem> items = new ArrayList<>(REQUIRED_CHARACTERS);
        for (int index = 0; index < chosenIds.size(); index++) {
            Lexeme character = byId.get(chosenIds.get(index));
            var prompt = new ServedItem.Prompt(character.getText(), character.getPinyin(), null,
                    character.getMeaningVi(), null, null, null, null, "CHARACTER",
                    character.getStrokeCount().intValue());
            items.add(new ServedItem(index + 1, ItemType.WRITING_RESULT, Skill.WRITING, character.getId(), prompt,
                    null, null, null, null, new ServedItem.Rules(0, 3,
                    character.getAudioUrl() != null && !character.getAudioUrl().isBlank())));
        }

        SessionView session = practice.start(new StartPracticeCommand(userId, PracticeSource.WRITING_CHALLENGE,
                null, null, items, List.of(), null,
                Map.of("timeLimitSeconds", 200, "maxMistakes", 3, "perCharSeconds", 20)));
        return new ChallengeStart(session, true);
    }

    private List<CharacterQueryRepository.RankedCharacter> randomizedByTies(
            List<CharacterQueryRepository.RankedCharacter> ranked) {
        List<CharacterQueryRepository.RankedCharacter> result = new ArrayList<>(ranked);
        int start = 0;
        while (start < result.size()) {
            Instant timestamp = result.get(start).lastPracticedAt();
            int end = start + 1;
            while (end < result.size() && java.util.Objects.equals(timestamp, result.get(end).lastPracticedAt())) end++;
            for (int index = end - 1; index > start; index--) {
                int swapWith = start + random.nextInt(index - start + 1);
                var value = result.set(index, result.get(swapWith));
                result.set(swapWith, value);
            }
            start = end;
        }
        return result;
    }

    public record ChallengeStart(SessionView session, boolean created) {
    }
}
