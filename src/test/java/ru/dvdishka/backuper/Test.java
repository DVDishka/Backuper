package ru.dvdishka.backuper;

import be.seeseemelk.mockbukkit.MockBukkit;
import be.seeseemelk.mockbukkit.ServerMock;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

public class Test {

    private ServerMock server;
    private Backuper plugin;

    @BeforeEach
    public void setUp() {

        server = MockBukkit.mock();
        plugin = MockBukkit.load(Backuper.class);
    }

    @AfterEach
    public void tearDown() {

        MockBukkit.unmock();
    }
}
