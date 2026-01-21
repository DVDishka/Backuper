package ru.dvdishka.backuper;

import dev.jorel.commandapi.MockCommandAPIPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Answers;
import org.mockito.MockedConstruction;
import org.mockito.Mockito;
import ru.dvdishka.backuper.backend.Bstats;
import ru.dvdishka.backuper.backend.ScheduleManager;
import ru.dvdishka.backuper.handlers.commands.reload.ReloadCommand;

import java.io.File;
import java.io.IOException;

public class BaseTest {

    protected ServerMock server;

    protected File configFile = new File("plugins/Backuper/config.yml");
    protected FileConfiguration config;

    private MockedConstruction<Bstats> mockBstats;
    private MockedConstruction<ScheduleManager> mockScheduleManager;

    @BeforeEach
    public void setup() throws IOException {
        try {
            configFile.delete();
        } catch (Exception ignored) {
        } // No problem if a file doesn't exist

        mockBstats = Mockito.mockConstruction(Bstats.class);

        server = MockBukkit.mock();
        MockCommandAPIPlugin.load();

        ScheduleManager scheduleManager = new ScheduleManager();
        mockScheduleManager = Mockito.mockConstruction(ScheduleManager.class, Mockito.withSettings().spiedInstance(scheduleManager).defaultAnswer(Answers.CALLS_REAL_METHODS), (mock, context) -> {
            Mockito.doAnswer(invocation -> {
                Bukkit.getScheduler().runTaskLater((Plugin) invocation.getArgument(0), (Runnable) invocation.getArgument(1), (Long) invocation.getArgument(2));
                return null;
            }).when(mock).runGlobalRegionDelayed(Mockito.any(), Mockito.any(), Mockito.anyLong());
            Mockito.doAnswer(invocation -> {
                Bukkit.getScheduler().scheduleSyncRepeatingTask((Plugin) invocation.getArgument(0), (Runnable) invocation.getArgument(1), (Long) invocation.getArgument(2), (Long) invocation.getArgument(3));
                return null;
            }).when(mock).runGlobalRegionRepeatingTask(Mockito.any(), Mockito.any(), Mockito.anyLong(), Mockito.anyLong());
        });

        MockBukkit.load(Backuper.class);
        config = YamlConfiguration.loadConfiguration(configFile);

        config.set("server.betterLogging", true);
        reload();
    }

    @AfterEach
    public void tearDown() {
        mockBstats.close();
        mockScheduleManager.close();
        MockBukkit.unmock();
    }

    /***
     * reloads the plugin (like /backuper reload command) but in the current thread and updates config field
     */
    protected void reload() throws IOException {
        config.save(configFile);
        new ReloadCommand(server.getConsoleSender(), null).execute(); // We should be aware of using commands directly because they are executed asynchronously
        config = YamlConfiguration.loadConfiguration(configFile);
    }
}
