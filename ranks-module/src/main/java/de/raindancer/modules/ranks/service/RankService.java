package de.raindancer.modules.ranks.service;

import de.raindancer.core.platform.permission.PermissionGroups;
import de.raindancer.core.ui.identity.NameBadges;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.ranks.RanksSettings;
import de.raindancer.modules.ranks.model.Holder;
import de.raindancer.modules.ranks.model.Place;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.rules.BadgeRule;
import de.raindancer.modules.ranks.store.HolderBook;
import de.raindancer.modules.ranks.store.RankCatalogue;
import net.kyori.adventure.text.Component;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

/** Giving and taking ranks, what a player wears, and the badge Core draws in front of their name. */
public final class RankService implements IRanksService {

    /** INHERITED: held through a group that inherits the rank's group; GROUP_FAILED: LuckPerms did not take the change. */
    public enum Outcome { DONE, ALREADY, NOT_HELD, NOT_ALLOWED, NOT_SAVED, INVALID, INHERITED, GROUP_FAILED }

    private final RankCatalogue catalogue;
    private final HolderBook book;
    private final BadgeRule rule = new BadgeRule();
    /** Parsed tags by their markup, so a chat line or a tablist pass parses nothing. */
    private final Map<String, Component> parsed = new ConcurrentHashMap<>();
    private volatile RanksSettings settings;
    private volatile PermissionGroups groups = PermissionGroups.NONE;
    private volatile Predicate<UUID> loaded = player -> false;

    public RankService(RankCatalogue catalogue, HolderBook book, RanksSettings settings) {
        this.catalogue = catalogue;
        this.book = book;
        settings(settings);
    }

    @Override
    public void settings(RanksSettings fresh) {
        this.settings = fresh;
    }

    public RanksSettings settings() {
        return settings;
    }

    /**
     * Ties ranks to permission (LuckPerms) groups. Being in a rank's group gives the rank, on top of what
     * holders.yml says; with editable groups, {@link #give} and {@link #take} change the membership instead of
     * holders.yml, so a staff rank set in LuckPerms, by /promote or by /rank give is one and the same.
     *
     * @param loaded whose groups may be asked for while drawing a badge — the online, whose groups are in memory
     */
    public void linkTo(PermissionGroups permissionGroups, Predicate<UUID> loaded) {
        this.groups = permissionGroups == null ? PermissionGroups.NONE : permissionGroups;
        this.loaded = loaded == null ? player -> false : loaded;
    }

    public void unlink() {
        linkTo(null, null);
    }

    /** Their stored holder with the ranks their groups give added in. */
    private Optional<Holder> effective(UUID player) {
        Optional<Holder> stored = book.find(player);
        Set<String> through = player == null || !loaded.test(player) ? Set.of() : throughGroups(groups.groupsOf(player));
        if (through.isEmpty()) {
            return stored;
        }
        Set<String> all = new LinkedHashSet<>(stored.map(Holder::ranks).orElse(Set.of()));
        if (!all.addAll(through)) {
            return stored;
        }
        return Optional.of(stored.orElse(Holder.fresh(player)).withRanks(all));
    }

    /** The ids of the ranks these groups give. */
    private Set<String> throughGroups(Set<String> inGroups) {
        if (inGroups.isEmpty()) {
            return Set.of();
        }
        Set<String> ids = new LinkedHashSet<>();
        for (Rank rank : catalogue.byId().values()) {
            if (rank.groups().stream().anyMatch(inGroups::contains)) {
                ids.add(rank.id());
            }
        }
        return ids;
    }

    /** May load an offline player from LuckPerms: never on the server thread. */
    private boolean inGroupOf(UUID player, Rank rank) {
        Set<String> all = groups.groupsOf(player);
        return rank.groups().stream().anyMatch(all::contains);
    }

    /**
     * When they hold the rank only because one of their groups inherits its group: those groups, to be named in
     * the refusal. Blocks like {@link #take}.
     */
    public Optional<String> inheritedFrom(UUID player, Rank rank) {
        if (!inGroupOf(player, rank)) {
            return Optional.empty();
        }
        List<String> parents = groups.directGroupsOf(player).stream()
                .filter(group -> !rank.groups().contains(group)).sorted().toList();
        return parents.isEmpty() ? Optional.empty() : Optional.of(String.join(", ", parents));
    }

    private Set<String> ranksOf(UUID player) {
        return effective(player).map(Holder::ranks).orElse(Set.of());
    }

    // ------------------------------------------------------------------------------- reading

    /** What Core draws in front of a name; safe from any thread. */
    public Optional<Component> badge(UUID player, NameBadges.Place where) {
        Optional<Place> place = Place.of(where);
        if (place.isEmpty()) {
            return Optional.empty();
        }
        return shown(player, place.get()).map(rank -> parsed.computeIfAbsent(rank.tag(),
                tag -> Text.styleOnly().deserialize(tag)));
    }

    public Optional<Rank> shown(UUID player, Place place) {
        Optional<Holder> holder = effective(player);
        return holder.isEmpty() ? Optional.empty()
                : rule.shown(holder.get(), catalogue.byId(), settings, place);
    }

