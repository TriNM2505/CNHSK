package com.cnhsk.learning.content.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LexemeRepository extends JpaRepository<Lexeme, Long> {

    Optional<Lexeme> findByKindAndText(LexemeKind kind, String text);

    List<Lexeme> findByKindAndTextIn(LexemeKind kind, Collection<String> texts);
}
