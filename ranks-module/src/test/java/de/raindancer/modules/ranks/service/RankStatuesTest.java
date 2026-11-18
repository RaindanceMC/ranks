package de.raindancer.modules.ranks.service;

import de.raindancer.core.ui.statue.StatueBoard;
import de.raindancer.modules.ranks.model.Rank;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankStatuesTest {

    private static final UUID ADA = UUID.randomUUID();
    private static final UUID BO = UUID.randomUUID();
    private static final UUID CY = UUID.randomUUID();
    private static final Map<UUID, String> NAMES = Map.of(ADA, "ada", BO, "Bo", CY, "Cy");
    private static final Rank OWNER = new Rank("owner", "Owner", "<dark_red>[OWNER]", 100, List.of(), true);
    private static final Rank MOD = new Rank("mod", "Mod", "<gold>[MOD]", 60, List.of(), true);
    private static final Rank CREATOR = new Rank("creator", "Creator", "<light_purple>[CREATOR]", 30, List.of(), true);
    private static final Rank PRIME = new Rank("prime", "Prime", "[PRIME]", 38, List.of(), false);

    private final Map<String, Set<UUID>> holders = new HashMap<>();
    private boolean storageGone;

    private RankStatues statues(String id, List<Rank> ranks) {
        return new RankStatues(id, "Our " + id, id, "<gray>[" + id.toUpperCase() + "]", () -> ranks, rank -> {
            if (storageGone) {
                throw new IllegalStateException("LuckPerms' storage is gone");
            }
            return holders.getOrDefault(rank.id(), Set.of());
        }, rank -> Component.text("<" + rank.id() + ">"));
    }

    private static List<String> shown(StatueBoard board) {
        return new ArrayList<>(board.standings().get().stream().map(StatueBoard.Standing::name).toList());
    }

    private static String plain(Component text) {
        return PlainTextComponentSerializer.plainText().serialize(text);
    }

    @Test
    @DisplayName("the creator board thanks whoever holds the creator rank, by name, with no line under it")
    void creators() {
        RankStatues creators = statues("creator", List.of(CREATOR));
        StatueBoard board = creators.board(id -> Optional.ofNullable(NAMES.get(id)));
        assertThat(board.id()).isEqualTo("creator");
        assertThat(board.thankYou()).isTrue();
        assertThat(board.caption()).isEmpty();
        assertThat(shown(board)).isEmpty();

        holders.put("creator", Set.of(CY, BO, ADA, UUID.randomUUID()));
        creators.refresh();
        assertThat(shown(board)).as("by name, somebody never seen left out").containsExactly("ada", "Bo", "Cy");
        assertThat(board.standings().get()).allSatisfy(each -> assertThat(plain(each.badge())).isEqualTo("<creator>"));
    }

    @Test
    @DisplayName("the team board is everybody on a team rank, most senior first, each with their own highest rank's badge")
    void team() {
        RankStatues team = statues("team", List.of(MOD, OWNER, PRIME));
        StatueBoard board = team.board(id -> Optional.ofNullable(NAMES.get(id)));
        holders.put("owner", Set.of(CY));
        holders.put("mod", Set.of(ADA, CY, BO));
        holders.put("prime", Set.of(BO));
        team.refresh();

        assertThat(shown(board)).containsExactly("Cy", "ada", "Bo");
        Map<String, String> badges = new HashMap<>();
        board.standings().get().forEach(each -> badges.put(each.name(), plain(each.badge())));
        assertThat(badges).containsEntry("Cy", "<owner>").containsEntry("ada", "<mod>").containsEntry("Bo", "<mod>");
    }

    @Test
    @DisplayName("a look that fails keeps the people from the last one, rather than emptying the statues")
    void failedLookKeepsTheLast() {
        RankStatues creators = statues("creator", List.of(CREATOR));
        StatueBoard board = creators.board(id -> Optional.ofNullable(NAMES.get(id)));
        holders.put("creator", Set.of(BO));
        creators.refresh();
        storageGone = true;
        creators.refresh();
        assertThat(shown(board)).containsExactly("Bo");
        storageGone = false;
        holders.put("creator", Set.of());
        creators.refresh();
        assertThat(shown(board)).as("the rank taken from everybody").isEmpty();
    }
}
