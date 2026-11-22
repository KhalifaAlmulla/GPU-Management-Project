import javax.swing.*;
import java.nio.file.*;

/** Starts the program: loads the save file and opens the login window. */
public class Main {
    public static final Path SAVE_FILE = Paths.get("data", "system.ser");

    public static void main(String[] args) {
        // Use Swing's built-in Nimbus look so the program looks the same on every computer.
        try {
            UIManager.setLookAndFeel("javax.swing.plaf.nimbus.NimbusLookAndFeel");
        } catch (Exception ignored) {
            // The default look is fine if Nimbus is missing.
        }

        SwingUtilities.invokeLater(() -> {
            try {
                Scheduler scheduler;
                if (Files.exists(SAVE_FILE)) {
                    scheduler = Storage.load(SAVE_FILE);
                } else {
                    int answer = JOptionPane.showConfirmDialog(null,
                            "No saved data found.\nLoad the demo data (5 students, 20 jobs)?",
                            "First start", JOptionPane.YES_NO_OPTION);
                    scheduler = answer == JOptionPane.YES_OPTION ? SampleData.build() : new Scheduler();
                    Storage.save(scheduler, SAVE_FILE);
                }
                new LoginFrame(scheduler).setVisible(true);
            } catch (Exception e) {
                JOptionPane.showMessageDialog(null, e.getMessage(),
                        "Could not load saved data", JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}
