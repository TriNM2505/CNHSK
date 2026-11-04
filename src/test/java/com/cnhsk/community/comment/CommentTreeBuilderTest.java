package com.cnhsk.community.comment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;

import java.time.Instant;
import java.util.List;

import com.cnhsk.community.common.CommentStatus;
import org.junit.jupiter.api.Test;

class CommentTreeBuilderTest {
    private static final Instant NOW = Instant.parse("2026-11-04T01:00:00Z");

    @Test
    void FR018_deletedParentRetainsTwoVisibleRepliesAndRedactsAllPrivateValues() {
        var root = entry(1, null, 0, CommentStatus.DELETED);
        var first = entry(2, 1L, 1, CommentStatus.VISIBLE);
        var second = entry(3, 1L, 1, CommentStatus.VISIBLE);
        var result = CommentTreeBuilder.build(List.of(root, first, second));
        assertThat(result).extracting(CommentTreeBuilder.Entry::id).containsExactly(1L, 2L, 3L);
        assertThat(result.get(0)).isEqualTo(new CommentTreeBuilder.Entry(1, 10, null, (short) 0,
                CommentStatus.DELETED, null, null, NOW, null));
        assertThat(result.subList(1, 3)).containsExactly(first, second);
        assertThat(root.content()).isEqualTo("literal <img src=x onerror=alert(1)> **đậm**");
    }

    @Test
    void FR018_retainsEveryNonvisibleAncestorOfAVisibleDescendant() {
        var result = CommentTreeBuilder.build(List.of(entry(1, null, 0, CommentStatus.DELETED),
                entry(2, 1L, 1, CommentStatus.HIDDEN), entry(3, 2L, 2, CommentStatus.VISIBLE)));
        assertThat(result).extracting(CommentTreeBuilder.Entry::id).containsExactly(1L, 2L, 3L);
        assertThat(result.subList(0, 2)).allSatisfy(node -> {
            assertThat(node.content()).isNull();
            assertThat(node.authorId()).isNull();
            assertThat(node.editedAt()).isNull();
        });
    }

    @Test
    void FR018_prunesInvisibleBranchesAndLeavesButKeepsVisibleRoots() {
        var root = entry(1, null, 0, CommentStatus.VISIBLE);
        assertThat(CommentTreeBuilder.build(List.of(root, entry(2, 1L, 1, CommentStatus.HIDDEN),
                entry(3, 2L, 2, CommentStatus.DELETED), entry(4, null, 0, CommentStatus.DELETED),
                entry(5, null, 0, CommentStatus.HIDDEN)))).containsExactly(root);
    }

    @Test
    void FR018_emptyAndCompletelyInvisibleForestsReturnEmptyLists() {
        assertThat(CommentTreeBuilder.build(List.of())).isEmpty();
        assertThat(CommentTreeBuilder.build(List.of(entry(1, null, 0, CommentStatus.HIDDEN),
                entry(2, 1L, 1, CommentStatus.DELETED)))).isEmpty();
    }

    @Test
    void FR018_preservesGlobalTimestampAndIdOrderInsteadOfDepthFirstOrder() {
        var first = entry(1, null, 0, CommentStatus.VISIBLE);
        var secondRoot = entry(2, null, 0, CommentStatus.VISIBLE);
        var reply = entry(3, 1L, 1, CommentStatus.VISIBLE);
        var later = new CommentTreeBuilder.Entry(4, 10, 2L, (short) 1, CommentStatus.VISIBLE,
                "later", 42L, NOW.plusSeconds(1), null);
        List<CommentTreeBuilder.Entry> ordered = List.of(first, secondRoot, reply, later);
        assertThat(CommentTreeBuilder.build(ordered)).containsExactlyElementsOf(ordered);
    }

    @Test
    void FR018_retentionDoesNotAssumeParentHasAnEarlierTimestamp() {
        assertThat(CommentTreeBuilder.build(List.of(entry(1, 2L, 1, CommentStatus.VISIBLE),
                entry(2, null, 0, CommentStatus.DELETED))))
                .extracting(CommentTreeBuilder.Entry::id).containsExactly(1L, 2L);
    }

    @Test
    void FR018_missingParentFailsWithoutReturningAPartialTree() {
        assertThatIllegalStateException().isThrownBy(() -> CommentTreeBuilder.build(List.of(
                entry(1, null, 0, CommentStatus.VISIBLE), entry(2, 99L, 1, CommentStatus.VISIBLE))))
                .withMessage("Comment parent is missing from the flat tree.");
    }

    private CommentTreeBuilder.Entry entry(long id, Long parent, int depth, CommentStatus status) {
        return new CommentTreeBuilder.Entry(id, 10, parent, (short) depth, status,
                "literal <img src=x onerror=alert(1)> **đậm**", 42L, NOW, NOW.plusSeconds(5));
    }
}
