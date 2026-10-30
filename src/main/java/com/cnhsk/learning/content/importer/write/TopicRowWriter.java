package com.cnhsk.learning.content.importer.write;

import com.cnhsk.learning.content.entity.Topic;
import com.cnhsk.learning.content.importer.ImportPreview.RowAction;
import com.cnhsk.learning.content.importer.template.ImportDataType;
import com.cnhsk.learning.content.importer.validate.RowValidationResult.ValidatedRow;
import com.cnhsk.learning.content.repository.TopicRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Component
public class TopicRowWriter implements RowWriter {
    private final TopicRepository topics;

    public TopicRowWriter(TopicRepository topics) {
        this.topics = topics;
    }

    @Override
    public ImportDataType dataType() {
        return ImportDataType.TOPIC;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public RowAction write(ValidatedRow row) {
        Topic existing = topics.findByCode(row.values().get("code")).orElse(null);
        Topic topic = existing == null ? new Topic(row.values().get("code"), row.values().get("name"),
                Short.parseShort(row.values().get("hsk_level"))) : existing;
        RowWriteSupport.Changes changes = new RowWriteSupport.Changes();
        changes.text(row, "name", topic.getName(), topic::setName);
        changes.shortValue(row, "hsk_level", topic.getHskLevel(), topic::setHskLevel);
        changes.integer(row, "order_index", topic.getOrderIndex(), topic::setOrderIndex);
        if (existing == null || changes.changed()) {
            topics.saveAndFlush(topic);
            return existing == null ? RowAction.CREATE : RowAction.UPDATE;
        }
        return RowAction.UNCHANGED;
    }
}
