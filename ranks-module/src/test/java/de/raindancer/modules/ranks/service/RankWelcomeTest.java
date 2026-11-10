package de.raindancer.modules.ranks.service;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.modules.ranks.RanksModule;
import de.raindancer.modules.ranks.RanksSettings;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.store.HolderBook;
import de.raindancer.modules.ranks.store.RankCatalogue;
import de.raindancer.modules.ranks.store.WelcomedBook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankWelcomeTest {

    private static final UUID ADA = UUID.randomUUID();

    @TempDir
    Path folder;
    private RankCatalogue catalogue;
    private RankService service;
    private RankWelcome welcome;

    @BeforeEach
    void setUp() {
        catalogue = new RankCatalogue(new YamlStore(folder.resolve("ranks.yml")),
                () -> RanksModule.class.getResourceAsStream("ranks.yml"), new YamlStore(folder.resolve("custom-ranks.yml")));
        catalogue.reload();
        HolderBook book = new HolderBook(new YamlStore(folder.resolve("holders.yml")));
        book.load();
        service = new RankService(catalogue, book, RanksSettings.DEFAULTS);
        welcome = new RankWelcome(service, catalogue, new WelcomedBook(new YamlStore(folder.resolve("welcomed.yml"))));
    }

    @Test
    @DisplayName("the shipped creator rank tells what it gives: free guild, every cosmetic")
    void creatorText() {
        String said = String.join(" ", catalogue.welcome("creator"));
        assertThat(said).containsIgnoringCase("guild").containsIgnoringCase("cosmetic");
        assertThat(catalogue.welcome("owner")).isEmpty();
    }

    @Test
    @DisplayName("a rank with a welcome is told once, whenever it is first seen held; one without never")
    void onceEach() {
        Rank creator = service.rank("creator").orElseThrow();
        assertThat(welcome.takeDue(ADA)).isEmpty();
        service.give(ADA, service.rank("owner").orElseThrow());
        assertThat(welcome.takeDue(ADA)).isEmpty();
        service.give(ADA, creator);
        assertThat(welcome.takeDue(ADA)).containsExactly(creator);
        assertThat(welcome.takeDue(ADA)).isEmpty();
        // Remembered in the file, so another server or a restart does not tell it again.
        RankWelcome later = new RankWelcome(service, catalogue, new WelcomedBook(new YamlStore(folder.resolve("welcomed.yml"))));
        assertThat(later.takeDue(ADA)).isEmpty();
    }

    @Test
    @DisplayName("a ranks.yml written before welcomes existed gets the shipped welcome filled in, the owner's edits kept")
    void filledIn() throws Exception {
        Path old = folder.resolve("old");
        Files.createDirectories(old);
        Files.writeString(old.resolve("ranks.yml"), """
                ranks:
                  creator:
                    team: true
                    title: "Streamer"
                    tag: "<light_purple>[CREATOR]</light_purple>"
                    weight: 45
                """);
        RankCatalogue upgraded = new RankCatalogue(new YamlStore(old.resolve("ranks.yml")),
                () -> RanksModule.class.getResourceAsStream("ranks.yml"));
        upgraded.reload();
        assertThat(upgraded.find("creator")).map(Rank::title).contains("Streamer");
        assertThat(upgraded.welcome("creator")).isNotEmpty();

        RankCatalogue none = new RankCatalogue(new YamlStore(folder.resolve("none.yml")),
                () -> new ByteArrayInputStream("ranks:\n  vip:\n    tag: \"[VIP]\"\n".getBytes(StandardCharsets.UTF_8)));
        none.reload();
        assertThat(none.welcome("vip")).isEmpty();
    }
}
