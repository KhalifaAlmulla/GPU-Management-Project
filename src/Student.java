/** An approved student account with its own GPU-minute allowance. */
public class Student extends User {
    private static final long serialVersionUID = 1L;
    private final String email;
    private final String phone;
    private final TimeAllowance allowance = new TimeAllowance(100);

    public Student(String id, String name, String email, String phone, String password) {
        super(id, name, password);
        this.email = email;
        this.phone = phone;
    }

    @Override public String getRole() { return "Student"; }
    @Override public boolean canManage(Job job) { return job.getStudentId().equals(getId()); }   // own jobs only
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public TimeAllowance getAllowance() { return allowance; }
}
