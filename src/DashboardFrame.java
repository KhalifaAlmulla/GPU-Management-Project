import javax.swing.*;
import javax.swing.table.DefaultTableModel;
import java.awt.*;
import java.util.ArrayList;

/**
 * Parent class of AdminFrame and StudentFrame.
 * It holds what both windows share: the top bar, the tabs, table helpers and saving.
 * Every rule (who may do what, how minutes are charged) is in Scheduler, not here.
 */
public abstract class DashboardFrame extends JFrame {
    protected final Scheduler scheduler;
    protected final User user;
    protected final JTabbedPane tabs = new JTabbedPane();
    protected final JLabel clockLabel = new JLabel();
    protected final JPanel topButtons = new JPanel();

    /** Columns for the "Jobs" tables (what the brief asks the dashboard to show). */
    protected static final String[] JOB_COLUMNS = {"Job ID", "Student", "Title", "Application",
            "Size", "Status", "GPU", "Queue #", "Predicted start", "Predicted finish", "Wait (min)"};

    /** Columns for the "History" tables (completed and cancelled jobs). */
    protected static final String[] HISTORY_COLUMNS = {"Job ID", "Student", "Title", "Status", "GPU",
            "Submitted", "Started", "Finished", "Runtime", "Queue wait", "Charged", "Cancelled by / reason"};

    public DashboardFrame(Scheduler scheduler, User user) {
        super("GPU Utilization Management - " + user.displayName());
        this.scheduler = scheduler;
        this.user = user;

        JButton logoutButton = new JButton("Save and logout");
        logoutButton.addActionListener(e -> logout());
        topButtons.add(logoutButton);

        JPanel top = new JPanel(new BorderLayout());
        top.setBorder(BorderFactory.createEmptyBorder(8, 12, 8, 12));
        clockLabel.setFont(new Font("SansSerif", Font.BOLD, 15));
        top.add(clockLabel, BorderLayout.WEST);
        top.add(topButtons, BorderLayout.EAST);

        add(top, BorderLayout.NORTH);
        add(tabs, BorderLayout.CENTER);

        // Closing the window saves the data first.
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override public void windowClosing(java.awt.event.WindowEvent e) { logout(); }
        });
        setSize(1200, 650);
        setLocationRelativeTo(null);
    }

    /** Each child window fills its own tables. (Polymorphism: AdminFrame and StudentFrame differ.) */
    protected abstract void refresh();

    /** Updates the clock text, then lets the child window refresh its tables. */
    protected void refreshAll() {
        scheduler.refreshPredictions();
        clockLabel.setText("Simulation time: " + scheduler.time(scheduler.getNow()));
        refresh();
    }

    /** Runs a button action, saves to the file, and refreshes the screen. Shows any error. */
    protected void runAction(Runnable action) {
        try {
            action.run();
            Storage.save(scheduler, Main.SAVE_FILE);
            refreshAll();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, e.getMessage(), "Not allowed", JOptionPane.WARNING_MESSAGE);
        }
    }

    protected void logout() {
        try {
            Storage.save(scheduler, Main.SAVE_FILE);
            new LoginFrame(scheduler).setVisible(true);
            dispose();
        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Could not save: " + e.getMessage());
        }
    }

    // ---------- small table helpers ----------

    protected JTable makeTable(String[] columns) {
        DefaultTableModel model = new DefaultTableModel(columns, 0) {
            @Override public boolean isCellEditable(int row, int column) { return false; }
        };
        JTable table = new JTable(model);
        table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        table.setRowHeight(24);
        return table;
    }

    /** History table with wider columns for the dates and the cancellation reason. */
    protected JTable makeHistoryTable() {
        JTable table = makeTable(HISTORY_COLUMNS);
        for (int column = 5; column <= 7; column++) {
            table.getColumnModel().getColumn(column).setPreferredWidth(130);
        }
        table.getColumnModel().getColumn(11).setPreferredWidth(300);
        return table;
    }

    protected DefaultTableModel model(JTable table) {
        return (DefaultTableModel) table.getModel();
    }

    /** Fills a Jobs table. Only queued and running jobs have a queue position or wait. */
    protected void fillJobs(JTable table, ArrayList<Job> jobs, String search) {
        model(table).setRowCount(0);
        for (Job job : jobs) {
            if (!matches(job, search)) continue;
            boolean queued = job.getStatus() == Job.Status.QUEUED;
            boolean active = queued || job.getStatus() == Job.Status.RUNNING;
            model(table).addRow(new Object[]{
                    job.getJobId(), job.getStudentId(), job.getTitle(), job.getApplicationName(),
                    job.getTaskSize() + " (" + job.getEstimatedDuration() + " min)", job.getStatus(),
                    gpuText(job), queued ? job.getQueuePosition() : "-",
                    active ? scheduler.time(job.getPredictedStart()) : "-",
                    active ? scheduler.time(job.getPredictedFinish()) : "-",
                    queued ? job.remainingWait(scheduler.getNow()) : "-"});
        }
    }

    /** Fills a History table with completed and cancelled jobs only. */
    protected void fillHistory(JTable table, ArrayList<Job> jobs) {
        model(table).setRowCount(0);
        for (Job job : jobs) {
            if (job.getStatus() != Job.Status.COMPLETED && job.getStatus() != Job.Status.CANCELLED) continue;
            boolean started = job.getStartTime() != null;
            String cancelInfo = job.getStatus() == Job.Status.CANCELLED
                    ? job.getCancelledBy() + ": " + job.getCancellationReason() : "-";
            model(table).addRow(new Object[]{
                    job.getJobId(), job.getStudentId(), job.getTitle(), job.getStatus(), gpuText(job),
                    scheduler.time(job.getSubmissionTime()), scheduler.time(job.getStartTime()),
                    scheduler.time(job.getFinishTime()), job.runtime(scheduler.getNow()) + " min",
                    started ? job.actualWait() + " min" : "-", job.getChargedMinutes() + " min",
                    cancelInfo});
        }
    }

    private boolean matches(Job job, String search) {
        if (search == null || search.trim().isEmpty()) return true;
        String text = (job.getJobId() + " " + job.getStudentId() + " " + job.getTitle() + " "
                + job.getApplicationName() + " " + job.getStatus()).toLowerCase();
        return text.contains(search.trim().toLowerCase());
    }

    private String gpuText(Job job) {
        return job.getAssignedGpuId() == -1 ? "-" : "GPU " + job.getAssignedGpuId();
    }

    /** Returns the Job ID in the selected row (column 0), or shows a message. */
    protected Integer selectedJobId(JTable table) {
        int row = table.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a job in the table first.");
            return null;
        }
        return (Integer) table.getValueAt(row, 0);
    }

    /** Asks for a reason, then cancels the selected job. Scheduler checks permissions and charges. */
    protected void cancelSelectedJob(JTable table) {
        Integer jobId = selectedJobId(table);
        if (jobId == null) return;
        String reason = JOptionPane.showInputDialog(this, "Reason for cancelling job " + jobId + ":");
        if (reason != null) runAction(() -> scheduler.cancelJob(user, jobId, reason));
    }

    /** A panel with a table in the middle and an optional bar of controls on top. */
    protected JPanel tablePanel(JComponent topBar, JTable table) {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        if (topBar != null) panel.add(topBar, BorderLayout.NORTH);
        panel.add(new JScrollPane(table), BorderLayout.CENTER);
        return panel;
    }
}
