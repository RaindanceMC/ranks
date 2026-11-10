package de.raindancer.modules.ranks.listener;

import de.raindancer.core.platform.util.Scheduling;
import de.raindancer.core.ui.messages.Messages;
import de.raindancer.core.ui.text.Text;
import de.raindancer.modules.ranks.model.Rank;
import de.raindancer.modules.ranks.service.RankService;
import de.raindancer.modules.ranks.service.RankWelcome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.Plugin;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/** Tells a rank's welcome when somebody joins holding it, or gets it while online. */
public final class WelcomeListener implements Listener {

    private final Plugin plugin;
    private final RankService ranks;
    private final RankWelcome welcome;
    private final Messages messages;

    public WelcomeListener(Plugin plugin, RankService ranks, RankWelcome welcome, Messages messages) {
        this.plugin = plugin;
        this.ranks = ranks;
        this.welcome = welcome;
        this.messages = messages;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        // After the join messages and the server's own greeting, so it is not scrolled away at once.
        check(event.getPlayer().getUniqueId(), 4);
    }

    /** Looks again shortly: LuckPerms tells a change before every server has it. Off the server thread. */
    public void check(UUID player, long seconds) {
        Scheduling.asyncLater(plugin, seconds, TimeUnit.SECONDS, () -> {
            Player online = plugin.getServer().getPlayer(player);
            if (online == null) {
                return;
            }
            for (Rank rank : welcome.takeDue(player)) {
                messages.send(online, "ranks.welcome.header", "rank", ranks.tagOf(rank));
                for (String line : welcome.lines(rank)) {
                    messages.sendPlain(online, "ranks.welcome.line", "line", Text.styled(line));
                }
            }
        });
    }
}
