package be.myrmacta.domain.user.model;

/** TODO §113 — voir {@link UserGroup}. */
public class UserGroupNotFoundException extends RuntimeException {

    public UserGroupNotFoundException(String value) {
        super("No user group with code: " + value);
    }
}
