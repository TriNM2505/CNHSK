package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.entity.Lexeme;
import com.cnhsk.learning.content.entity.LexemeKind;
import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.entity.TopicItem;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.LexemeRepository;
import com.cnhsk.learning.content.repository.TopicItemRepository;
import com.cnhsk.learning.content.repository.TopicRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TopicItemRowWriter implements RowWriter {
    private final TopicRepository topics;
    private final LexemeRepository lexemes;
    private final TopicItemRepository items;

    public TopicItemRowWriter(TopicRepository topics, LexemeRepository lexemes, TopicItemRepository items) {
        this.topics = topics;
        this.lexemes = lexemes;
        this.items = items;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.TOPIC_ITEM;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Topic topic = topics.findByCode(row.values().get("topic_code"))
                .orElseThrow(() -> new IllegalStateException("Missing topic import reference"));
        Lexeme lexeme = lexemes.findByKindAndText(LexemeKind.valueOf(row.values().get("kind")), row.values().get("text"))
                .orElseThrow(() -> new IllegalStateException("Missing lexeme import reference"));
        TopicItem existing = items.findByTopicIdAndLexemeId(topic.getId(), lexeme.getId()).orElse(null);
        TopicItem item = existing == null ? new TopicItem(topic.getId(), lexeme.getId()) : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        changes.integer(row, "order_index", item.getOrderIndex(), item::setOrderIndex);
        if (existing == null || changes.changed()) {
            items.saveAndFlush(item);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
