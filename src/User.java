import java.io.Serializable;

/** Parent class for all application users. */
public abstract class User implements Identifiable, Serializable {
    private static final long serialVersionUID = 1L;
    private final String id;
    private final String name;
    private final String password;

    protected User(String id, String name, String password) {
        this.id = id;
        this.name = name;
        this.password = password;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public boolean passwordMatches(String attempt) { return password.equals(attempt); }
    public abstract String getRole();
    /** Polymorphism: each kind of user decides which jobs it may cancel. */
    public abstract boolean canManage(Job job);
    public String displayName() { return getRole() + ": " + name; }
}
