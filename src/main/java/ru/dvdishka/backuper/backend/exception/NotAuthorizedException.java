package ru.dvdishka.backuper.backend.exception;

public class NotAuthorizedException extends Exception {

    private final String authServiceId;

    public NotAuthorizedException(String authServiceId) {
        super("The user is not authorized via " + authServiceId);
        this.authServiceId = authServiceId;
    }

    public String getAuthServiceId() {
        return this.authServiceId;
    }
}
