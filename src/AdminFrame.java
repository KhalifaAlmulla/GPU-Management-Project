import javax.swing.*;
import java.awt.*;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/** The administrator's window: overview, requests, students, all jobs and history. */
public class AdminFrame extends DashboardFrame {
    private final JTextArea summary = new JTextArea(4, 40);
    private final JTable gpuTable = makeTable(new String[]{
            "GPU", "Availability", "Current job", "Remaining runtime (min)", "Expected finish"});
    private final JTable balanceTable = makeTable(new String[]{
            "Student", "Allocated", "Used", "Reserved", "Available"});
    private final JTable requestTable = makeTable(new String[]{
            "University ID", "Name", "Email", "Phone", "Chosen password", "Status"});
    private final JTable studentTable = makeTable(new String[]{
            "University ID", "Name", "Email", "Phone", "Allocated", "Used", "Reserved", "Available"});
    private final JTable jobTable = makeTable(JOB_COLUMNS);
    private final JTable historyTable = makeHistoryTable();
    private final JTextField studentSearch = new JTextField(10);
    private final JTextField jobSearch = new JTextField(15);
    private final JTextArea allowanceLog = new JTextArea(4, 40);
    private final JPanel clockPanel = new JPanel();

    public AdminFrame(Scheduler scheduler, User admin) {
        super(scheduler, admin);

        JButton advanceButton = new JButton("Advance Minute");
        advanceButton.addActionListener(e -> runAction(() -> scheduler.advanceMinute(user)));
        topButtons.add(advanceButton, 0);   // put it before the logout button

        tabs.addTab("Overview", buildOverviewTab());
        tabs.addTab("Registration requests", buildRequestsTab());
        tabs.addTab("Students", buildStudentsTab());
        tabs.addTab("All jobs", buildJobsTab());
        tabs.addTab("History", tablePanel(null, historyTable));
        refreshAll();
    }

    // ---------- tab 1: overview + clock setup ----------

    private JPanel buildOverviewTab() {
        // Date picker and time picker, used only once to start the clock.
        JSpinner datePicker = new JSpinner(new SpinnerDateModel());
        datePicker.setEditor(new JSpinner.DateEditor(datePicker, "dd/MM/yyyy"));
        JSpinner timePicker = new JSpinner(new SpinnerDateModel());
        timePicker.setEditor(new JSpinner.DateEditor(timePicker, "HH:mm"));
        JButton startButton = new JButton("Start clock");
        startButton.addActionListener(e -> runAction(() -> {
            LocalDateTime date = toLocal((Date) datePicker.getValue());
            LocalDateTime time = toLocal((Date) timePicker.getValue());
            scheduler.initializeClock(user, date.toLocalDate().atTime(time.getHour(), time.getMinute()));
        }));
        clockPanel.add(new JLabel("Set the start of the simulation:  date"));
        clockPanel.add(datePicker);
        clockPanel.add(new JLabel("time (24h)"));
        clockPanel.add(timePicker);
        clockPanel.add(startButton);

        summary.setEditable(false);
        summary.setFont(new Font("SansSerif", Font.PLAIN, 14));

        JPanel tables = new JPanel(new GridLayout(1, 2, 10, 0));
        balanceTable.getColumnModel().getColumn(0).setPreferredWidth(200);   // room for the name
        tables.add(titled("GPUs", new JScrollPane(gpuTable)));
        tables.add(titled("Student balances (minutes)", new JScrollPane(balanceTable)));

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
        panel.add(titled("Summary", summary), BorderLayout.NORTH);
        panel.add(tables, BorderLayout.CENTER);
        panel.add(clockPanel, BorderLayout.SOUTH);
        return panel;
    }

