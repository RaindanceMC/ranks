package de.raindancer.modules.ranks.service;

import de.raindancer.core.ui.statue.StatueBoard;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class CreatorStatuesTest {

    private static final UUID ADA = UUID.randomUUID();
    private static final UUID BO = UUID.randomUUID();
    private static final UUID CY = UUID.randomUUID();
    private static final Map<UUID, String> NAMES = Map.of(ADA, "ada", BO, "Bo", CY, "Cy");

    private final AtomicReference<Set<UUID>> holders = new AtomicReference<>(Set.of());
    private final CreatorStatues creators = new CreatorStatues(() -> {
        Set<UUID> now = holders.get();
        if (now == null) {
            throw new IllegalStateException("LuckPerms' storage is gone");
        }
        return now;
    });
    private final StatueBoard board = creators.board(id -> Optional.ofNullable(NAMES.get(id)));

    private List<String> shown() {
        return new ArrayList<>(board.standings().get().stream().map(StatueBoard.Standing::name).toList());
    }

    @Test
    @DisplayName("the creator board thanks whoever holds the creator rank, by name; nobody before the first look")
    void holdersByName() {
        assertThat(board.id()).isEqualTo("creator");
        assertThat(board.thankYou()).isTrue();
        assertThat(board.title(2, "Bo")).contains("CREATOR").contains("Bo").doesNotContain("#2");
        assertThat(shown()).isEmpty();
        holders.set(Set.of(CY, BO, ADA, UUID.randomUUID()));
        creators.refresh();
        assertThat(shown()).as("by name, somebody never seen left out").containsExactly("ada", "Bo", "Cy");
    }

    @Test
    @DisplayName("a look that fails keeps the creators from the last one, rather than emptying the statues")
    void failedLookKeepsTheLast() {
        holders.set(Set.of(BO));
        creators.refresh();
        holders.set(null);
        creators.refresh();
        assertThat(shown()).containsExactly("Bo");
        holders.set(Set.of());
        creators.refresh();
        assertThat(shown()).as("the rank taken from everybody").isEmpty();
    }
}
