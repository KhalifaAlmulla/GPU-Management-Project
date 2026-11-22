import javax.swing.*;
import java.awt.*;

/** First window: sign in, or send a registration request. */
public class LoginFrame extends JFrame {
    private final Scheduler scheduler;
    private final JTextField idField = new JTextField(15);
    private final JPasswordField passwordField = new JPasswordField(15);

    public LoginFrame(Scheduler scheduler) {
        super("GPU Utilization Management - Login");
        this.scheduler = scheduler;

        JPanel form = new JPanel(new GridLayout(2, 2, 8, 8));
        form.add(new JLabel("University ID (or admin):"));
        form.add(idField);
        form.add(new JLabel("Password:"));
        form.add(passwordField);

        JButton loginButton = new JButton("Login");
        JButton registerButton = new JButton("Register as student");
        JPanel buttons = new JPanel();
        buttons.add(loginButton);
        buttons.add(registerButton);

        JLabel title = new JLabel("GPU Utilization Management", SwingConstants.CENTER);
        title.setFont(new Font("SansSerif", Font.BOLD, 20));

        JPanel main = new JPanel(new BorderLayout(10, 15));
        main.setBorder(BorderFactory.createEmptyBorder(20, 25, 20, 25));
        main.add(title, BorderLayout.NORTH);
        main.add(form, BorderLayout.CENTER);
        main.add(buttons, BorderLayout.SOUTH);
        add(main);

        loginButton.addActionListener(e -> login());
        passwordField.addActionListener(e -> login());      // Enter key also logs in
        registerButton.addActionListener(e -> register());

        setDefaultCloseOperation(EXIT_ON_CLOSE);
        pack();
        setLocationRelativeTo(null);
    }

    private void login() {
        String id = idField.getText().trim();
        String password = new String(passwordField.getPassword());

        // Try the administrator first, then a student.
        User user = scheduler.loginAdmin(id, password);
        if (user == null) user = scheduler.loginStudent(id, password);

        if (user == null) {
            JOptionPane.showMessageDialog(this,
                    "Wrong ID or password, or your request is not approved yet.");
            return;
        }
        // Open the window that matches the kind of user.
        if (user instanceof Administrator) new AdminFrame(scheduler, user).setVisible(true);
        else new StudentFrame(scheduler, (Student) user).setVisible(true);
        dispose();
    }

    private void register() {
        JTextField id = new JTextField();
        JTextField name = new JTextField();
        JTextField email = new JTextField();
        JTextField phone = new JTextField();
        JPasswordField password = new JPasswordField();
        Object[] fields = {
                "University ID:", id, "Full name:", name, "Email:", email,
                "Phone number:", phone, "Choose a password:", password};

        int answer = JOptionPane.showConfirmDialog(this, fields, "Student registration",
                JOptionPane.OK_CANCEL_OPTION);
        if (answer != JOptionPane.OK_OPTION) return;
        try {
            scheduler.register(id.getText(), name.getText(), email.getText(), phone.getText(),
                    new String(password.getPassword()));
            Storage.save(scheduler, Main.SAVE_FILE);
            JOptionPane.showMessageDialog(this, "Request sent. Wait for the administrator to approve it.");
        } catch (Exception error) {
            JOptionPane.showMessageDialog(this, error.getMessage(), "Registration failed",
                    JOptionPane.WARNING_MESSAGE);
        }
    }
}
