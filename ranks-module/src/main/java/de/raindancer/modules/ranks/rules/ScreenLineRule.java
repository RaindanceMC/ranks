package de.raindancer.modules.ranks.rules;

import de.raindancer.modules.ranks.model.Rank;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/** What the ranks' screens count, order and say. */
public final class ScreenLineRule implements IRanksRule {

    private static final Comparator<Rank> SENIOR_FIRST = Comparator.comparingInt(Rank::weight).reversed()
            .thenComparing(Rank::id);

    /** Somebody online and the ranks they hold. */
    public record Member(String name, List<Rank> held) {
    }

    /** Somebody of the team, under their most senior team rank. */
    public record TeamEntry(String name, Rank rank) {
    }

    public record Count(Rank rank, int online) {
    }

    public List<TeamEntry> team(List<Member> online) {
        List<TeamEntry> entries = new ArrayList<>();
        for (Member member : online) {
            member.held().stream().filter(Rank::team).min(SENIOR_FIRST)
                    .ifPresent(rank -> entries.add(new TeamEntry(member.name(), rank)));
        }
        entries.sort(Comparator.comparing((TeamEntry entry) -> entry.rank(), SENIOR_FIRST)
                .thenComparing(TeamEntry::name, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public List<Count> counts(List<Member> online, List<Rank> ranks) {
        return ranks.stream().sorted(SENIOR_FIRST)
                .map(rank -> new Count(rank, (int) online.stream().filter(member -> member.held().contains(rank)).count()))
                .filter(count -> count.online() > 0).toList();
    }

    /** The titles of the other ranks somebody holds, most senior first. */
    public String also(List<Rank> held, Rank worn) {
        return held.stream().filter(rank -> !rank.id().equals(worn.id())).sorted(SENIOR_FIRST).map(Rank::title)
                .collect(Collectors.joining(", "));
    }

    public String more(int total, int shown) {
        return total > shown ? "<dark_gray>and " + (total - shown) + " more" : "";
    }

    @Override
    public String describe() {
        return "what the ranks' screens count, order and say";
    }
}
