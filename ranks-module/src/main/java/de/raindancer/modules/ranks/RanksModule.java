package de.raindancer.modules.ranks;

import de.raindancer.core.data.settings.SettingsStore;
import de.raindancer.core.data.store.YamlStore;
import de.raindancer.core.ui.identity.NameBadges;
import de.raindancer.core.ui.identity.RankTitles;
import de.raindancer.core.ui.tablist.TablistModel;
import de.raindancer.modules.api.FlexModule;
import de.raindancer.modules.api.ModuleCommand;
import de.raindancer.modules.api.ModuleContext;
import de.raindancer.modules.api.ModuleInfo;
import de.raindancer.modules.ranks.service.RankService;
import de.raindancer.modules.ranks.store.HolderBook;
import de.raindancer.modules.ranks.store.RankCatalogue;
import de.raindancer.modules.ranks.util.PermissionNodes;

import java.util.List;

/**
 * Ranks in front of names. Drawing them is Core's: the rank is a {@link NameBadges} badge asked for per place,
 * so chat, the nametag and the tablist all show it without this module knowing how any of them is drawn — and
 * without taking the one prefix slot, which stays free for whoever else writes one.
 */
public final class RanksModule implements FlexModule {

    public static final String BADGE_OWNER = "ranks";

    private static final ModuleInfo INFO = ModuleInfo.of("ranks", "Ranks", "0.19.0")
            .describedAs("Ranks like [OWNER], [DEV] or [ADMIN] in front of a name: in chat, above the head and in the tablist, switchable for the server and by every player")
            .by("Raindancer118");

    @Override
    public ModuleInfo info() {
        return INFO;
    }

