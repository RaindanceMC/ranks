package de.raindancer.modules.ranks.service;

import de.raindancer.core.ui.statue.StatueBoard;
import de.raindancer.modules.ranks.model.Rank;
import net.kyori.adventure.text.Component;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * A thank-you statue board for Core's statues ({@code /eco statue place creator|team}): everybody holding one of some
 * ranks, in holders.yml or through its permission groups, offline holders too, each under the badge of their most
 * senior one. Asking the groups reads LuckPerms' storage, so who holds them is looked up off the server thread
 * ({@link #refresh}) and the board answers from what was found last.
 */
public final class RankStatues {

    public static final String CREATOR = "creator";
    public static final String TEAM = "team";

    private record Held(UUID player, Rank rank) {
    }

    private final String id;
    private final String heading;
    private final String people;
    private final String mark;
    private final Supplier<? extends Collection<Rank>> ranks;
    private final Function<Rank, ? extends Collection<UUID>> holdersOf;
    private final Function<Rank, Component> badgeOf;
    private volatile List<Held> held = List.of();

    /**
     * @param mark      the tag on a spot nobody stands on yet
     * @param ranks     which ranks the board is for
     * @param holdersOf everybody holding a rank now; may block
     * @param badgeOf   a rank as its badge, or its tag where badges do not show
     */
    public RankStatues(String id, String heading, String people, String mark, Supplier<? extends Collection<Rank>> ranks,
                       Function<Rank, ? extends Collection<UUID>> holdersOf, Function<Rank, Component> badgeOf) {
        this.id = id;
        this.heading = heading;
        this.people = people;
        this.mark = mark;
        this.ranks = ranks;
        this.holdersOf = holdersOf;
        this.badgeOf = badgeOf;
    }

    /** Looks again who holds the ranks. Blocks: off the server thread. A look that fails keeps the last one. */
    public void refresh() {
        try {
            List<Rank> senior = new ArrayList<>(ranks.get());
            senior.sort(Comparator.comparingInt(Rank::weight).reversed().thenComparing(Rank::id));
            Map<UUID, Rank> best = new LinkedHashMap<>();
            for (Rank rank : senior) {
                Collection<UUID> holders = holdersOf.apply(rank);
                if (holders != null) {
                    holders.forEach(player -> best.putIfAbsent(player, rank));
                }
            }
            held = best.entrySet().stream().map(each -> new Held(each.getKey(), each.getValue())).toList();
        } catch (RuntimeException failed) {
            // LuckPerms' storage did not answer; the statues keep showing who they showed.
        }
    }

    /** @param names a player's name; somebody never on this server has none and no statue yet */
    public StatueBoard board(Function<UUID, Optional<String>> names) {
        return StatueBoard.thanks(id, heading, "", mark, people, () -> {
            List<Held> now = held;
            List<Map.Entry<Held, StatueBoard.Standing>> named = new ArrayList<>();
            for (Held each : now) {
                names.apply(each.player()).filter(name -> !name.isBlank()).ifPresent(name -> named.add(Map.entry(each,
                        new StatueBoard.Standing(each.player(), name, null, badgeOf.apply(each.rank())))));
            }
            return named.stream()
                    .sorted(Comparator.<Map.Entry<Held, StatueBoard.Standing>>comparingInt(e -> -e.getKey().rank().weight())
                            .thenComparing(e -> e.getValue().name(), String.CASE_INSENSITIVE_ORDER))
                    .map(Map.Entry::getValue).toList();
        });
    }
}
