package com.cnhsk.learning.vocab;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

final class TopicPrerequisiteGraph {
    private TopicPrerequisiteGraph() { }

    static List<Long> cycle(Map<Long, List<Long>> graph) {
        Map<Long, Integer> colors = new HashMap<>();
        Map<Long, Integer> positions = new HashMap<>();
        List<Long> path = new ArrayList<>();
        var stack = new ArrayDeque<Frame>();
        for (long root : graph.keySet().stream().sorted().toList()) {
            if (colors.containsKey(root)) continue;
            enter(root, graph, colors, positions, path, stack);
            while (!stack.isEmpty()) {
                Frame current = stack.peek();
                if (!current.edges().hasNext()) {
                    colors.put(current.id(), 2);
                    positions.remove(current.id());
                    path.remove(path.size() - 1);
                    stack.pop();
                    continue;
                }
                long next = current.edges().next();
                Integer color = colors.get(next);
                if (color != null && color == 1) {
                    var result = new ArrayList<>(path.subList(positions.get(next), path.size()));
                    result.add(next);
                    return List.copyOf(result);
                }
                if (color == null) enter(next, graph, colors, positions, path, stack);
            }
        }
        return List.of();
    }

    static boolean hasRoot(Map<Long, List<Long>> graph) {
        return graph.values().stream().anyMatch(edges -> edges.stream().noneMatch(graph::containsKey));
    }

    private static void enter(long id, Map<Long, List<Long>> graph, Map<Long, Integer> colors,
            Map<Long, Integer> positions, List<Long> path, ArrayDeque<Frame> stack) {
        colors.put(id, 1);
        positions.put(id, path.size());
        path.add(id);
        stack.push(new Frame(id, graph.get(id).stream().filter(graph::containsKey).distinct().sorted().iterator()));
    }

    private record Frame(long id, Iterator<Long> edges) { }
}
