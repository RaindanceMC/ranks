package de.raindancer.modules.ranks;

import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.preference.Preference;
import de.raindancer.modules.ranks.model.Holder;
import de.raindancer.modules.ranks.model.Place;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.service.RankService;
import de.raindancer.modules.ranks.service.RankService.Outcome;
import org.bukkit.entity.Player;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RanksPreferencesTest {

    private final UUID id = UUID.randomUUID();
    private final Player player = mock(Player.class);
    private final RankService ranks = mock(RankService.class);
    private final Messages messages = mock(Messages.class);
    private final RanksServices services = new RanksServices(null, null, null, null, messages, null, ranks);

    @BeforeEach
    void wire() {
        when(player.getUniqueId()).thenReturn(id);
        when(ranks.settings()).thenReturn(RanksSettings.DEFAULTS);
    }

    private List<Preference> all() {
        return RanksPreferences.all(() -> services);
    }

    @Test
    void threeSwitchesAndAPageWithClearTitles() {
        assertThat(all()).extracting(Preference::id)
                .containsExactly("ranks:show-chat", "ranks:show-nametag", "ranks:show-tablist", "ranks:rank");
        assertThat(all()).extracting(Preference::title).containsExactly("Your rank in chat",
                "Your rank on your nametag", "Your rank in the tab list", "Your rank");
        assertThat(all().get(3).isPage()).isTrue();
    }

    @Test
    void aSwitchShowsWhatTheHolderHasAndSetsItThroughTheService() {
        Preference chat = all().get(0);
        when(ranks.holder(id)).thenReturn(Holder.fresh(id).showing(Place.CHAT, false));
        assertThat(chat.state(player)).isEqualTo("Off");

        when(ranks.show(id, Place.CHAT, true)).thenReturn(Outcome.DONE);
        chat.choose().accept(player, 0);
        verify(ranks).show(id, Place.CHAT, true);
        verify(messages).send(player, "ranks.shown", "place", Place.CHAT.title());
    }

    @Test
    void eachSwitchMeansItsOwnPlace() {
        when(ranks.show(any(), any(), org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(Outcome.DONE);
        all().get(1).choose().accept(player, 1);
        all().get(2).choose().accept(player, 1);
        verify(ranks).show(id, Place.NAMETAG, false);
        verify(ranks).show(id, Place.TABLIST, false);
        verify(messages).send(player, "ranks.hidden", "place", Place.NAMETAG.title());
    }

    @Test
    void aRefusalSaysSoInsteadOfPretending() {
        when(ranks.show(id, Place.CHAT, false)).thenReturn(Outcome.NOT_ALLOWED);
        all().get(0).choose().accept(player, 1);
        verify(messages).send(player, "ranks.choosing-off");
        when(ranks.show(id, Place.CHAT, true)).thenReturn(Outcome.NOT_SAVED);
        all().get(0).choose().accept(player, 0);
        verify(messages).send(player, "ranks.not-saved");
    }

    @Test
    void onlyWhoHoldsARankIsOffered() {
        when(ranks.held(id)).thenReturn(List.of());
        assertThat(all()).allSatisfy(each -> assertThat(each.shown().test(player)).isFalse());

        when(ranks.held(id)).thenReturn(List.of(mock(Rank.class)));
        assertThat(all()).allSatisfy(each -> assertThat(each.shown().test(player)).isTrue());
    }

    @Test
    void theSwitchesAreHiddenWhenTheServerPicksForEverybody() {
        when(ranks.held(id)).thenReturn(List.of(mock(Rank.class)));
        when(ranks.settings()).thenReturn(new RanksSettings(true, true, true, false, true, true, "Team", true));
        assertThat(all().subList(0, 3)).allSatisfy(each -> assertThat(each.shown().test(player)).isFalse());
    }
}
