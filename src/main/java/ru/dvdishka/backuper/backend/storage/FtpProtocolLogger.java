package ru.dvdishka.backuper.backend.storage;

import org.apache.commons.net.ProtocolCommandEvent;
import org.apache.commons.net.ProtocolCommandListener;

import java.io.Closeable;

class FtpProtocolLogger implements Closeable {

    private final StorageProtocolLogger logger;

    FtpProtocolLogger(String storageId) {
        this.logger = new StorageProtocolLogger(storageId);
    }

    ProtocolCommandListener createListener(String clientRole) {
        return new Listener(clientRole);
    }

    synchronized void logLifecycle(String clientRole, String message) {
        logger.log(clientRole, "INFO", message);
    }

    @Override
    public synchronized void close() {
        logger.close();
    }

    private class Listener implements ProtocolCommandListener {

        private final String clientRole;

        private Listener(String clientRole) {
            this.clientRole = clientRole;
        }

        @Override
        public void protocolCommandSent(ProtocolCommandEvent event) {
            String command = event.getCommand();
            if ("PASS".equalsIgnoreCase(command)) {
                logger.log(clientRole, "SEND", "PASS ***");
                return;
            }
            logger.log(clientRole, "SEND", event.getMessage());
        }

        @Override
        public void protocolReplyReceived(ProtocolCommandEvent event) {
            logger.log(clientRole, "REPLY", event.getMessage());
        }
    }
}
