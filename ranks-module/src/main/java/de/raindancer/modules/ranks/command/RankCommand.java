package de.raindancer.modules.ranks.command;

import de.raindancer.core.platform.command.PlayerTargets;
import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.text.Markup;
import de.raindancer.modules.ranks.RanksServices;
import de.raindancer.modules.ranks.model.Holder;
import de.raindancer.modules.ranks.model.Place;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.screen.RankAdminMenu;
import de.raindancer.modules.ranks.screen.RankMenu;
import de.raindancer.modules.ranks.service.RankService;
import de.raindancer.modules.ranks.service.RankService.Outcome;
import de.raindancer.modules.ranks.util.PermissionNodes;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * {@code /rank} opens your ranks; {@code info [player]}, {@code wear <rank|none|auto>},
 * {@code show <place> [on|off]}, {@code style [style|default]} — the colours of the rank you wear. Staff: {@code give|take <player> <rank>}, {@code list [rank]}, {@code reload}.
 */
public final class RankCommand implements IRanksCommand {

    private final Supplier<RanksServices> services;

    public RankCommand(Supplier<RanksServices> services) {
        this.services = services;
    }

    @Override
    public void execute(CommandSourceStack source, String[] args) {
        RanksServices live = services.get();
        CommandSender sender = source.getSender();
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        switch (first) {
            case "" -> asPlayer(live, sender).ifPresent(player -> new RankMenu(live, player, null).open());
            case "info" -> info(live, sender, args.length > 1 ? args[1] : null);
            case "wear" -> asPlayer(live, sender).ifPresent(player -> wear(live, player, args));
            case "show" -> asPlayer(live, sender).ifPresent(player -> show(live, player, args));
            case "style" -> asPlayer(live, sender).ifPresent(player -> style(live, player, args));
            case "give", "take" -> staffChange(live, sender, args, first.equals("give"));
            case "list" -> list(live, sender, args);
            case "manage" -> {
                if (!sender.hasPermission(PermissionNodes.ADMIN)) {
                    live.messages().send(sender, "ranks.usage");
                } else {
                    asPlayer(live, sender).ifPresent(player -> new RankAdminMenu(live, player, null).open());
                }
            }
            case "create", "edit", "delete" -> manage(live, sender, first, args);
            case "reload" -> {
                if (!sender.hasPermission(PermissionNodes.ADMIN)) {
                    live.messages().send(sender, "ranks.usage");
                    return;
                }
                live.messages().send(sender, "ranks.reloaded", "count", String.valueOf(live.ranks().reload()));
                live.ranks().problems().forEach(problem -> live.messages().send(sender, "ranks.problem", "problem", problem));
            }
            default -> live.messages().send(sender, "ranks.usage");
        }
    }

    private static void wear(RanksServices live, Player player, String[] args) {
        if (args.length < 2) {
            live.messages().send(player, "ranks.usage-wear");
            return;
        }
        String wanted = args[1].toLowerCase(Locale.ROOT);
        String id = wanted.equals("auto") ? "" : wanted;
        Optional<Rank> rank = live.ranks().rank(id);
        if (!id.isEmpty() && !id.equals(Holder.HIDDEN) && rank.isEmpty()) {
            live.messages().send(player, "ranks.unknown", "name", args[1]);
            return;
        }
        Outcome outcome = live.ranks().wear(player.getUniqueId(), id);
        switch (outcome) {
            case DONE, ALREADY -> {
                if (id.equals(Holder.HIDDEN)) {
                    live.messages().send(player, "ranks.wear-none");
                } else {
                    live.ranks().worn(player.getUniqueId()).ifPresentOrElse(
                            worn -> live.messages().send(player, "ranks.wear", "rank", new Markup(live.ranks().shownTag(worn))),
                            () -> live.messages().send(player, "ranks.none-yours"));
                }
            }
            case NOT_HELD -> live.messages().send(player, "ranks.not-yours", "rank", new Markup(live.ranks().shownTag(rank.orElseThrow())));
            default -> tellRefusal(live, player, outcome);
        }
    }

