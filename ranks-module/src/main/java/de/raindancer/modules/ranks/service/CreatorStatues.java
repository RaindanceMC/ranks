package de.raindancer.modules.ranks.service;

import de.raindancer.core.ui.statue.StatueBoard;
import de.raindancer.core.ui.statue.StatueBoards;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * The creators' statue board for Core's statues ({@code /eco statue place creator}): everybody holding the rank
 * {@link #RANK}, in holders.yml or through its permission group, offline holders too. Asking the groups reads
 * LuckPerms' storage, so who holds it is looked up off the server thread ({@link #refresh}) and the board answers
 * from what was found last.
 */
public final class CreatorStatues {

    public static final String RANK = "creator";
    /** The creator rank's colour, as Ranks shows it. */
    private static final String MARK = "<light_purple><bold>CREATOR</bold></light_purple>";

    private final Supplier<? extends Collection<UUID>> everyHolder;
    private final AtomicReference<List<UUID>> holders = new AtomicReference<>(List.of());

    /** @param everyHolder everybody holding the rank now; may block */
    public CreatorStatues(Supplier<? extends Collection<UUID>> everyHolder) {
        this.everyHolder = everyHolder;
    }

    /** Looks again who holds the rank. Blocks: off the server thread. A look that fails keeps the last one. */
    public void refresh() {
        try {
            Collection<UUID> now = everyHolder.get();
            if (now != null) {
                holders.set(List.copyOf(now));
            }
        } catch (RuntimeException failed) {
            // LuckPerms' storage did not answer; the statues keep showing who they showed.
        }
    }

    /** @param names a player's name; somebody never on this server has none and no statue yet */
    public StatueBoard board(Function<UUID, Optional<String>> names) {
        return StatueBoard.thanks(RANK, "<light_purple><bold>Our creators</bold></light_purple>",
                "<gray>Making videos and streams about the server", MARK, "creators",
                () -> StatueBoards.named(holders.get(), names).stream()
                        .sorted(Comparator.comparing(StatueBoard.Standing::name, String.CASE_INSENSITIVE_ORDER))
                        .toList());
    }
}
