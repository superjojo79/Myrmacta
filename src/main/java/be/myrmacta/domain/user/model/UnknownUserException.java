package be.myrmacta.domain.user.model;

/** TODO §113 — voir {@link UserGroup}. */
public class UnknownUserException extends RuntimeException {

    public UnknownUserException(String value) {
        super("Unknown user (never logged in to this instance): " + value);
    }
}