    /** Lists the styles of the rank they wear, or picks one; {@code default} is the rank's own. */
    private static void style(RanksServices live, Player player, String[] args) {
        Optional<Rank> worn = live.ranks().worn(player.getUniqueId()).flatMap(shown -> live.ranks().rank(shown.id()));
        if (worn.isEmpty()) {
            live.messages().send(player, "ranks.none-yours");
            return;
        }
        Rank rank = worn.get();
        if (rank.styles().isEmpty()) {
            live.messages().send(player, "ranks.style-none", "rank", new Markup(live.ranks().shownTag(rank)));
            return;
        }
        if (args.length < 2) {
            live.messages().send(player, "ranks.style-list", "rank", new Markup(live.ranks().shownTag(rank)));
            String picked = live.ranks().holder(player.getUniqueId()).styleOf(rank.id());
            rank.styles().keySet().forEach(style -> live.messages().send(player,
                    style.equals(picked) || (picked.isEmpty() && rank.styled(style).tag().equals(rank.tag()))
                            ? "ranks.style-line-worn" : "ranks.style-line",
                    "badge", new Markup(live.ranks().shownTag(rank.styled(style))), "style", style,
                    "title", Rank.styleTitle(style)));
            return;
        }
        String wanted = args[1].toLowerCase(Locale.ROOT);
        String style = wanted.equals("default") ? "" : wanted;
        Outcome outcome = live.ranks().pickStyle(player.getUniqueId(), rank.id(), style);
        switch (outcome) {
            case DONE, ALREADY -> live.messages().send(player, "ranks.style-picked",
                    "rank", new Markup(live.ranks().shownTag(rank.styled(style))));
            case INVALID -> live.messages().send(player, "ranks.style-unknown", "style", args[1]);
            default -> tellRefusal(live, player, outcome);
        }
    }

    private static void show(RanksServices live, Player player, String[] args) {
        Optional<Place> place = args.length < 2 ? Optional.empty() : Place.byWord(args[1]);
        if (place.isEmpty()) {
            live.messages().send(player, "ranks.usage-show");
            return;
        }
        boolean now = live.ranks().holder(player.getUniqueId()).shows(place.get());
        boolean on;
        if (args.length < 3) {
            on = !now;
        } else if (List.of("on", "yes", "true").contains(args[2].toLowerCase(Locale.ROOT))) {
            on = true;
        } else if (List.of("off", "no", "false").contains(args[2].toLowerCase(Locale.ROOT))) {
            on = false;
        } else {
            live.messages().send(player, "ranks.usage-show");
            return;
        }
        Outcome outcome = live.ranks().show(player.getUniqueId(), place.get(), on);
        if (outcome == Outcome.DONE || outcome == Outcome.ALREADY) {
            live.messages().send(player, on ? "ranks.shown" : "ranks.hidden", "place", place.get().title());
        } else {
            tellRefusal(live, player, outcome);
        }
    }

    private static void staffChange(RanksServices live, CommandSender sender, String[] args, boolean give) {
        if (!sender.hasPermission(PermissionNodes.ADMIN)) {
            live.messages().send(sender, "ranks.usage");
            return;
        }
        if (args.length < 3) {
            live.messages().send(sender, "ranks.usage-staff");
            return;
        }
        Optional<Rank> rank = live.ranks().rank(args[2]);
        if (rank.isEmpty()) {
            live.messages().send(sender, "ranks.unknown", "name", args[2]);
            return;
        }
        // A rank with a LuckPerms group is changed in LuckPerms' storage: not on the server thread.
        resolve(live, sender, args[1], (id, name) -> Scheduling.async(live.plugin(), () -> tellChange(live, sender, id,
                name, rank.get(), give, give ? live.ranks().give(id, rank.get()) : live.ranks().take(id, rank.get()))));
    }

