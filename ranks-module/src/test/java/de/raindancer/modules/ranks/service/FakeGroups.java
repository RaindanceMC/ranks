package de.raindancer.modules.ranks.service;

import de.raindancer.core.platform.network.NetworkBus;
import de.raindancer.core.platform.permission.GroupSeed;
import de.raindancer.core.platform.permission.PermissionGroups;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/** LuckPerms as far as ranks sees it: groups, their parents, who is directly in which. */
final class FakeGroups implements PermissionGroups {

    final Map<String, List<String>> parents = new HashMap<>();
    final Map<UUID, Set<String>> direct = new HashMap<>();
    boolean editable = true;

    FakeGroups group(String name, String... parentGroups) {
        parents.put(name, List.of(parentGroups));
        return this;
    }

    @Override
    public boolean editable() {
        return editable;
    }

    @Override
    public boolean seed(GroupSeed seed) {
        return false;
    }

    @Override
    public boolean exists(String group) {
        return parents.containsKey(group);
    }

    @Override
    public boolean join(UUID who, String group) {
        return exists(group) && direct.computeIfAbsent(who, any -> new LinkedHashSet<>()).add(group);
    }

    @Override
    public boolean leave(UUID who, String group) {
        return direct.getOrDefault(who, new LinkedHashSet<>()).remove(group);
    }

    @Override
    public Set<String> directGroupsOf(UUID who) {
        return Set.copyOf(direct.getOrDefault(who, Set.of()));
    }

    @Override
    public Set<String> groupsOf(UUID who) {
        Set<String> all = new LinkedHashSet<>();
        directGroupsOf(who).forEach(group -> inherit(group, all));
        return all;
    }

    private void inherit(String group, Set<String> into) {
        if (into.add(group)) {
            parents.getOrDefault(group, List.of()).forEach(parent -> inherit(parent, into));
        }
    }

    @Override
    public Set<UUID> membersOf(String group) {
        return direct.entrySet().stream().filter(entry -> entry.getValue().contains(group))
                .map(Map.Entry::getKey).collect(Collectors.toSet());
    }

    @Override
    public Set<String> dropDirect(UUID who, Collection<String> nodes) {
        return Set.of();
    }

    @Override
    public NetworkBus.Subscription onChange(Consumer<UUID> watcher) {
        return () -> { };
    }
}