    private LocalDateTime toLocal(Date date) {
        return LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault());
    }

    private JComponent titled(String title, JComponent inside) {
        JPanel box = new JPanel(new BorderLayout());
        box.setBorder(BorderFactory.createTitledBorder(title));
        box.add(inside);
        return box;
    }

    // ---------- tab 2: registration requests ----------

    private JPanel buildRequestsTab() {
        JButton approve = new JButton("Approve selected");
        JButton reject = new JButton("Reject selected");
        approve.addActionListener(e -> reviewSelected(true));
        reject.addActionListener(e -> reviewSelected(false));
        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(approve);
        bar.add(reject);
        bar.add(new JLabel("Approved students get 100 minutes. Change it in the Students tab."));
        return tablePanel(bar, requestTable);
    }

    private void reviewSelected(boolean approve) {
        int row = requestTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a request first.");
            return;
        }
        String id = (String) requestTable.getValueAt(row, 0);
        runAction(() -> scheduler.reviewRequest(user, id, approve));
    }

    // ---------- tab 3: students and allowances ----------

    private JPanel buildStudentsTab() {
        JButton search = new JButton("Search");
        JButton setAllocation = new JButton("Set allocation");
        JButton grant = new JButton("Grant extra minutes");
        JButton showJobs = new JButton("Show this student's jobs");
        search.addActionListener(e -> refreshAll());
        studentSearch.addActionListener(e -> refreshAll());
        setAllocation.addActionListener(e -> setAllocation());
        grant.addActionListener(e -> grantMinutes());
        showJobs.addActionListener(e -> showStudentJobs());

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("University ID:"));
        bar.add(studentSearch);
        bar.add(search);
        bar.add(setAllocation);
        bar.add(grant);
        bar.add(showJobs);

        allowanceLog.setEditable(false);
        JPanel panel = tablePanel(bar, studentTable);
        panel.add(titled("Allowance changes and reasons", new JScrollPane(allowanceLog)), BorderLayout.SOUTH);
        return panel;
    }

    private String selectedStudentId() {
        int row = studentTable.getSelectedRow();
        if (row < 0) {
            JOptionPane.showMessageDialog(this, "Select a student first.");
            return null;
        }
        return (String) studentTable.getValueAt(row, 0);
    }

    private void setAllocation() {
        String id = selectedStudentId();
        if (id == null) return;
        String value = JOptionPane.showInputDialog(this, "New total allocation (minutes) for " + id + ":");
        if (value == null) return;
        runAction(() -> scheduler.setAllocation(user, id, readNumber(value)));
    }

    private void grantMinutes() {
        String id = selectedStudentId();
        if (id == null) return;
        String value = JOptionPane.showInputDialog(this, "Extra minutes for " + id + ":");
        if (value == null) return;
        String reason = JOptionPane.showInputDialog(this, "Reason for the extra minutes:");
        if (reason == null) return;
        runAction(() -> scheduler.grantMinutes(user, id, readNumber(value), reason));
    }

    private int readNumber(String text) {
        try {
            return Integer.parseInt(text.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Please enter a whole number.");
        }
    }

    private void showStudentJobs() {
        String id = selectedStudentId();
        if (id == null) return;
        jobSearch.setText(id);
        tabs.setSelectedIndex(3);   // the "All jobs" tab
        refreshAll();
    }

    // ---------- tab 4: all jobs ----------

    private JPanel buildJobsTab() {
        JButton search = new JButton("Search");
        JButton cancel = new JButton("Cancel selected job");
        search.addActionListener(e -> refreshAll());
        jobSearch.addActionListener(e -> refreshAll());
        cancel.addActionListener(e -> cancelSelectedJob(jobTable));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search (ID, student, title, status):"));
        bar.add(jobSearch);
        bar.add(search);
        bar.add(cancel);
        return tablePanel(bar, jobTable);
    }

    // ---------- refresh all tables ----------

    @Override
    protected void refresh() {
        clockPanel.setVisible(!scheduler.isClockReady());   // the clock can be set only once

        summary.setText(
                "Queued: " + scheduler.count(Job.Status.QUEUED)
                + "    Running: " + scheduler.count(Job.Status.RUNNING)
                + "    Completed: " + scheduler.count(Job.Status.COMPLETED)
                + "    Cancelled: " + scheduler.count(Job.Status.CANCELLED)
                + "\nBusy GPUs: " + scheduler.busyGpuCount() + " of 4"
                + "\nGPU utilization: " + scheduler.utilization()
                + "   (busy GPU minutes " + scheduler.busyMinutes()
                + " / (4 x " + scheduler.elapsedMinutes() + " elapsed minutes))"
                + (scheduler.isClockReady() ? "" : "\n\nThe clock is not started yet. Set it below."));

        model(gpuTable).setRowCount(0);
        for (GPU gpu : scheduler.getGpus()) {
            if (gpu.isFree()) {
                model(gpuTable).addRow(new Object[]{"GPU " + gpu.getId(), "Available", "-", "-", "-"});
            } else {
                Job job = scheduler.findJob(gpu.getCurrentJobId());
                model(gpuTable).addRow(new Object[]{"GPU " + gpu.getId(), "Busy", job.getJobId(),
                        job.remainingRuntime(scheduler.getNow()), scheduler.time(job.getPredictedFinish())});
            }
        }

        model(balanceTable).setRowCount(0);
        model(studentTable).setRowCount(0);
        String search = studentSearch.getText().trim().toLowerCase();
        for (Student s : scheduler.getStudents()) {
            TimeAllowance a = s.getAllowance();
            model(balanceTable).addRow(new Object[]{s.getId() + " " + s.getName(),
                    a.getAllocated(), a.getUsed(), a.getReserved(), a.getAvailable()});
            if (s.getId().toLowerCase().contains(search)) {
                model(studentTable).addRow(new Object[]{s.getId(), s.getName(), s.getEmail(), s.getPhone(),
                        a.getAllocated(), a.getUsed(), a.getReserved(), a.getAvailable()});
            }
        }

        model(requestTable).setRowCount(0);
        for (AccessRequest r : scheduler.getRequests()) {
            model(requestTable).addRow(new Object[]{r.getId(), r.getName(), r.getEmail(), r.getPhone(),
                    r.getPassword(), r.getStatus()});
        }

        allowanceLog.setText(String.join("\n", scheduler.getAllowanceHistory()));
        fillJobs(jobTable, scheduler.getJobs(), jobSearch.getText());
        fillHistory(historyTable, scheduler.getJobs());
    }
}