    /**
     * Who {@code typed} is — a name or selector this server knows, else a uuid, else a name asked of Mojang off the
     * server thread, so somebody who never joined can be given a rank. Says so when nobody is meant.
     */
    private static void resolve(RanksServices live, CommandSender sender, String typed,
                                java.util.function.BiConsumer<UUID, String> then) {
        boolean uuid = typed.trim().length() == 36;
        Optional<OfflinePlayer> known = uuid ? Optional.empty() : PlayerTargets.find(live.server(), sender, typed);
        if (known.isPresent()) {
            then.accept(known.get().getUniqueId(), nameOf(live, known.get()));
            return;
        }
        de.raindancer.core.social.presence.ProfileLookup lookup = profileLookup(live);
        if (lookup == null) {
            live.messages().send(sender, "ranks.never-here", "player", typed);
            return;
        }
        lookup.byNameOrId(typed).thenAccept(found -> {
            if (found.isEmpty()) {
                live.messages().send(sender, "ranks.no-such-player", "player", typed);
                return;
            }
            UUID id = found.get().id();
            String name = found.get().name() != null ? found.get().name()
                    : Optional.ofNullable(live.core().identities().nameOf(id).orElse(null)).orElse(id.toString());
            then.accept(id, name);
        });
    }

    private static de.raindancer.core.social.presence.ProfileLookup profileLookup(RanksServices live) {
        try {
            return live.core().profileLookup();
        } catch (LinkageError olderCore) {
            return null;
        }
    }

    /** A name to show: their nickname or real name, else what Core remembers, else the uuid. */
    private static String nameOf(RanksServices live, OfflinePlayer who) {
        if (who.getName() != null && !who.getName().isBlank()) {
            return PlayerTargets.shownName(who);
        }
        return live.core().identities().nameOf(who.getUniqueId()).orElse(who.getUniqueId().toString());
    }

    private static void tellChange(RanksServices live, CommandSender sender, UUID target, String name,
                                   Rank rank, boolean give, Outcome outcome) {
        Markup tag = tagFor(live, sender, rank);
        switch (outcome) {
            case DONE -> {
                live.messages().send(sender, give ? "ranks.given" : "ranks.taken", "player", name, "rank", tag);
                Player online = live.server().getPlayer(target);
                if (online != null && !online.equals(sender)) {
                    live.messages().send(online, give ? "ranks.given-you" : "ranks.taken-you", "rank", tagFor(live, online, rank));
                }
            }
            case ALREADY -> live.messages().send(sender, "ranks.already-holds", "player", name, "rank", tag);
            case NOT_HELD -> live.messages().send(sender, "ranks.does-not-hold", "player", name, "rank", tag);
            case INHERITED -> live.messages().send(sender, "ranks.inherited", "player", name, "rank", tag,
                    "group", live.ranks().inheritedFrom(target, rank).orElse("?"));
            case GROUP_FAILED -> live.messages().send(sender, "ranks.group-failed", "group", String.join(", ", rank.groups()));
            default -> tellRefusal(live, sender, outcome);
        }
    }

