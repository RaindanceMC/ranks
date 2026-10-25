package de.raindancer.modules.ranks.service;

import de.raindancer.core.ui.hologram.ScreenKind;
import de.raindancer.core.ui.hologram.Screens;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.rules.ScreenLineRule;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.Server;
import org.bukkit.plugin.Plugin;

import java.util.List;
import java.util.UUID;
import java.util.function.Predicate;

/** The ranks' screens for Core's {@code /screen}: everybody's own rank, the team online, and who holds what. */
public final class RankScreens {

    public static final List<String> KINDS = List.of("rank-you", "rank-team", "rank-online");
    private static final MiniMessage MINI = MiniMessage.miniMessage();
    private static final int SHOWN = 9;

    private final RankService ranks;
    private final Server server;
    private final Predicate<UUID> vanished;
    private final ScreenLineRule rule = new ScreenLineRule();

    public RankScreens(RankService ranks, Server server, Predicate<UUID> vanished) {
        this.ranks = ranks;
        this.server = server;
        this.vanished = vanished;
    }

    public void register(Screens screens, Plugin owner) {
        screens.register(owner, ScreenKind.personal("rank-you", "Your rank",
                "Everybody's own rank, what it says and the other ranks they hold.", Material.NAME_TAG, this::you));
        screens.register(owner, ScreenKind.everyone("rank-team", "The team online",
                "The server's team members who are online, most senior first.", Material.GOLDEN_HELMET, this::team));
        screens.register(owner, ScreenKind.everyone("rank-online", "Ranks online",
                "How many players online hold each rank.", Material.BOOKSHELF, this::online));
    }

    private List<ScreenLineRule.Member> members() {
        return server.getOnlinePlayers().stream().filter(player -> !vanished.test(player.getUniqueId()))
                .map(player -> new ScreenLineRule.Member(player.getName(), ranks.held(player.getUniqueId()))).toList();
    }

    private Component you(ScreenKind.Viewer viewer) {
        Component title = MINI.deserialize("<gold><bold>Your rank</bold></gold>");
        var worn = ranks.worn(viewer.id());
        if (worn.isEmpty()) {
            return title.append(MINI.deserialize("\n<gray>None — ranks are given by the team"));
        }
        Rank rank = worn.get();
        Component text = title.append(Component.newline()).append(ranks.tagOf(rank))
                .append(MINI.deserialize(" <white>" + MINI.escapeTags(rank.title())));
        if (!rank.description().isEmpty()) {
            text = text.append(MINI.deserialize("\n<gray>" + MINI.escapeTags(rank.description().get(0))));
        }
        String also = rule.also(ranks.held(viewer.id()), rank);
        return also.isEmpty() ? text : text.append(MINI.deserialize("\n<gray>Also <white>" + MINI.escapeTags(also)));
    }

    private Component team() {
        Component text = MINI.deserialize("<gold><bold>The team online</bold></gold>");
        var entries = rule.team(members());
        if (entries.isEmpty()) {
            return text.append(MINI.deserialize("\n<gray>Nobody right now"));
        }
        for (var entry : entries.stream().limit(SHOWN).toList()) {
            text = text.append(Component.newline()).append(ranks.tagOf(entry.rank()))
                    .append(MINI.deserialize(" <white>" + MINI.escapeTags(entry.name())));
        }
        String more = rule.more(entries.size(), SHOWN);
        return more.isEmpty() ? text : text.append(MINI.deserialize("\n" + more));
    }

    private Component online() {
        Component text = MINI.deserialize("<gold><bold>Ranks online</bold></gold>");
        var counts = rule.counts(members(), ranks.ranks());
        if (counts.isEmpty()) {
            return text.append(MINI.deserialize("\n<gray>Nobody online holds a rank"));
        }
        for (var count : counts.stream().limit(SHOWN).toList()) {
            text = text.append(Component.newline()).append(ranks.tagOf(count.rank()))
                    .append(MINI.deserialize(" <white>" + count.online()));
        }
        String more = rule.more(counts.size(), SHOWN);
        return more.isEmpty() ? text : text.append(MINI.deserialize("\n" + more));
    }
}
