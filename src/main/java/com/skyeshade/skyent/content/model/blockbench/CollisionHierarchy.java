package com.skyeshade.skyent.content.model.blockbench;

import java.util.*;

/** Keeps author intent, parent UUIDs and exclusion provenance separate from render visibility. */
public record CollisionHierarchy(Map<String, State> states, boolean authored) {
    public record State(Boolean explicit, String parent, List<String> excludedAncestors) {
        public static final State DEFAULT = new State(null, null, List.of());
        public State { excludedAncestors = List.copyOf(excludedAncestors); }
        public boolean effective() { return !Boolean.FALSE.equals(explicit) && excludedAncestors.isEmpty(); }
    }
    public CollisionHierarchy { states = Map.copyOf(states); }
    public static CollisionHierarchy of(BlockbenchScene scene) {
        Map<String, State> states = new LinkedHashMap<>();
        for (String root : scene.roots()) visit(scene,root,null,List.of(),states);
        return new CollisionHierarchy(states, states.values().stream().anyMatch(s -> s.explicit() != null));
    }
    private static void visit(BlockbenchScene scene, String id, String parent, List<String> excluded, Map<String, State> result) {
        var node = scene.nodes().get(id);
        result.put(id,new State(node.metadata().collidable(),parent,excluded));
        List<String> next = new ArrayList<>(excluded);
        if (node.type().equals("group") && node.metadata().explicitlyExcluded()) next.add(id);
        for (String child : node.children()) visit(scene,child,id,next,result);
    }
    public String audit(BlockbenchScene scene) {
        var geometry = scene.nodes().values().stream().filter(n -> !n.type().equals("group")).toList();
        long explicit = states.values().stream().filter(s -> Boolean.FALSE.equals(s.explicit())).count();
        long effective = states.values().stream().filter(s -> !s.effective()).count();
        long inherited = geometry.stream().filter(n -> !states.get(n.uuid()).excludedAncestors().isEmpty()).count();
        long remaining = geometry.stream().filter(n -> states.get(n.uuid()).effective()).count();
        return "collisionPolicy=" + (authored ? "authored" : "legacy") + " geometryNodes=" + geometry.size()
                + " explicitlyExcludedNodes=" + explicit + " effectivelyExcludedNodes=" + effective
                + " inheritedExcludedGeometry=" + inherited + " eligibleGeometry=" + remaining;
    }
}
