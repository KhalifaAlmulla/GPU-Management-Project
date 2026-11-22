import javax.swing.*;
import java.awt.*;

/** The student's window: balance, submit a job, own jobs and own history. */
public class StudentFrame extends DashboardFrame {
    private final Student student;
    private final JTextArea balance = new JTextArea(9, 40);
    private final JTable jobTable = makeTable(JOB_COLUMNS);
    private final JTable historyTable = makeHistoryTable();
    private final JTextField jobSearch = new JTextField(15);
    private final JButton submitButton = new JButton("Submit job");

    public StudentFrame(Scheduler scheduler, Student student) {
        super(scheduler, student);
        this.student = student;

        balance.setEditable(false);
        balance.setFont(new Font("SansSerif", Font.PLAIN, 15));
        JPanel account = new JPanel(new BorderLayout());
        account.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        account.add(balance, BorderLayout.NORTH);

        tabs.addTab("My account", account);
        tabs.addTab("Submit job", buildSubmitTab());
        tabs.addTab("My jobs", buildJobsTab());
        tabs.addTab("History", tablePanel(null, historyTable));
        refreshAll();
    }

    private JPanel buildSubmitTab() {
        JTextField title = new JTextField(25);
        JTextField application = new JTextField(25);
        JTextField description = new JTextField(25);
        JComboBox<String> size = new JComboBox<>(new String[]{"Small", "Medium", "Large", "Very Large"});
        JLabel duration = new JLabel("5 minutes");
        size.addActionListener(e -> duration.setText(Job.durationFor((String) size.getSelectedItem()) + " minutes"));

        JPanel form = new JPanel(new GridLayout(5, 2, 8, 8));
        form.add(new JLabel("Job title:"));
        form.add(title);
        form.add(new JLabel("Application name:"));
        form.add(application);
        form.add(new JLabel("Description:"));
        form.add(description);
        form.add(new JLabel("Task size:"));
        form.add(size);
        form.add(new JLabel("Duration:"));
        form.add(duration);

        submitButton.addActionListener(e -> {
            String chosen = (String) size.getSelectedItem();
            int minutes = Job.durationFor(chosen);
            int answer = JOptionPane.showConfirmDialog(this,
                    "This job will take " + minutes + " minutes.\nReserve " + minutes + " minutes now?",
                    "Confirm duration", JOptionPane.YES_NO_OPTION);
            if (answer != JOptionPane.YES_OPTION) return;
            runAction(() -> {
                scheduler.submitJob(student, title.getText(), application.getText(), description.getText(), chosen);
                title.setText("");
                application.setText("");
                description.setText("");
                tabs.setSelectedIndex(2);   // show "My jobs"
            });
        });

        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));
        JPanel formRow = new JPanel(new FlowLayout(FlowLayout.LEFT));   // keeps the form its normal size
        formRow.add(form);
        JPanel buttonRow = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonRow.add(submitButton);
        panel.add(formRow, BorderLayout.NORTH);
        panel.add(buttonRow, BorderLayout.CENTER);
        return panel;
    }

    private JPanel buildJobsTab() {
        JButton search = new JButton("Search");
        JButton cancel = new JButton("Cancel selected job");
        search.addActionListener(e -> refreshAll());
        jobSearch.addActionListener(e -> refreshAll());
        cancel.addActionListener(e -> cancelSelectedJob(jobTable));

        JPanel bar = new JPanel(new FlowLayout(FlowLayout.LEFT));
        bar.add(new JLabel("Search my jobs:"));
        bar.add(jobSearch);
        bar.add(search);
        bar.add(cancel);
        return tablePanel(bar, jobTable);
    }

    @Override
    protected void refresh() {
        TimeAllowance a = student.getAllowance();
        balance.setText(
                "Student: " + student.getId() + " - " + student.getName()
                + "\n\nAllocated minutes:   " + a.getAllocated()
                + "\nUsed minutes:          " + a.getUsed()
                + "\nReserved minutes:     " + a.getReserved()
                + "\nAvailable minutes:    " + a.getAvailable()
                + "\n\n(Available = Allocated - Used - Reserved)"
                + (scheduler.isClockReady() ? "" : "\n\nThe administrator has not started the clock yet."));

        submitButton.setEnabled(scheduler.isClockReady());
        fillJobs(jobTable, scheduler.getJobsFor(student), jobSearch.getText());
        fillHistory(historyTable, scheduler.getJobsFor(student));
    }
}
