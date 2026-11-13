package de.raindancer.modules.ranks.rules;

import de.raindancer.modules.ranks.model.Rank;

import java.util.List;
import java.util.Optional;

/**
 * Which rank /rank puts beside the player's head: one whose colours players pick (Prime). Somebody without it sees
 * what it is and where to get it, somebody with it where to pick its colours.
 */
public final class ShowcaseRule {

    /** @param held whether the player has it: then it leads to its colours, else to how to get it */
    public record Showcase(Rank rank, boolean held) {
    }

    private ShowcaseRule() {
    }

    public static Optional<Showcase> of(List<Rank> all, List<Rank> held) {
        return all.stream().filter(rank -> !rank.styles().isEmpty()).findFirst()
                .map(rank -> new Showcase(rank, held.stream().anyMatch(own -> own.id().equals(rank.id()))));
    }
}
