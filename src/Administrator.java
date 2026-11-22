/** One predefined administrator account, as required by the assignment. */
public class Administrator extends User {
    private static final long serialVersionUID = 1L;
    public static final String USERNAME = "admin";
    public static final String PASSWORD = "gpu2026";
    public Administrator() { super(USERNAME, "Administrator", PASSWORD); }
    @Override public String getRole() { return "Administrator"; }
    @Override public boolean canManage(Job job) { return true; }   // admin may cancel any job
}
