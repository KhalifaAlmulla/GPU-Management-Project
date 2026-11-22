import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;

/** The complete business logic; there are no real GPU calls. */
public class Scheduler implements Serializable {
    private static final long serialVersionUID = 1L;
    public static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private final ArrayList<Student> students = new ArrayList<>();
    private final ArrayList<AccessRequest> requests = new ArrayList<>();
    private final ArrayList<Job> jobs = new ArrayList<>();
    private final ArrayList<GPU> gpus = new ArrayList<>();
    private final ArrayList<String> allowanceHistory = new ArrayList<>();
    private final Administrator administrator = new Administrator();
    private int nextJobId = 1001;
    private LocalDateTime initialTime, now;

    public Scheduler() {
        for (int id = 1; id <= 4; id++) gpus.add(new GPU(id));
    }

    private void requireAdmin(User actor) {
        if (actor != administrator) throw new SecurityException("Administrator permission required.");
    }
    private void requireStudent(Student student) {
        if (student == null || findStudent(student.getId()) != student) {
            throw new SecurityException("Approved student permission required.");
        }
    }
    private void requireClock() {
        if (now == null) throw new IllegalStateException("Administrator must initialize the clock.");
    }
    public Administrator loginAdmin(String user, String pass) {
        // The published admin account has fixed credentials, including after loading a save.
        // Check against the course specification rather than a potentially older serialized password.
        if (user == null || pass == null) return null;
        return Administrator.USERNAME.equalsIgnoreCase(user.trim())
                && Administrator.PASSWORD.equals(pass) ? administrator : null;
    }
    public Student loginStudent(String id, String password) {
        Student student = findStudent(id);
        return student != null && student.passwordMatches(password) ? student : null;
    }
    public void register(String id, String name, String email, String phone, String password) {
        // Check for missing fields before trimming, so forms get helpful errors.
        if (id == null || name == null || email == null || phone == null || password == null) {
            throw new IllegalArgumentException("All registration fields are required.");
        }
        id = id.trim(); name = name.trim(); email = email.trim(); phone = phone.trim();
        if (!id.matches("[A-Za-z0-9]{3,20}")) throw new IllegalArgumentException("ID: 3-20 letters/digits.");
        if (name.isEmpty() || name.length() > 80) throw new IllegalArgumentException("Enter a valid name.");
        if (email.length() > 120 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            throw new IllegalArgumentException("Enter a valid email (max 120 characters).");
        }
        int digitCount = phone.replaceAll("[^0-9]", "").length();
        if (!phone.matches("[+0-9 -]{7,20}") || digitCount < 7 || digitCount > 15) {
            throw new IllegalArgumentException("Phone must contain 7-15 digits.");
        }
        if (password.trim().length() < 4 || password.length() > 80) {
            throw new IllegalArgumentException("Password: 4-80 non-blank characters.");
        }
        if (id.equalsIgnoreCase("admin") || findStudent(id) != null || findRequest(id) != null) {
            throw new IllegalArgumentException("University ID already registered/requested.");
        }
        requests.add(new AccessRequest(id, name, email, phone, password));
    }
    public void reviewRequest(User actor, String id, boolean approve) {
        requireAdmin(actor);
        AccessRequest request = findRequest(id);
        if (request == null || !request.getStatus().equals("Pending")) {
            throw new IllegalStateException("No pending request for this ID.");
        }
        if (approve) {
            if (findStudent(id) != null) throw new IllegalStateException("Account already exists.");
            students.add(new Student(request.getId(), request.getName(), request.getEmail(),
                    request.getPhone(), request.getPassword()));
            request.approve();
        } else request.reject();
    }
    public Student findStudent(String id) {
        for (Student student : students) if (student.getId().equalsIgnoreCase(id)) return student;
        return null;
    }
    public AccessRequest findRequest(String id) {
        for (AccessRequest request : requests) if (request.getId().equalsIgnoreCase(id)) return request;
        return null;
    }
    public ArrayList<Student> getStudents() { return new ArrayList<>(students); }
    public ArrayList<AccessRequest> getRequests() { return new ArrayList<>(requests); }
    public ArrayList<Job> getJobs() { return new ArrayList<>(jobs); }
    public ArrayList<GPU> getGpus() { return new ArrayList<>(gpus); }
    public ArrayList<String> getAllowanceHistory() { return new ArrayList<>(allowanceHistory); }
    public LocalDateTime getNow() { return now; }
    public LocalDateTime getInitialTime() { return initialTime; }
    public boolean isClockReady() { return now != null; }
    public String time(LocalDateTime value) { return value == null ? "—" : value.format(FORMAT); }