    /** {@code create <id> <tag…>}, {@code edit <id> tag|title|weight|style|team|groups …}, {@code delete <id> confirm}. */
    private static void manage(RanksServices live, CommandSender sender, String what, String[] args) {
        if (!sender.hasPermission(PermissionNodes.ADMIN)) {
            live.messages().send(sender, "ranks.usage");
            return;
        }
        String rest = args.length > 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)) : "";
        Outcome outcome;
        switch (what) {
            case "create" -> {
                if (args.length < 3) {
                    live.messages().send(sender, "ranks.usage-manage");
                    return;
                }
                outcome = live.ranks().create(args[1].toLowerCase(Locale.ROOT),
                        String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)), 50);
            }
            case "delete" -> {
                if (args.length < 2) {
                    live.messages().send(sender, "ranks.usage-manage");
                    return;
                }
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    live.messages().send(sender, "ranks.delete-confirm", "id", args[1].toLowerCase(Locale.ROOT));
                    return;
                }
                outcome = live.ranks().delete(args[1].toLowerCase(Locale.ROOT));
            }
            default -> {
                if (args.length >= 3 && args[2].equalsIgnoreCase("welcome")) {
                    welcome(live, sender, args[1].toLowerCase(Locale.ROOT), java.util.Arrays.copyOfRange(args, 3, args.length));
                    return;
                }
                if (args.length < 4) {
                    live.messages().send(sender, "ranks.usage-manage");
                    return;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                outcome = switch (args[2].toLowerCase(Locale.ROOT)) {
                    case "tag" -> live.ranks().retag(id, rest);
                    case "title" -> live.ranks().rename(id, rest);
                    case "weight" -> {
                        try {
                            yield live.ranks().reweigh(id, Integer.parseInt(args[3]));
                        } catch (NumberFormatException notANumber) {
                            yield Outcome.INVALID;
                        }
                    }
                    case "team" -> live.ranks().team(id, List.of("on", "yes", "true").contains(args[3].toLowerCase(Locale.ROOT)));
                    case "groups", "group" -> live.ranks().link(id, args[3].equalsIgnoreCase("none") ? List.of()
                            : List.of(rest.split("[,\\s]+")));
                    case "style" -> {
                        if (args[3].equalsIgnoreCase("reset")) {
                            yield live.ranks().restyle(id, de.raindancer.core.ui.text.NameStyle.NONE);
                        }
                        var read = de.raindancer.core.ui.text.TypedStyles.read(
                                List.of(java.util.Arrays.copyOfRange(args, 3, args.length)), word -> null);
                        if (read.style().isEmpty()) {
                            live.messages().send(sender, "ranks.style-unreadable", "problem", read.problem());
                            yield null;
                        }
                        yield live.ranks().restyle(id, read.style().get());
                    }
                    default -> {
                        live.messages().send(sender, "ranks.usage-manage");
                        yield null;
                    }
                };
                if (outcome == null) {
                    return;
                }
            }
        }
        switch (outcome) {
            case DONE -> live.messages().send(sender, "ranks.managed");
            case ALREADY -> live.messages().send(sender, "ranks.exists", "id", args[1]);
            case NOT_HELD -> live.messages().send(sender, "ranks.unknown", "name", args[1]);
            case INVALID -> live.messages().send(sender, "ranks.invalid");
            default -> tellRefusal(live, sender, outcome);
        }
    }

    /**
     * {@code edit <id> welcome} lists what the rank says to whoever gets it; {@code welcome <n> <text>} sets line n
     * (one past the last adds one), {@code welcome remove <n>} drops one, {@code welcome clear} says nothing.
     */
    private static void welcome(RanksServices live, CommandSender sender, String id, String[] words) {
        Optional<Rank> rank = live.ranks().rank(id);
        if (rank.isEmpty()) {
            live.messages().send(sender, "ranks.unknown", "name", id);
            return;
        }
        List<String> lines = new java.util.ArrayList<>(live.ranks().welcome(id));
        if (words.length > 0) {
            String first = words[0].toLowerCase(Locale.ROOT);
            Integer number = null;
            try {
                number = Integer.parseInt(first.equals("remove") && words.length > 1 ? words[1] : first);
            } catch (NumberFormatException notANumber) {
                // clear, or a typo: the usage says what works
            }
            if (first.equals("clear")) {
                lines.clear();
            } else if (first.equals("remove") && number != null && number >= 1 && number <= lines.size()) {
                lines.remove(number - 1);
            } else if (!first.equals("remove") && number != null && number >= 1 && number <= lines.size() + 1
                    && words.length > 1) {
                String text = String.join(" ", java.util.Arrays.copyOfRange(words, 1, words.length)).strip();
                if (number <= lines.size()) {
                    lines.set(number - 1, text);
                } else {
                    lines.add(text);
                }
            } else {
                live.messages().send(sender, "ranks.usage-manage");
                return;
            }
            Outcome outcome = live.ranks().rewelcome(id, lines);
            if (outcome == Outcome.INVALID) {
                live.messages().send(sender, "ranks.welcome-invalid", "most", RankService.WELCOME_LINES,
                        "length", RankService.WELCOME_LINE);
                return;
            }
            if (outcome != Outcome.DONE) {
                tellRefusal(live, sender, outcome);
                return;
            }
            live.messages().send(sender, "ranks.managed");
        }
        Markup shown = new Markup(live.ranks().shownTag(rank.get()));
        List<String> now = live.ranks().welcome(id);
        if (now.isEmpty()) {
            live.messages().send(sender, "ranks.welcome-none", "rank", shown);
            return;
        }
        live.messages().send(sender, "ranks.welcome-head", "rank", shown, "id", id);
        for (int index = 0; index < now.size(); index++) {
            live.messages().send(sender, "ranks.welcome-line", "number", index + 1, "line", new Markup(now.get(index)));
        }
    }

    private static void list(RanksServices live, CommandSender sender, String[] args) {
        if (!sender.hasPermission(PermissionNodes.ADMIN)) {
            live.messages().send(sender, "ranks.usage");
            return;
        }
        List<Rank> which;
        if (args.length > 1) {
            Optional<Rank> one = live.ranks().rank(args[1]);
            if (one.isEmpty()) {
                live.messages().send(sender, "ranks.unknown", "name", args[1]);
                return;
            }
            which = List.of(one.get());
        } else {
            which = live.ranks().ranks();
        }
        if (which.isEmpty()) {
            live.messages().send(sender, "ranks.no-ranks");
            return;
        }
        // Members of a rank's groups come from LuckPerms' storage: not on the server thread.
        Scheduling.async(live.plugin(), () -> {
            for (Rank rank : which) {
                List<String> names = live.ranks().everyHolderOf(rank).stream()
                        .map(id -> nameOfHolder(live, id))
                        .sorted(String.CASE_INSENSITIVE_ORDER).toList();
                live.messages().send(sender, "ranks.list-line", "rank", tagFor(live, sender, rank), "id", rank.id(),
                        "count", String.valueOf(names.size()), "players", names.isEmpty() ? "—" : String.join(", ", names),
                        "groups", String.join(", ", rank.groups()));
            }
        });
    }

    private static void info(RanksServices live, CommandSender sender, String who) {
        if (who == null) {
            asPlayer(live, sender).ifPresent(self -> Scheduling.async(live.plugin(),
                    () -> tellInfo(live, sender, self.getUniqueId(), PlayerTargets.shownName(self))));
            return;
        }
        resolve(live, sender, who, (id, name) -> Scheduling.async(live.plugin(), () -> tellInfo(live, sender, id, name)));
    }

    /** Off the server thread: an offline player's groups are loaded first, so what they hold through them shows. */
    private static void tellInfo(RanksServices live, CommandSender sender, UUID target, String name) {
        live.ranks().loadGroups(target);
        List<Rank> held = live.ranks().held(target);
        if (held.isEmpty()) {
            live.messages().send(sender, "ranks.info-none", "player", name);
            return;
        }
        live.messages().send(sender, "ranks.info", "player", name);
        Optional<Rank> worn = live.ranks().worn(target);
        for (Rank rank : held) {
            live.messages().send(sender, worn.filter(rank::equals).isPresent() ? "ranks.info-worn" : "ranks.info-line",
                    "rank", tagFor(live, sender, rank), "title", rank.title());
        }
    }

    /** A holder's name: what Core remembers or Mojang says (remembered from then on), else the start of the uuid. */
    private static String nameOfHolder(RanksServices live, UUID id) {
        Optional<String> known = live.core().identities().nameOf(id);
        if (known.isPresent()) {
            return known.get();
        }
        de.raindancer.core.social.presence.ProfileLookup lookup = profileLookup(live);
        return (lookup == null ? Optional.<String>empty() : lookup.nameOf(id)).orElse(id.toString().substring(0, 8));
    }

    private static void tellRefusal(RanksServices live, CommandSender sender, Outcome outcome) {
        live.messages().send(sender, switch (outcome) {
            case NOT_ALLOWED -> "ranks.choosing-off";
            case NOT_SAVED -> "ranks.not-saved";
            default -> "ranks.usage";
        });
    }

    private static Optional<Player> asPlayer(RanksServices live, CommandSender sender) {
        if (sender instanceof Player player) {
            return Optional.of(player);
        }
        live.messages().send(sender, "ranks.only-a-player");
        return Optional.empty();
    }

    @Override
    public Collection<String> suggest(CommandSourceStack source, String[] args) {
        CommandSender sender = source.getSender();
        RanksServices live = services.get();
        String typed = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        boolean admin = sender.hasPermission(PermissionNodes.ADMIN);
        String first = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        List<String> options = new ArrayList<>();
        if (args.length <= 1) {
            options.addAll(List.of("info", "wear", "show", "style"));
            if (admin) {
                options.addAll(List.of("give", "take", "list", "reload", "manage", "create", "edit", "delete"));
            }
        } else if (args.length == 2 && first.equals("wear") && sender instanceof Player player) {
            live.ranks().held(player.getUniqueId()).forEach(rank -> options.add(rank.id()));
            options.addAll(List.of("none", "auto"));
        } else if (args.length == 2 && first.equals("style") && sender instanceof Player player) {
            live.ranks().worn(player.getUniqueId()).flatMap(shown -> live.ranks().rank(shown.id()))
                    .ifPresent(rank -> options.addAll(rank.styles().keySet()));
            options.add("default");
        } else if (args.length == 2 && first.equals("show")) {
            for (Place place : Place.values()) {
                options.add(place.word());
            }
        } else if (args.length == 3 && first.equals("show")) {
            options.addAll(List.of("on", "off"));
        } else if (admin && args.length == 4 && first.equals("edit") && args[2].equalsIgnoreCase("groups")) {
            options.add("none");
            live.ranks().rank(args[1]).ifPresent(rank -> options.add(String.join(",", rank.groups())));
        } else if (args.length == 2 && (first.equals("info") || (admin && List.of("give", "take").contains(first)))) {
            return PlayerTargets.suggest(live.server(), sender, args[1], viewer -> true);
        } else if (admin && args.length == 3 && first.equals("edit")) {
            options.addAll(List.of("tag", "title", "weight", "style", "team", "groups"));
        } else if (admin && args.length == 4 && first.equals("edit") && args[2].equalsIgnoreCase("team")) {
            options.addAll(List.of("on", "off"));
        } else if (admin && args.length == 3 && first.equals("delete")) {
            options.add("confirm");
        } else if (admin && ((args.length == 3 && List.of("give", "take").contains(first))
                || (args.length == 2 && List.of("list", "edit", "delete").contains(first)))) {
            live.ranks().ranks().forEach(rank -> options.add(rank.id()));
        }
        return options.stream().filter(option -> option.toLowerCase(Locale.ROOT).startsWith(typed)).toList();
    }

    @Override
    public String permission() {
        return PermissionNodes.USE;
    }

    /** The badge for a player, the tag's text for the console — which has no font to draw a badge with. */
    private static Markup tagFor(RanksServices live, CommandSender to, Rank rank) {
        return new Markup(to instanceof org.bukkit.entity.Player ? live.ranks().shownTag(rank) : rank.tag());
    }

}
