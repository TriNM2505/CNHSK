package com.cnhsk.community.comment;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.cnhsk.community.common.CommentStatus;

/** Retains visible comments and their ancestors without changing the supplied flat order. */
public final class CommentTreeBuilder {
    private CommentTreeBuilder() { }

    public record Entry(long id, long postId, Long parentId, short depth, CommentStatus status,
            String content, Long authorId, Instant createdAt, Instant editedAt) { }

    public static List<Entry> build(List<Entry> entries) {
        Map<Long, Node> nodes = new HashMap<>();
        for (Entry entry : entries) {
            nodes.put(entry.id(), new Node(entry));
        }
        for (Node node : nodes.values()) {
            if (node.entry.parentId() != null) {
                Node parent = nodes.get(node.entry.parentId());
                if (parent == null) {
                    throw new IllegalStateException("Comment parent is missing from the flat tree.");
                }
                node.parent = parent;
                parent.remainingChildren++;
            }
        }
        ArrayDeque<Node> leaves = new ArrayDeque<>();
        nodes.values().stream().filter(node -> node.remainingChildren == 0).forEach(leaves::add);
        while (!leaves.isEmpty()) {
            Node node = leaves.remove();
            if (node.parent != null) {
                node.parent.retained |= node.retained;
                if (--node.parent.remainingChildren == 0) {
                    leaves.add(node.parent);
                }
            }
        }
        List<Entry> retained = new ArrayList<>();
        for (Entry entry : entries) {
            if (nodes.get(entry.id()).retained) {
                retained.add(entry.status() == CommentStatus.VISIBLE ? entry
                        : new Entry(entry.id(), entry.postId(), entry.parentId(), entry.depth(), entry.status(),
                                null, null, entry.createdAt(), null));
            }
        }
        return List.copyOf(retained);
    }

    private static final class Node {
        private final Entry entry;
        private Node parent;
        private int remainingChildren;
        private boolean retained;

        private Node(Entry entry) {
            this.entry = entry;
            retained = entry.status() == CommentStatus.VISIBLE;
        }
    }
}
