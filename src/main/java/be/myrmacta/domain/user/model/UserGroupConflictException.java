package be.myrmacta.domain.user.model;

/** TODO §113 — voir {@link UserGroup}. */
public class UserGroupConflictException extends RuntimeException {

    public UserGroupConflictException(String value) {
        super("A user group already exists with code: " + value);
    }
}
