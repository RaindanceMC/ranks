package de.raindancer.modules.ranks.store;

import de.raindancer.core.data.store.YamlStore;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Which ranks each player was already told the welcome of, in {@code welcomed.yml} — one document for the network,
 * so a rank is told once, not once per server. Read and written only when somebody joins or gets a rank: blocks,
 * never on the server thread.
 */
public final class WelcomedBook {

    private final YamlStore store;

    public WelcomedBook(YamlStore store) {
        this.store = store;
    }

    /** What they were told. A file that cannot be read counts as told everything: nobody is told twice over it. */
    public Set<String> told(UUID player) {
        return store.readIfReadable().map(yaml -> Set.copyOf(yaml.getStringList("told." + player)))
                .orElse(null);
    }

    /** @return false when it could not be written */
    public boolean mark(UUID player, Collection<String> ranks) {
        return store.update(yaml -> {
            List<String> now = new ArrayList<>(yaml.getStringList("told." + player));
            ranks.stream().filter(id -> !now.contains(id)).forEach(now::add);
            yaml.set("told." + player, now);
        });
    }
}