    public void initializeClock(User actor, LocalDateTime selected) {
        requireAdmin(actor);
        if (now != null) throw new IllegalStateException("Clock already initialized.");
        if (selected == null) throw new IllegalArgumentException("Select a date and time.");
        initialTime = selected.withSecond(0).withNano(0);
        now = initialTime;
    }
    public void setAllocation(User actor, String studentId, int total) {
        requireAdmin(actor);
        Student student = mustFindStudent(studentId);
        int before = student.getAllowance().getAllocated();
        student.getAllowance().setAllocated(total);
        // Keeping both totals makes administrator changes easy to explain.
        allowanceHistory.add("Allocation | " + student.getId() + " | " + before
                + " -> " + total + " | by " + actor.getId() + " | " + time(now));
    }
    public void grantMinutes(User actor, String studentId, int extra, String reason) {
        requireAdmin(actor);
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("Reason required.");
        Student student = mustFindStudent(studentId);
        int before = student.getAllowance().getAllocated();
        student.getAllowance().grant(extra);
        allowanceHistory.add("Extra grant | " + student.getId() + " | " + before
                + " -> " + student.getAllowance().getAllocated() + " (+" + extra + ") | "
                + "reason: " + reason.trim() + " | by " + actor.getId() + " | " + time(now));
    }
    private Student mustFindStudent(String id) {
        Student student = findStudent(id);
        if (student == null) throw new IllegalArgumentException("Student not found.");
        return student;
    }
    private void checkText(String value, String label, int max) {
        if (value == null || value.trim().isEmpty() || value.trim().length() > max) {
            throw new IllegalArgumentException(label + " is required (max " + max + " characters).");
        }
    }
    public Job submitJob(Student student, String title, String application, String description, String size) {
        requireStudent(student);
        requireClock();
        checkText(title, "Job title", 80);
        checkText(application, "Application name", 80);
        checkText(description, "Description", 500);
        int duration = Job.durationFor(size);
        // Reserve *before* acceptance; exact available balance is accepted.
        student.getAllowance().reserve(duration);
        Job job = new Job(nextJobId++, student.getId(), title.trim(), application.trim(),
                description.trim(), size, now);
        jobs.add(job);
        dispatch();
        refreshPredictions();
        return job;
    }
    private ArrayList<Job> waitingQueue() {
        ArrayList<Job> queue = new ArrayList<>();
        for (Job job : jobs) if (job.getStatus() == Job.Status.QUEUED) queue.add(job);
        Collections.sort(queue);   // uses Job.compareTo (submission time, then job ID)
        return queue;
    }
    private void dispatch() {
        if (now == null) return;
        ArrayList<Job> queue = waitingQueue();
        int index = 0;
        for (GPU gpu : gpus) {
            if (gpu.isFree() && index < queue.size()) {
                Job job = queue.get(index++);
                job.start(gpu.getId(), now);
                gpu.assign(job.getJobId());
            }
        }
    }
    public void advanceMinute(User actor) {
        requireAdmin(actor);
        requireClock();
        now = now.plusMinutes(1);
        // Complete all jobs that reached the end of their reserved runtime.
        for (GPU gpu : gpus) {
            if (gpu.isFree()) continue;
            Job job = findJob(gpu.getCurrentJobId());
            if (job.remainingRuntime(now) == 0) {
                job.complete(now);
                settle(job, job.getEstimatedDuration());
                gpu.release();
            }
        }
        dispatch(); // Free GPUs immediately receive the next FCFS jobs at this same minute.
        refreshPredictions();
    }
    private void settle(Job job, int charge) {
        if (job.isSettled()) throw new IllegalStateException("Duplicate settlement.");
        mustFindStudent(job.getStudentId()).getAllowance().settle(job.getEstimatedDuration(), charge);
        job.markSettled(charge);
    }
    public Job findJob(int id) {
        for (Job job : jobs) if (job.getJobId() == id) return job;
        return null;
    }
    public void cancelJob(User actor, int jobId, String reason) {
        requireClock();
        if (reason == null || reason.trim().isEmpty()) throw new IllegalArgumentException("Cancellation reason required.");
        Job job = findJob(jobId);
        if (job == null) throw new IllegalArgumentException("Job not found.");
        if (actor instanceof Student) requireStudent((Student) actor);
        else requireAdmin(actor);
        if (!actor.canManage(job)) throw new SecurityException("Not your job.");
        if (job.getStatus() != Job.Status.QUEUED && job.getStatus() != Job.Status.RUNNING) {
            throw new IllegalStateException("Job is no longer active.");
        }
        boolean running = job.getStatus() == Job.Status.RUNNING;
        int charge = running ? Math.min(job.getEstimatedDuration(), job.runtime(now)) : 0;
        job.cancel(now, actor.getId(), reason.trim());
        settle(job, charge);
        if (running) {
            for (GPU gpu : gpus) if (gpu.getCurrentJobId() == jobId) gpu.release();
        }
        dispatch();
        refreshPredictions();
    }
    /** Prediction uses copied availability times, never real GPU assignments. */
    public void refreshPredictions() {
        if (now == null) return;
        LocalDateTime[] available = new LocalDateTime[4];
        for (int i = 0; i < 4; i++) {
            GPU gpu = gpus.get(i);
            Job running = gpu.isFree() ? null : findJob(gpu.getCurrentJobId());
            available[i] = running == null ? now : now.plusMinutes(running.remainingRuntime(now));
        }
        ArrayList<Job> queue = waitingQueue();
        for (int i = 0; i < queue.size(); i++) {
            int gpuIndex = 0;
            for (int g = 1; g < 4; g++) {
                if (available[g].isBefore(available[gpuIndex])) gpuIndex = g;
            }
            Job job = queue.get(i);
            LocalDateTime start = available[gpuIndex];
            LocalDateTime finish = start.plusMinutes(job.getEstimatedDuration());
            job.setPrediction(i + 1, start, finish);
            available[gpuIndex] = finish;
        }
    }
    public ArrayList<Job> getJobsFor(Student student) {
        requireStudent(student);
        ArrayList<Job> own = new ArrayList<>();
        for (Job job : jobs) if (job.getStudentId().equals(student.getId())) own.add(job);
        return own;
    }
    public int count(Job.Status status) {
        int count = 0;
        for (Job job : jobs) if (job.getStatus() == status) count++;
        return count;
    }
    public int busyGpuCount() {
        int count = 0;
        for (GPU gpu : gpus) if (!gpu.isFree()) count++;
        return count;
    }
    public long elapsedMinutes() {
        return now == null ? 0 : Duration.between(initialTime, now).toMinutes();
    }
    public long busyMinutes() {
        long busy = 0;
        if (now == null) return 0;
        for (Job job : jobs) {
            if (job.getStatus() == Job.Status.RUNNING) busy += job.runtime(now);
            else if (job.isSettled()) busy += job.getChargedMinutes();
        }
        return busy;
    }
    public String utilization() {
        if (elapsedMinutes() == 0) return "N/A";
        return String.format(java.util.Locale.US, "%.1f%%", 100.0 * busyMinutes() / (4 * elapsedMinutes()));
    }
}