    /** The rank they wear wherever it shows — what /rank and the menu call "your rank". */
    public Optional<Rank> worn(UUID player) {
        return effective(player).flatMap(holder -> rule.worn(holder, catalogue.byId(), settings.playersChoose()));
    }

    /** Their ranks that still exist in ranks.yml, most senior first. */
    public List<Rank> held(UUID player) {
        Map<String, Rank> known = catalogue.byId();
        return ranksOf(player).stream().map(known::get).filter(rank -> rank != null)
                .sorted(Comparator.comparingInt(Rank::weight).reversed().thenComparing(Rank::id)).toList();
    }

    public Holder holder(UUID player) {
        return book.of(player);
    }

    /** The tablist section somebody belongs in — the team — or empty. Safe from any thread. */
    public Optional<String> section(UUID player) {
        RanksSettings now = settings;
        if (!now.teamSection() || player == null) {
            return Optional.empty();
        }
        Map<String, Rank> known = catalogue.byId();
        boolean team = ranksOf(player).stream().map(known::get).anyMatch(rank -> rank != null && rank.team());
        String name = now.teamName() == null || now.teamName().isBlank() ? "Team" : now.teamName().strip();
        return team ? Optional.of(name) : Optional.empty();
    }

    public int sortWeight(UUID player) {
        return effective(player).map(holder -> rule.sortWeight(holder, catalogue.byId(), settings)).orElse(0);
    }

    public List<Rank> ranks() {
        return catalogue.all();
    }

    public Optional<Rank> rank(String id) {
        return catalogue.find(id);
    }

    /** Everybody holding this rank in holders.yml — quick, for a menu; {@link #everyHolderOf} adds its groups. */
    public List<UUID> holdersOf(Rank rank) {
        return book.all().values().stream().filter(holder -> holder.ranks().contains(rank.id()))
                .map(Holder::player).toList();
    }

    /**
     * Everybody holding this rank: holders.yml and whoever is directly in one of its groups (somebody holding it only
     * through an inheriting group is not listed). Asks LuckPerms' storage: never on the server thread.
     */
    public Set<UUID> everyHolderOf(Rank rank) {
        Set<UUID> all = new LinkedHashSet<>(holdersOf(rank));
        PermissionGroups now = groups;
        rank.groups().forEach(group -> all.addAll(now.membersOf(group)));
        return all;
    }

    public boolean readable() {
        return book.readable();
    }

    // ------------------------------------------------------------------------------- staff

    /**
     * Gives the rank: into its first group that exists when the groups are editable, into holders.yml otherwise.
     * May load an offline player from LuckPerms: never on the server thread.
     */
    public Outcome give(UUID player, Rank rank) {
        if (book.of(player).ranks().contains(rank.id()) || inGroupOf(player, rank)) {
            return Outcome.ALREADY;
        }
        PermissionGroups now = groups;
        if (now.editable()) {
            Optional<String> group = rank.groups().stream().filter(now::exists).findFirst();
            if (group.isPresent()) {
                return now.join(player, group.get()) ? Outcome.DONE : Outcome.GROUP_FAILED;
            }
        }
        return book.change(player, holder -> {
            Set<String> more = new LinkedHashSet<>(holder.ranks());
            more.add(rank.id());
            return holder.withRanks(more);
        }) ? Outcome.DONE : Outcome.NOT_SAVED;
    }

    /**
     * Takes the rank out of holders.yml and them out of its groups. Still holding it through a group that inherits
     * the rank's is {@link Outcome#INHERITED} — that group is LuckPerms' to change. Blocks like {@link #give}.
     */
    public Outcome take(UUID player, Rank rank) {
        PermissionGroups now = groups;
        boolean stored = book.of(player).ranks().contains(rank.id());
        List<String> direct = now.editable()
                ? now.directGroupsOf(player).stream().filter(rank.groups()::contains).toList() : List.of();
        if (!stored && direct.isEmpty()) {
            return inheritedFrom(player, rank).isPresent() ? Outcome.INHERITED : Outcome.NOT_HELD;
        }
        for (String group : direct) {
            now.leave(player, group);
        }
        if (now.directGroupsOf(player).stream().anyMatch(rank.groups()::contains)) {
            return Outcome.GROUP_FAILED;
        }
        boolean wornIt = book.of(player).worn().equals(rank.id());
        if ((stored || wornIt) && !book.change(player, holder -> {
            Set<String> fewer = new LinkedHashSet<>(holder.ranks());
            fewer.remove(rank.id());
            Holder changed = holder.withRanks(fewer);
            return changed.worn().equals(rank.id()) ? changed.wearing("") : changed;
        })) {
            return Outcome.NOT_SAVED;
        }
        return inheritedFrom(player, rank).isPresent() ? Outcome.INHERITED : Outcome.DONE;
    }

    // ------------------------------------------------------------------------------- the player's own

