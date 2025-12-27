package ru.dvdishka.backuper.config;

import dev.jorel.commandapi.MockCommandAPIPlugin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockito.Mockito;
import ru.dvdishka.backuper.Backuper;
import ru.dvdishka.backuper.backend.Bstats;

import java.io.File;

public class BaseTest {

    protected ServerMock server;

    protected File configFile = new File("plugins/Backuper/config.yml");

    private org.mockito.MockedConstruction<Bstats> mockBstats;

    @BeforeEach
    public void setup() {
        mockBstats = Mockito.mockConstruction(Bstats.class);
        try {
            configFile.delete();
        } catch (Exception ignored) {
        } // No problem if a file doesn't exist
        server = MockBukkit.mock();

        MockCommandAPIPlugin.load();
        MockBukkit.load(Backuper.class);
    }

    @AfterEach
    public void tearDown() {
        mockBstats.close();
        MockBukkit.unmock();
    }
}
