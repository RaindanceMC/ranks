package de.raindancer.modules.ranks;

import de.raindancer.core.ui.help.HelpLint;
import de.raindancer.modules.api.ModuleCommand;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class GuideTest {

    @Test
    @DisplayName("the guide pages read, quote real settings and explain every command")
    void theGuideIsComplete() {
        assertThat(HelpLint.of(Path.of("src/main/resources/rains-help"))
                .module("ranks")
                .otherTopics("basics", "guide")
                .settings(RanksSettings.class, RanksSettings.DEFAULTS)
                .commands(new RanksModule().commands().stream().map(ModuleCommand::name).toList())
                .problems()).isEmpty();
    }
}
