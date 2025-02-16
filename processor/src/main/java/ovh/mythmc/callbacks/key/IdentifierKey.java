package ovh.mythmc.callbacks.key;

import java.util.Comparator;
import java.util.Objects;

import org.jetbrains.annotations.NotNull;

public final class IdentifierKey implements Comparable<IdentifierKey> {

    static final Comparator<? super IdentifierKey> COMPARATOR = Comparator.comparing(IdentifierKey::identifier).thenComparing(IdentifierKey::group);

    private final String group;

    private final String identifier;

    private IdentifierKey(String group, String identifier) {
        this.group = group;
        this.identifier = identifier;
    }

    public String group() { return group; }

    public String identifier() { return identifier; }

    @Override
    public boolean equals(final Object other) {
        if (this == other) return true;
        if (!(other instanceof IdentifierKey)) return false;
        final IdentifierKey that = (IdentifierKey) other;
        return Objects.equals(this.identifier, that.identifier()) && Objects.equals(this.group, that.group());
    }

    @Override
    public int hashCode() {
        int result = this.identifier.hashCode();
        result = (31 * result) + this.group.hashCode();
        return result;
    }

    public @NotNull String asString() {
        return asString(this.group, this.identifier);
    }

    private static @NotNull String asString(final @NotNull String group, final @NotNull String identifier) {
        return group + ':' + identifier;
    }

    @Override
    public @NotNull String toString() {
        return this.asString();
    }

    public static IdentifierKey of(String group, String identifier) {
        return new IdentifierKey(group, identifier);
    }

    public static IdentifierKey of(String key) {
        if (!isValid(key))
            return null;

        String group = key.substring(0, key.indexOf(":"));
        String identifier = key.substring(key.indexOf(":") + 1, key.length());

        return of(group, identifier);
    }

    public static boolean isValid(String key) {
        if (!key.contains(":"))
            return false;

        return true;
    }

    @Override
    public int compareTo(IdentifierKey that) {
        return COMPARATOR.compare(this, that);
    }
    
}