    @Override
    public void enable(ModuleContext context) {
        SettingsStore<RanksSettings> settings = context.settings(RanksSettings.class, RanksSettings.DEFAULTS);
        context.core().messages().defineFrom(RanksModule.class.getResourceAsStream("messages.yml"),
                context.chat().brand()::chatPrefix);
        int registered = PermissionNodes.register(context.plugin().getServer());
        if (registered > 0) {
            context.log().info("{} permission(s) registered.", registered);
        }

        RankCatalogue catalogue = new RankCatalogue(YamlStore.network(context.dataFolder().resolve("ranks.yml")),
                () -> RanksModule.class.getResourceAsStream("ranks.yml"),
                YamlStore.network(context.dataFolder().resolve("custom-ranks.yml")));
        int ranks = catalogue.reload();
        context.closeWith(catalogue.watch());
        catalogue.problems().forEach(problem -> context.log().warn("ranks.yml: {}", problem));
        catalogue.merged().forEach(change -> context.log().info("ranks.yml: {} from the shipped ranks.", change));
        HolderBook holders = new HolderBook(YamlStore.network(context.dataFolder().resolve("holders.yml")));
        holders.load();
        context.closeWith(holders.watch());
        if (!holders.readable()) {
            context.log().error("holders.yml could not be read. No rank can be given or changed until it is "
                    + "fixed. Saving now would replace everybody's ranks.");
        }

        RankService service = new RankService(catalogue, holders, settings.current());
        settings.onChange(service::settings);
        // Online players only: their groups are in memory, an offline one would mean a storage read in a render.
        var groups = context.core().groups();
        var server = context.plugin().getServer();
        // Offline players' groups are loaded off the server thread the first time they are drawn (statues).
        service.linkTo(groups, player -> server.getPlayer(player) != null,
                task -> de.raindancer.core.platform.util.Scheduling.async(context.plugin(), task));
        var welcome = new de.raindancer.modules.ranks.listener.WelcomeListener(context.plugin(), service,
                new de.raindancer.modules.ranks.service.RankWelcome(service, catalogue,
                        new de.raindancer.modules.ranks.store.WelcomedBook(
                                YamlStore.network(context.dataFolder().resolve("welcomed.yml")))),
                context.core().messages());
        context.listener(welcome);
        context.closeWith(() -> service.onGiven(null));
        // Statues are Core's (2.106.0), placed with /eco statue place creator|team; here who stands on them, each under
        // their own rank's badge (Core 2.114.0). The team board leaves the creators to their own.
        var creators = new de.raindancer.modules.ranks.service.RankStatues(
                de.raindancer.modules.ranks.service.RankStatues.CREATOR,
                "<light_purple><bold>Our creators</bold></light_purple>", "creators",
                service.rank("creator").map(de.raindancer.modules.ranks.model.Rank::tag)
                        .orElse("<light_purple>[CREATOR]</light_purple>"),
                () -> service.rank("creator").stream().toList(), service::everyHolderOf, service::drawn);
        var team = new de.raindancer.modules.ranks.service.RankStatues(
                de.raindancer.modules.ranks.service.RankStatues.TEAM,
                "<gold><bold>Our team</bold></gold>", "team members",
                service.rank("team").map(de.raindancer.modules.ranks.model.Rank::tag)
                        .orElse("<light_purple><bold>[TEAM]</bold></light_purple>"),
                () -> service.ranks().stream().filter(rank -> rank.team() && !rank.id().equals("creator")).toList(),
                service::everyHolderOf, service::drawn);
        // Off the server thread; a rank given or taken redraws the statues right away instead of on the next round.
        Runnable lookAgain = () -> {
            creators.refresh();
            team.refresh();
            de.raindancer.core.ui.statue.Statues.installed().ifPresent(de.raindancer.core.ui.statue.Statues::refresh);
        };
        for (var statues : java.util.List.of(creators, team)) {
            var board = statues.board(id -> de.raindancer.core.platform.command.PlayerTargets.nameOf(server, id));
            de.raindancer.core.ui.statue.StatueBoards.offer(context.plugin(), board, statues::refresh);
            context.closeWith(() -> de.raindancer.core.ui.statue.StatueBoards.withdraw(board));
        }
        service.onTaken(player -> de.raindancer.core.platform.util.Scheduling.async(context.plugin(), lookAgain));
        context.closeWith(() -> service.onTaken(null));
        var statuesLook = de.raindancer.core.platform.util.Scheduling.asyncTimer(context.plugin(), 1, 60,
                task -> lookAgain.run());
        if (statuesLook != null) {
            context.closeWith(statuesLook::cancel);
        }
        service.onGiven(player -> {
            welcome.check(player, 1);
            de.raindancer.core.platform.util.Scheduling.async(context.plugin(), lookAgain);
        });
        var groupWatch = groups.onChange(player -> {
            de.raindancer.core.platform.util.Scheduling.async(context.plugin(), lookAgain);
            service.groupsChanged(player);
            if (player != null) {
                welcome.check(player, 2);
            }
        });
        context.closeWith(groupWatch::close);
        context.closeWith(service::unlink);

        service.badges(tag -> context.core().badges().register(tag), () -> context.core().badgesShown());
        NameBadges.registerPlaced(BADGE_OWNER, service::badge);
        context.closeWith(() -> NameBadges.unregister(BADGE_OWNER));
        // Core 2.77.0: the Discord card names the rank somebody wears. The question comes off the server thread.
        try {
            RankTitles.Source titles = player -> {
                service.loadGroups(player);
                return service.worn(player).map(rank -> rank.title());
            };
            RankTitles.provide(titles);
            context.closeWith(() -> RankTitles.withdraw(titles));
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is older than 2.77.0: other plugins cannot ask for the rank title.");
        }
        TablistModel tablist = context.core().tablists().model();
        tablist.rankOf(entry -> service.sortWeight(entry.player()));
        context.closeWith(() -> tablist.rankOf(null));
        tablist.sectionOf(entry -> service.section(entry.player()));
        context.closeWith(() -> tablist.sectionOf(null));

        // Screens for /screen — Core 2.10.0; on an older Core there are simply none.
        try {
            var screens = context.core().screens();
            if (screens != null) {
                new de.raindancer.modules.ranks.service.RankScreens(service, server,
                        id -> context.core().vanish().isVanished(id)).register(screens, context.plugin());
                context.closeWith(() -> de.raindancer.modules.ranks.service.RankScreens.KINDS
                        .forEach(screens::unregister));
            }
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is older than 2.10.0: no rank screens until it is updated.");
        }

        RanksServices services = new RanksServices(context.plugin(), context.plugin().getServer(), context.core(),
                context.log(), context.core().messages(), context.chat().brand(), service);
        RanksCommands.ready(services);
        try {
            var offered = RanksPreferences.all(() -> services);
            offered.forEach(each -> de.raindancer.core.ui.preference.Preferences.offer(context.plugin(), each));
            context.closeWith(() -> offered.forEach(de.raindancer.core.ui.preference.Preferences::withdraw));
        } catch (LinkageError olderCore) {
            context.log().info("RainsCore is older than 2.103.0: ranks are not on /preferences.");
        }
        context.log().info("Ranks are up: {} rank(s), {} player(s) hold one.", ranks,
                holders.all().values().stream().filter(holder -> !holder.ranks().isEmpty()).count());
    }

    @Override
    public List<ModuleCommand> commands() {
        return RanksCommands.declared();
    }

    @Override
    public void disable() {
        RanksCommands.stopped();
    }
}
