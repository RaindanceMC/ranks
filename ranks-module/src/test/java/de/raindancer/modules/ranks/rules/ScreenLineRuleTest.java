package de.raindancer.modules.ranks.rules;

import de.raindancer.modules.ranks.model.Rank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("What the ranks' screens count and say")
class ScreenLineRuleTest {

    private final ScreenLineRule rule = new ScreenLineRule();

    private static Rank rank(String id, int weight, boolean team) {
        return new Rank(id, id.substring(0, 1).toUpperCase() + id.substring(1), "<red>[" + id + "]", weight,
                List.of(), team);
    }

    private final Rank owner = rank("owner", 99, true);
    private final Rank mod = rank("mod", 60, true);
    private final Rank vip = rank("vip", 20, false);

    @Test
    @DisplayName("the team online: only holders of a team rank, most senior rank first, then by name")
    void team() {
        var entries = rule.team(List.of(
                new ScreenLineRule.Member("Zed", List.of(mod, vip)),
                new ScreenLineRule.Member("Plain", List.of(vip)),
                new ScreenLineRule.Member("Ann", List.of(mod)),
                new ScreenLineRule.Member("Boss", List.of(vip, owner)),
                new ScreenLineRule.Member("Nobody", List.of())));
        assertThat(entries).extracting(ScreenLineRule.TeamEntry::name).containsExactly("Boss", "Ann", "Zed");
        assertThat(entries.get(0).rank()).isEqualTo(owner);
        assertThat(entries.get(2).rank()).isEqualTo(mod);
    }

    @Test
    @DisplayName("players online per rank: ranks nobody online holds are left out, most senior first")
    void counts() {
        var counts = rule.counts(List.of(
                new ScreenLineRule.Member("A", List.of(mod, vip)),
                new ScreenLineRule.Member("B", List.of(vip)),
                new ScreenLineRule.Member("C", List.of())), List.of(vip, owner, mod));
        assertThat(counts).extracting(count -> count.rank().id()).containsExactly("mod", "vip");
        assertThat(counts).extracting(ScreenLineRule.Count::online).containsExactly(1, 2);
    }

    @Test
    @DisplayName("'also' names the other ranks somebody holds, most senior first, without the one they wear")
    void also() {
        assertThat(rule.also(List.of(vip, owner, mod), owner)).isEqualTo("Mod, Vip");
        assertThat(rule.also(List.of(owner), owner)).isEmpty();
    }

    @Test
    @DisplayName("what is not shown is said as 'and n more'")
    void more() {
        assertThat(rule.more(12, 8)).isEqualTo("<dark_gray>and 4 more");
        assertThat(rule.more(8, 8)).isEmpty();
    }
}
