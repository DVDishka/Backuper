package ru.dvdishka.backuper.common;

import java.util.Arrays;

public class Logger {

    public static Logger getLogger() {
        return new Logger();
    }

    public void log(String text) {
        Common.plugin.getLogger().info(text);
    }

    public void devLog(String text) {
        if (ConfigVariables.betterLogging) {
            Common.plugin.getLogger().info(text);
        }
    }

    public void warn(String text) {
        Common.plugin.getLogger().warning(text);
    }

    public void devWarn(Object sourceClass, String text) {
        if (ConfigVariables.betterLogging) {
            Common.plugin.getLogger().warning(sourceClass.getClass().getSimpleName() + ": " + text);
        }
    }

    public void devWarn(String sourceClassName, String text) {
        if (ConfigVariables.betterLogging) {
            Common.plugin.getLogger().warning(sourceClassName + ": " + text);
        }
    }

    public void devWarn(Object sourceClass, Exception exception) {
        if (ConfigVariables.betterLogging) {
            Common.plugin.getLogger().warning(sourceClass.getClass().getSimpleName() + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
        }
    }

    public void devWarn(String sourceClassName, Exception exception) {
        if (ConfigVariables.betterLogging) {
            Common.plugin.getLogger().warning(sourceClassName + ": " + exception.getMessage() + "\n" + Arrays.toString(exception.getStackTrace()));
        }
    }
}
