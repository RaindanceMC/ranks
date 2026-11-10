package de.raindancer.modules.ranks.service;

import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.store.RankCatalogue;
import de.raindancer.modules.ranks.store.WelcomedBook;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * A rank's {@code welcome:} lines — what it gives — told to each holder once: the moment they get it while online,
 * or the first time they join holding it. Blocks on welcomed.yml (and LuckPerms for an offline player): never on
 * the server thread.
 */
public final class RankWelcome {

    private final RankService ranks;
    private final RankCatalogue catalogue;
    private final WelcomedBook book;

    public RankWelcome(RankService ranks, RankCatalogue catalogue, WelcomedBook book) {
        this.ranks = ranks;
        this.catalogue = catalogue;
        this.book = book;
    }

    /** The ranks whose welcome is owed now, marked as told; empty when none is or the file cannot be written. */
    public synchronized List<Rank> takeDue(UUID player) {
        List<Rank> withText = ranks.held(player).stream().filter(rank -> !catalogue.welcome(rank.id()).isEmpty()).toList();
        if (withText.isEmpty()) {
            return List.of();
        }
        Set<String> told = book.told(player);
        if (told == null) {
            return List.of();
        }
        List<Rank> due = withText.stream().filter(rank -> !told.contains(rank.id())).toList();
        if (due.isEmpty() || !book.mark(player, due.stream().map(Rank::id).toList())) {
            return List.of();
        }
        return due;
    }

    public List<String> lines(Rank rank) {
        return catalogue.welcome(rank.id());
    }
}