    /**
     * Which of their ranks they show: a rank id they hold, {@link Holder#HIDDEN} for none, or empty for the most
     * senior.
     */
    public Outcome wear(UUID player, String id) {
        if (!settings.playersChoose()) {
            return Outcome.NOT_ALLOWED;
        }
        String wanted = id == null ? "" : id;
        if (!wanted.isEmpty() && !wanted.equals(Holder.HIDDEN) && !ranksOf(player).contains(wanted)) {
            return Outcome.NOT_HELD;
        }
        if (book.of(player).worn().equals(wanted)) {
            return Outcome.ALREADY;
        }
        return book.change(player, holder -> holder.wearing(wanted)) ? Outcome.DONE : Outcome.NOT_SAVED;
    }

    /** Switches their own rank in one place. */
    public Outcome show(UUID player, Place place, boolean on) {
        if (!settings.playersChoose()) {
            return Outcome.NOT_ALLOWED;
        }
        if (book.of(player).shows(place) == on) {
            return Outcome.ALREADY;
        }
        return book.change(player, holder -> holder.showing(place, on)) ? Outcome.DONE : Outcome.NOT_SAVED;
    }

    // ------------------------------------------------------------------------------- making ranks in game

    /**
     * A new rank. {@code tag} is what shows — plain text gets square brackets if it has none, markup is kept as
     * written. Saved to custom-ranks.yml.
     */
    public Outcome create(String id, String tag, int weight) {
        if (!RankCatalogue.isId(id) || weight < 1 || weight > 99) {
            return Outcome.INVALID;
        }
        if (catalogue.find(id).isPresent()) {
            return Outcome.ALREADY;
        }
        String shown = bracketed(tag);
        if (RankCatalogue.tagProblem(shown).isPresent()) {
            return Outcome.INVALID;
        }
        return saved(catalogue.define(new Rank(id, id.substring(0, 1).toUpperCase(java.util.Locale.ROOT) + id.substring(1),
                shown, weight, List.of())));
    }

    /** What the rank shows, as typed — this replaces its style too. */
    public Outcome retag(String id, String tag) {
        String shown = bracketed(tag);
        if (RankCatalogue.tagProblem(shown).isPresent()) {
            return Outcome.INVALID;
        }
        return change(id, rank -> rank.withTag(shown));
    }

    /** Paints the rank's tag — same letters, a colour, a gradient, bold. NONE leaves it plain white. */
    public Outcome restyle(String id, de.raindancer.core.ui.text.NameStyle style) {
        return change(id, rank -> {
            String letters = Text.plain(Text.styleOnly().deserialize(rank.tag()));
            Component painted = style == null || style.isEmpty() ? Component.text(letters)
                    : de.raindancer.core.ui.text.Gradients.styled(letters, style);
            return rank.withTag(net.kyori.adventure.text.minimessage.MiniMessage.miniMessage().serialize(painted));
        });
    }

    public Outcome rename(String id, String title) {
        if (title == null || title.isBlank() || title.length() > 32) {
            return Outcome.INVALID;
        }
        return change(id, rank -> rank.withTitle(title.strip()));
    }

    public Outcome reweigh(String id, int weight) {
        if (weight < 1 || weight > 99) {
            return Outcome.INVALID;
        }
        return change(id, rank -> rank.withWeight(weight));
    }

    /** The permission groups that give the rank; none goes back to the group named like it. */
    public Outcome link(String id, List<String> groupNames) {
        List<String> named = groupNames == null ? List.of() : groupNames;
        if (!named.stream().allMatch(RankCatalogue::isGroup)) {
            return Outcome.INVALID;
        }
        return change(id, rank -> rank.withGroups(named));
    }

    /** Whether holding the rank puts somebody in the server's team. */
    public Outcome team(String id, boolean onTeam) {
        return change(id, rank -> rank.withTeam(onTeam));
    }

    /** Deletes the rank everywhere; whoever held it simply shows nothing for it. */
    public Outcome delete(String id) {
        if (catalogue.find(id).isEmpty()) {
            return Outcome.NOT_HELD;
        }
        Outcome outcome = saved(catalogue.remove(id));
        parsed.clear();
        return outcome;
    }

    private Outcome change(String id, java.util.function.UnaryOperator<Rank> how) {
        Optional<Rank> rank = catalogue.find(id);
        if (rank.isEmpty()) {
            return Outcome.NOT_HELD;
        }
        Rank changed = how.apply(rank.get());
        if (RankCatalogue.tagProblem(changed.tag()).isPresent()) {
            return Outcome.INVALID;
        }
        return saved(catalogue.define(changed));
    }

    private Outcome saved(boolean saved) {
        return saved ? Outcome.DONE : Outcome.NOT_SAVED;
    }

    private static String bracketed(String typed) {
        String tag = typed == null ? "" : typed.strip();
        if (tag.isEmpty() || tag.contains("<") || tag.startsWith("[")) {
            return tag;
        }
        return "[" + tag + "]";
    }

    /** Reads ranks.yml again. @return how many ranks */
    public int reload() {
        int count = catalogue.reload();
        parsed.clear();
        return count;
    }

    public List<String> problems() {
        return catalogue.problems();
    }
}
