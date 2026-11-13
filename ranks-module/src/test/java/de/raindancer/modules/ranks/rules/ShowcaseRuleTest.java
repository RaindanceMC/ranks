package de.raindancer.modules.ranks.rules;

import de.raindancer.modules.ranks.model.Rank;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ShowcaseRuleTest {

    private static Rank rank(String id, Map<String, String> styles) {
        return new Rank(id, id, "[" + id + "]", 10, List.of(), false, List.of(id), styles);
    }

    private final Rank owner = rank("owner", Map.of());
    private final Rank prime = rank("prime", Map.of("hot-pink", "<#ff3d9a>[PRIME]", "pastel", "<#ffb3d9>[PRIME]"));

    @Test
    @DisplayName("a rank with colours to pick is shown to who lacks it as an offer, to who has it as a style choice")
    void offerOrStyle() {
        assertThat(ShowcaseRule.of(List.of(owner, prime), List.of(owner)))
                .contains(new ShowcaseRule.Showcase(prime, false));
        assertThat(ShowcaseRule.of(List.of(owner, prime), List.of(owner, prime)))
                .contains(new ShowcaseRule.Showcase(prime, true));
    }

    @Test
    @DisplayName("ranks without colours to pick are never offered")
    void plainRanks() {
        assertThat(ShowcaseRule.of(List.of(owner), List.of())).isEmpty();
    }
}
