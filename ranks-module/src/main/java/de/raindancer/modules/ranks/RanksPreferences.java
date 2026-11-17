package de.raindancer.modules.ranks;

import de.raindancer.core.ui.preference.Preference;
import de.raindancer.modules.ranks.model.Place;
import de.raindancer.modules.ranks.screen.RankMenu;
import de.raindancer.modules.ranks.service.RankService.Outcome;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.function.Supplier;

/** What /preferences offers for ranks: where your rank shows, and a door to /rank. Only for players holding a rank. */
public final class RanksPreferences {

    private static final String GROUP = "Rank";

    private RanksPreferences() {
    }

    public static List<Preference> all(Supplier<RanksServices> services) {
        return List.of(
                show(services, Place.CHAT, "ranks:show-chat", "Your rank in chat",
                        "Your rank in front of your name on chat lines."),
                show(services, Place.NAMETAG, "ranks:show-nametag", "Your rank on your nametag",
                        "Your rank above your head."),
                show(services, Place.TABLIST, "ranks:show-tablist", "Your rank in the tab list",
                        "Your rank in front of your name in the tab list."),
                rank(services));
    }

    static Preference show(Supplier<RanksServices> services, Place place, String id, String title, String description) {
        return Preference.toggle(id, GROUP, title, description,
                        player -> services.get().ranks().holder(player.getUniqueId()).shows(place),
                        (player, on) -> {
                            RanksServices live = services.get();
                            Outcome outcome = live.ranks().show(player.getUniqueId(), place, on);
                            if (outcome == Outcome.DONE || outcome == Outcome.ALREADY) {
                                live.messages().send(player, on ? "ranks.shown" : "ranks.hidden", "place", place.title());
                            } else {
                                live.messages().send(player, outcome == Outcome.NOT_SAVED ? "ranks.not-saved" : "ranks.choosing-off");
                            }
                        })
                .shownTo(player -> picks(services.get(), player));
    }

    static Preference rank(Supplier<RanksServices> services) {
        return Preference.page("ranks:rank", GROUP, "Your rank",
                        "Which of your ranks you wear, its colours and where it shows.",
                        (player, back) -> new RankMenu(services.get(), player, back).open())
                .shownTo(player -> holds(services.get(), player));
    }

    private static boolean holds(RanksServices live, Player player) {
        return !live.ranks().held(player.getUniqueId()).isEmpty();
    }

    private static boolean picks(RanksServices live, Player player) {
        return holds(live, player) && live.ranks().settings().playersChoose();
    }
}
