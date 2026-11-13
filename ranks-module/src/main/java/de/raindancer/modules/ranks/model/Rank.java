package de.raindancer.modules.ranks.model;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

/**
 * One rank from ranks.yml.
 *
 * @param tag    the owner's own markup, drawn in front of the name — {@code <red><b>[OWNER]</b></red>}
 * @param weight higher is more senior: shown first when somebody holds several, and higher in the tablist
 * @param team   whether holding it puts somebody in the server's team — listed on its own in the tablist
 * @param groups the permission (LuckPerms) groups that give it; none named means the group of the rank's own id
 * @param styles other tags a holder may pick instead of {@code tag}, by id ({@code rose-gold}) — Prime's colourways
 */
public record Rank(String id, String title, String tag, int weight, List<String> description, boolean team,
                   List<String> groups, java.util.Map<String, String> styles) {

    public Rank {
        description = description == null ? List.of() : List.copyOf(description);
        LinkedHashSet<String> named = new LinkedHashSet<>();
        if (groups != null) {
            groups.stream().filter(group -> group != null && !group.isBlank())
                    .map(group -> group.strip().toLowerCase(Locale.ROOT)).forEach(named::add);
        }
        groups = named.isEmpty() && id != null ? List.of(id) : List.copyOf(named);
        styles = styles == null ? java.util.Map.of()
                : java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(styles));
    }

    public Rank(String id, String title, String tag, int weight, List<String> description, boolean team,
                List<String> groups) {
        this(id, title, tag, weight, description, team, groups, java.util.Map.of());
    }

    /** The rank as it shows with the holder's pick; an unknown or empty pick is its own tag. */
    public Rank styled(String style) {
        String picked = style == null ? null : styles.get(style);
        return picked == null ? this : new Rank(id, title, picked, weight, description, team, groups, styles);
    }

    /** {@code rose-gold} → {@code Rose gold}. */
    public static String styleTitle(String style) {
        String spaced = style.replace('-', ' ').replace('_', ' ');
        return spaced.isEmpty() ? spaced : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    public Rank(String id, String title, String tag, int weight, List<String> description, boolean team) {
        this(id, title, tag, weight, description, team, List.of(), java.util.Map.of());
    }

    /** Not part of the team. */
    public Rank(String id, String title, String tag, int weight, List<String> description) {
        this(id, title, tag, weight, description, false);
    }

    /** Whether its groups are just the one named like it — nothing for a file to write down. */
    public boolean ownGroupOnly() {
        return groups.equals(List.of(id));
    }

    public Rank withTag(String newTag) {
        return new Rank(id, title, newTag, weight, description, team, groups, styles);
    }

    public Rank withTitle(String newTitle) {
        return new Rank(id, newTitle, tag, weight, description, team, groups, styles);
    }

    public Rank withWeight(int newWeight) {
        return new Rank(id, title, tag, newWeight, description, team, groups, styles);
    }

    public Rank withTeam(boolean onTeam) {
        return new Rank(id, title, tag, weight, description, onTeam, groups, styles);
    }

    /** Empty goes back to the group of its own id. */
    public Rank withGroups(List<String> newGroups) {
        return new Rank(id, title, tag, weight, description, team, newGroups, styles);
    }
}
