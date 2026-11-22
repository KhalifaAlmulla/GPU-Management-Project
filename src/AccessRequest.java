import java.io.Serializable;

/** Registration awaiting review, or retained as an approval/rejection record. */
public class AccessRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private final String id, name, email, phone, password;
    private String status = "Pending";

    public AccessRequest(String id, String name, String email, String phone, String password) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.password = password;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public String getPassword() { return password; }
    public String getStatus() { return status; }
    public void approve() {
        if (!status.equals("Pending")) throw new IllegalStateException("Request already reviewed.");
        status = "Approved";
    }
    public void reject() {
        if (!status.equals("Pending")) throw new IllegalStateException("Request already reviewed.");
        status = "Rejected";
    }
}
