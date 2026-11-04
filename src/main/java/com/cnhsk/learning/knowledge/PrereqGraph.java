package com.cnhsk.learning.knowledge;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/** An in-memory graph; accepted earlier file rows can be added without changing persistence. */
public final class PrereqGraph {
    public enum EdgeType { PREREQ, SUPPORT }

    public record Node(long id, String kid, int level, List<Long> prereqIds, List<Long> supportIds) {
        public Node {
            prereqIds = List.copyOf(prereqIds);
            supportIds = List.copyOf(supportIds);
        }
    }

    public record Rejection(String code, String field, String message) { }

    private final Map<String, Node> nodes = new HashMap<>();
    private final Map<Long, Set<Long>> prereqs = new HashMap<>();
    private final Map<Long, Set<Long>> supports = new HashMap<>();
    private final Map<Long, Set<Long>> dependents = new HashMap<>();

    public PrereqGraph(Collection<Node> snapshot) {
        for (Node node : snapshot) {
            nodes.put(node.kid(), node);
            prereqs.put(node.id(), new HashSet<>(node.prereqIds()));
            supports.put(node.id(), new HashSet<>(node.supportIds()));
            for (long from : node.prereqIds()) {
                dependents.computeIfAbsent(from, ignored -> new HashSet<>()).add(node.id());
            }
        }
    }

    public boolean hasNode(String kid) {
        return nodes.containsKey(kid);
    }

    public boolean contains(String from, String to, EdgeType type) {
        if (!hasNode(from) || !hasNode(to)) return false;
        return edges(type).get(nodes.get(to).id()).contains(nodes.get(from).id());
    }

    public Optional<Rejection> rejection(String from, String to, EdgeType type) {
        if (!hasNode(from)) return reject("REFERENCE_NOT_FOUND", "from_kid", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước");
        if (!hasNode(to)) return reject("REFERENCE_NOT_FOUND", "to_kid", "Nút chưa tồn tại — nhập mẫu Nút cây năng lực trước");
        if (from.equals(to)) return reject("EDGE_SELF", "to_kid", "Một nút không thể là tiên quyết/hỗ trợ của chính nó");
        EdgeType other = type == EdgeType.PREREQ ? EdgeType.SUPPORT : EdgeType.PREREQ;
        if (contains(from, to, other)) return reject("EDGE_TYPE_CONFLICT", "edge_type", "Cặp nút đã có quan hệ loại khác");
        if (contains(from, to, type) || type == EdgeType.SUPPORT) return Optional.empty();
        Node source = nodes.get(from);
        Node target = nodes.get(to);
        if (source.level() > target.level()) {
            return reject("PREREQ_LEVEL_HIGHER", "from_kid", "Nút tiên quyết không được ở cấp cao hơn nút đích");
        }
        if (reachable(target.id(), source.id())) return reject("EDGE_CYCLE", "to_kid", "Quan hệ này tạo vòng lặp tiên quyết");
        if (prereqs.get(target.id()).size() >= 4) return reject("TOO_MANY_PREREQS", "to_kid", "Một nút có tối đa 4 tiên quyết trực tiếp");
        if (reachable(source.id(), target.id())) {
            return reject("EDGE_REDUNDANT", "to_kid", "Quan hệ thừa: đã đi được từ " + from + " tới " + to + " qua nút khác");
        }
        return Optional.empty();
    }

    /** Call only after rejection is empty; invalid or duplicate rows must never reach this method. */
    public void add(String from, String to, EdgeType type) {
        long source = nodes.get(from).id();
        long target = nodes.get(to).id();
        edges(type).get(target).add(source);
        if (type == EdgeType.PREREQ) dependents.computeIfAbsent(source, ignored -> new HashSet<>()).add(target);
    }

    private Map<Long, Set<Long>> edges(EdgeType type) {
        return type == EdgeType.PREREQ ? prereqs : supports;
    }

    private boolean reachable(long from, long to) {
        Set<Long> visited = new HashSet<>();
        ArrayDeque<Long> pending = new ArrayDeque<>();
        pending.add(from);
        while (!pending.isEmpty()) {
            long current = pending.removeFirst();
            if (!visited.add(current)) continue;
            if (current == to) return true;
            pending.addAll(dependents.getOrDefault(current, Set.of()));
        }
        return false;
    }

    private static Optional<Rejection> reject(String code, String field, String message) {
        return Optional.of(new Rejection(code, field, message));
    }
}
