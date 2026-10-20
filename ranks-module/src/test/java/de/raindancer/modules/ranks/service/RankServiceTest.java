package de.raindancer.modules.ranks.service;

import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.ui.identity.NameBadges;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.ranks.RanksModule;
import de.raindancer.modules.ranks.RanksSettings;
import de.raindancer.modules.ranks.model.Holder;
import de.raindancer.modules.ranks.model.Place;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.service.RankService.Outcome;
import de.raindancer.modules.ranks.store.HolderBook;
import de.raindancer.modules.ranks.store.RankCatalogue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class RankServiceTest {

    private static final UUID ADA = UUID.randomUUID();

    @TempDir
    Path folder;
    private RankService service;
    private Rank owner;
    private Rank dev;

    @BeforeEach
    void setUp() {
        RankCatalogue catalogue = new RankCatalogue(new YamlStore(folder.resolve("ranks.yml")),
                () -> RanksModule.class.getResourceAsStream("ranks.yml"), new YamlStore(folder.resolve("custom-ranks.yml")));
        catalogue.reload();
        HolderBook book = new HolderBook(new YamlStore(folder.resolve("holders.yml")));
        book.load();
        service = new RankService(catalogue, book, RanksSettings.DEFAULTS);
        owner = service.rank("owner").orElseThrow();
        dev = service.rank("dev").orElseThrow();
    }

    private String badge(NameBadges.Place place) {
        return service.badge(ADA, place).map(Text::plain).orElse("");
    }

    @Test
    @DisplayName("a given rank is the badge in chat, above the head and in the tablist")
    void given() {
        assertThat(badge(NameBadges.Place.CHAT)).isEmpty();
        assertThat(service.give(ADA, owner)).isEqualTo(Outcome.DONE);
        assertThat(service.give(ADA, owner)).isEqualTo(Outcome.ALREADY);

        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[OWNER]");
        assertThat(badge(NameBadges.Place.NAMETAG)).isEqualTo("[OWNER]");
        assertThat(badge(NameBadges.Place.TABLIST)).isEqualTo("[OWNER]");
        assertThat(service.sortWeight(ADA)).isEqualTo(99);
    }

    @Test
    @DisplayName("a player hides their rank in one place and it is gone from exactly that place")
    void hideOnePlace() {
        service.give(ADA, owner);
        assertThat(service.show(ADA, Place.NAMETAG, false)).isEqualTo(Outcome.DONE);

        assertThat(badge(NameBadges.Place.NAMETAG)).isEmpty();
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[OWNER]");
        assertThat(badge(NameBadges.Place.TABLIST)).isEqualTo("[OWNER]");
    }

    @Test
    @DisplayName("the server switching a place off wins over the player")
    void serverOff() {
        service.give(ADA, owner);
        service.settings(new RanksSettings(true, true, false, true, true, true, "Team"));
        assertThat(badge(NameBadges.Place.TABLIST)).isEmpty();
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[OWNER]");
    }

    @Test
    @DisplayName("wearing: a held rank, none, back to auto; somebody else's rank is refused")
    void wearing() {
        service.give(ADA, owner);
        assertThat(service.wear(ADA, "dev")).isEqualTo(Outcome.NOT_HELD);
        service.give(ADA, dev);
        assertThat(service.wear(ADA, "dev")).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[DEV]");
        assertThat(service.wear(ADA, Holder.HIDDEN)).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).isEmpty();
        assertThat(service.wear(ADA, "")).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[OWNER]");
    }

    @Test
    @DisplayName("taking the worn rank falls back to the next; taking one not held says so")
    void taking() {
        service.give(ADA, owner);
        service.give(ADA, dev);
        service.wear(ADA, "owner");
        assertThat(service.take(ADA, owner)).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[DEV]");
        assertThat(service.holder(ADA).worn()).isEmpty();
        assertThat(service.take(ADA, owner)).isEqualTo(Outcome.NOT_HELD);
        assertThat(service.holdersOf(dev)).containsExactly(ADA);
    }

    @Test
    @DisplayName("with choosing off, players cannot change where or which — and their old choices stop counting")
    void choosingOff() {
        service.give(ADA, owner);
        service.show(ADA, Place.CHAT, false);
        service.settings(new RanksSettings(true, true, true, false, true, true, "Team"));
        assertThat(service.show(ADA, Place.CHAT, true)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(service.wear(ADA, Holder.HIDDEN)).isEqualTo(Outcome.NOT_ALLOWED);
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[OWNER]");
    }

    @Test
    @DisplayName("staff make a rank, give it, restyle it and delete it — and a bad tag or id is refused")
    void customRanks() {
        assertThat(service.create("vip", "VIP", 40)).isEqualTo(Outcome.DONE);
        assertThat(service.create("vip", "VIP", 40)).isEqualTo(Outcome.ALREADY);
        assertThat(service.create("Bad Id", "X", 1)).isEqualTo(Outcome.INVALID);
        assertThat(service.create("long", "THIS TAG IS FAR TOO LONG FOR IT", 1)).isEqualTo(Outcome.INVALID);
        Rank vip = service.rank("vip").orElseThrow();
        assertThat(vip.tag()).contains("[VIP]");
        service.give(ADA, vip);
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[VIP]");

        de.raindancer.core.ui.text.NameStyle gradient = de.raindancer.core.ui.text.TypedStyles
                .read(java.util.List.of("gold", "#ff0000", "bold"), word -> null).style().orElseThrow();
        assertThat(service.restyle("vip", gradient)).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).as("same letters, new paint").isEqualTo("[VIP]");
        assertThat(service.rank("vip").orElseThrow().tag()).contains("bold");

        assertThat(service.retag("vip", "[V.I.P.]")).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).isEqualTo("[V.I.P.]");
        assertThat(service.reweigh("vip", 120)).isEqualTo(Outcome.INVALID);
        assertThat(service.reweigh("vip", 85)).isEqualTo(Outcome.DONE);
        assertThat(service.rename("vip", "Very Important")).isEqualTo(Outcome.DONE);

        assertThat(service.delete("vip")).isEqualTo(Outcome.DONE);
        assertThat(badge(NameBadges.Place.CHAT)).as("a deleted rank shows nowhere").isEmpty();
        assertThat(service.delete("vip")).isEqualTo(Outcome.NOT_HELD);
    }

    @Test
    @DisplayName("holding a team rank lists somebody in the team section of the tablist, unless the server says no")
    void teamSection() {
        assertThat(service.section(ADA)).isEmpty();
        assertThat(service.create("vip", "[VIP]", 10)).isEqualTo(Outcome.DONE);
        service.give(ADA, service.rank("vip").orElseThrow());
        assertThat(service.section(ADA)).as("vip is not team").isEmpty();
        service.give(ADA, service.rank("team").orElseThrow());
        assertThat(service.section(ADA)).contains("Team");
        assertThat(service.team("vip", true)).isEqualTo(Outcome.DONE);
        service.settings(new RanksSettings(true, true, true, true, true, false, "Team"));
        assertThat(service.section(ADA)).isEmpty();
    }

    private static final UUID BOB = UUID.randomUUID();

    /** LuckPerms with the staff groups; Ada and Bob are online. */
    private FakeGroups luckPerms() {
        FakeGroups groups = new FakeGroups().group("default").group("dev").group("owner", "admin")
                .group("admin", "mod").group("mod");
        service.linkTo(groups, player -> true);
        return groups;
    }

    @Test
    @DisplayName("a permission group of a rank's name gives that rank — the tag follows LuckPerms")
    void rankFromGroup() {
        FakeGroups groups = luckPerms();
        groups.join(ADA, "dev");
        groups.join(ADA, "default");

        assertThat(service.held(ADA)).extracting(Rank::id).containsExactly("dev");
        assertThat(service.worn(ADA)).map(Rank::id).contains("dev");
        assertThat(badge(NameBadges.Place.CHAT)).contains("DEV");
        assertThat(service.section(ADA)).isPresent();
    }

    @Test
    @DisplayName("an offline player's groups are not asked for while drawing — that would be a storage read")
    void offlineNotAsked() {
        FakeGroups groups = new FakeGroups().group("dev");
        groups.join(ADA, "dev");
        service.linkTo(groups, player -> false);

        assertThat(service.held(ADA)).isEmpty();
    }

    @Test
    @DisplayName("groups and given ranks add up; the heaviest is worn")
    void groupsAndGiven() {
        service.give(ADA, dev);
        FakeGroups groups = luckPerms();
        groups.join(ADA, "owner");

        assertThat(service.held(ADA)).extracting(Rank::id).containsExactly("owner", "admin", "dev", "mod");
        assertThat(service.worn(ADA)).map(Rank::id).contains("owner");
    }

    @Test
    @DisplayName("a rank held through a group can be worn by choice")
    void wearFromGroup() {
        service.give(ADA, owner);
        luckPerms().join(ADA, "dev");

        assertThat(service.wear(ADA, "dev")).isEqualTo(Outcome.DONE);
        assertThat(service.worn(ADA)).map(Rank::id).contains("dev");
    }

    @Test
    @DisplayName("a rank linked to differently named groups is held through any of them")
    void linkedGroups() {
        FakeGroups groups = luckPerms().group("moderator").group("trialmod");
        assertThat(service.link("mod", java.util.List.of("Moderator", "trialmod"))).isEqualTo(Outcome.DONE);
        groups.join(ADA, "trialmod");

        assertThat(service.rank("mod").orElseThrow().groups()).containsExactly("moderator", "trialmod");
        assertThat(service.held(ADA)).extracting(Rank::id).containsExactly("mod");
        groups.leave(ADA, "trialmod");
        groups.join(ADA, "mod");
        assertThat(service.held(ADA)).as("the rank's own name no longer counts once groups are named").isEmpty();

        assertThat(service.link("mod", java.util.List.of("not a group!"))).isEqualTo(Outcome.INVALID);
        assertThat(service.link("mod", java.util.List.of())).isEqualTo(Outcome.DONE);
        assertThat(service.rank("mod").orElseThrow().groups()).as("none named: its own name again").containsExactly("mod");
    }

    @Test
    @DisplayName("giving a rank with a LuckPerms group puts them in the group, not in holders.yml")
    void giveWritesThrough() {
        FakeGroups groups = luckPerms();

        assertThat(service.give(BOB, dev)).isEqualTo(Outcome.DONE);
        assertThat(groups.directGroupsOf(BOB)).containsExactly("dev");
        assertThat(service.holder(BOB).ranks()).isEmpty();
        assertThat(service.held(BOB)).extracting(Rank::id).containsExactly("dev");
        assertThat(service.give(BOB, dev)).isEqualTo(Outcome.ALREADY);
    }

    @Test
    @DisplayName("a rank held through an inherited group is already held")
    void giveInherited() {
        luckPerms().join(BOB, "admin");

        assertThat(service.give(BOB, service.rank("mod").orElseThrow())).isEqualTo(Outcome.ALREADY);
    }

    @Test
    @DisplayName("a rank without a group, or a server without LuckPerms, keeps it in holders.yml")
    void giveWithoutGroup() {
        FakeGroups groups = luckPerms();
        Rank tester = service.rank("tester").orElseThrow();

        assertThat(service.give(BOB, tester)).isEqualTo(Outcome.DONE);
        assertThat(service.holder(BOB).ranks()).containsExactly("tester");
        assertThat(groups.directGroupsOf(BOB)).isEmpty();

        groups.editable = false;
        assertThat(service.give(BOB, dev)).isEqualTo(Outcome.DONE);
        assertThat(service.holder(BOB).ranks()).contains("dev");
        assertThat(groups.directGroupsOf(BOB)).isEmpty();
    }

    @Test
    @DisplayName("taking a rank takes them out of its group and out of holders.yml")
    void takeWritesThrough() {
        FakeGroups groups = luckPerms();
        groups.editable = false;
        service.give(BOB, dev);
        groups.editable = true;
        groups.join(BOB, "dev");

        assertThat(service.take(BOB, dev)).isEqualTo(Outcome.DONE);
        assertThat(groups.directGroupsOf(BOB)).isEmpty();
        assertThat(service.holder(BOB).ranks()).isEmpty();
        assertThat(service.held(BOB)).isEmpty();
        assertThat(service.take(BOB, dev)).isEqualTo(Outcome.NOT_HELD);
    }

    @Test
    @DisplayName("a rank that comes with a higher group cannot be taken alone, and nothing is changed")
    void takeInherited() {
        FakeGroups groups = luckPerms();
        groups.join(BOB, "admin");

        assertThat(service.take(BOB, service.rank("mod").orElseThrow())).isEqualTo(Outcome.INHERITED);
        assertThat(groups.directGroupsOf(BOB)).containsExactly("admin");
        assertThat(service.inheritedFrom(BOB, service.rank("mod").orElseThrow())).contains("admin");
    }

    @Test
    @DisplayName("everybody holding a rank: from holders.yml and from its groups")
    void everyHolder() {
        FakeGroups groups = luckPerms();
        groups.editable = false;
        service.give(ADA, dev);
        groups.editable = true;
        service.give(BOB, dev);

        assertThat(service.everyHolderOf(dev)).containsExactlyInAnyOrder(ADA, BOB);
        assertThat(service.holdersOf(dev)).as("the quick count reads holders.yml only").containsExactly(ADA);
    }

    @Test
    @DisplayName("the staff ranks ship, named like their groups")
    void staffRanksShip() {
        for (String id : java.util.List.of("owner", "admin", "mod", "support", "builder", "creator", "dev", "tester")) {
            assertThat(service.rank(id)).as(id).isPresent();
            assertThat(service.rank(id).orElseThrow().team()).as(id + " is on the team").isTrue();
        }
    }
}
