package de.raindancer.modules.ranks.service;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.ranks.RanksModule;
import de.raindancer.modules.ranks.RanksSettings;
import de.raindancer.modules.ranks.model.Holder;
import de.raindancer.modules.ranks.model.Place;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.store.HolderBook;
import de.raindancer.modules.ranks.store.RankCatalogue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankStylesTest {

    private static final UUID ADA = UUID.randomUUID();

    @TempDir
    Path folder;
    private RankService service;
    private HolderBook book;
    private Rank prime;

    @BeforeEach
    void setUp() {
        RankCatalogue catalogue = new RankCatalogue(new YamlStore(folder.resolve("ranks.yml")),
                () -> RanksModule.class.getResourceAsStream("ranks.yml"), new YamlStore(folder.resolve("custom-ranks.yml")));
        catalogue.reload();
        book = new HolderBook(new YamlStore(folder.resolve("holders.yml")));
        book.load();
        service = new RankService(catalogue, book, RanksSettings.DEFAULTS);
        prime = service.rank("prime").orElseThrow();
    }

    @Test
    @DisplayName("Prime ships with nine colourways, hot pink the default, all reading [PRIME]")
    void shipped() {
        assertThat(prime.groups()).containsExactly("prime");
        assertThat(prime.styles()).hasSize(9).containsKeys("hot-pink", "rose-gold", "pink-gold-pink",
                "gold-hot-pink", "pure-gold", "hyades", "pastel", "champagne", "magenta-gold");
        assertThat(prime.tag()).isEqualTo(prime.styles().get("hot-pink"));
        prime.styles().values().forEach(tag -> {
            assertThat(RankCatalogue.tagProblem(tag)).as(tag).isEmpty();
            assertThat(Text.plain(Text.styleOnly().deserialize(tag))).isEqualTo("[PRIME]");
        });
    }

    @Test
    @DisplayName("a holder picks a colourway of a rank they hold; it is what shows, and the default comes back on reset")
    void pick() {
        service.give(ADA, prime);
        assertThat(service.shown(ADA, Place.CHAT).orElseThrow().tag()).isEqualTo(prime.styles().get("hot-pink"));

        assertThat(service.pickStyle(ADA, "prime", "champagne")).isEqualTo(RankService.Outcome.DONE);
        assertThat(service.shown(ADA, Place.CHAT).orElseThrow().tag()).isEqualTo(prime.styles().get("champagne"));
        assertThat(service.worn(ADA).orElseThrow().tag()).isEqualTo(prime.styles().get("champagne"));
        assertThat(service.pickStyle(ADA, "prime", "champagne")).isEqualTo(RankService.Outcome.ALREADY);

        assertThat(service.pickStyle(ADA, "prime", "")).isEqualTo(RankService.Outcome.DONE);
        assertThat(service.shown(ADA, Place.CHAT).orElseThrow().tag()).isEqualTo(prime.tag());
    }

    @Test
    @DisplayName("no style of a rank not held, nor one the rank does not have")
    void refused() {
        assertThat(service.pickStyle(ADA, "prime", "champagne")).isEqualTo(RankService.Outcome.NOT_HELD);
        service.give(ADA, prime);
        assertThat(service.pickStyle(ADA, "prime", "neon")).isEqualTo(RankService.Outcome.INVALID);
    }

    @Test
    @DisplayName("a style that was removed from ranks.yml falls back to the rank's own tag")
    void removedStyle() {
        service.give(ADA, prime);
        book.change(ADA, holder -> holder.styled("prime", "gone"));
        assertThat(service.shown(ADA, Place.CHAT).orElseThrow().tag()).isEqualTo(prime.tag());
    }

    @Test
    @DisplayName("every colourway is made a badge too, right after its rank")
    void badges() {
        List<String> made = new ArrayList<>();
        service.badges(tag -> {
            made.add(tag);
            return java.util.Optional.empty();
        }, () -> false);
        java.util.Set<String> primes = new java.util.HashSet<>(prime.styles().values());
        primes.add(prime.tag());
        int at = made.indexOf(prime.tag());
        assertThat(new java.util.HashSet<>(made.subList(at, at + 1 + prime.styles().size()))).isEqualTo(primes);
    }

    @Test
    @DisplayName("the pick is kept in holders.yml and read back")
    void stored() {
        service.give(ADA, prime);
        service.pickStyle(ADA, "prime", "pastel");
        HolderBook again = new HolderBook(new YamlStore(folder.resolve("holders.yml")));
        again.load();
        assertThat(again.of(ADA).styles()).isEqualTo(Map.of("prime", "pastel"));
        assertThat(Holder.fresh(ADA).styled("prime", "pastel").isBlank()).isFalse();
    }
}
