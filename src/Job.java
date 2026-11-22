import java.io.Serializable;
import java.time.Duration;
import java.time.LocalDateTime;

/** A job keeps immutable inputs and a strictly controlled execution lifecycle. */
public class Job implements Identifiable, Comparable<Job>, Serializable {
    private static final long serialVersionUID = 1L;
    public enum Status { QUEUED, RUNNING, COMPLETED, CANCELLED }
    private final int jobId;
    private final String studentId, title, applicationName, description, taskSize;
    private final int workloadUnits, estimatedDuration;
    private final LocalDateTime submissionTime;
    private Status status = Status.QUEUED;
    private int assignedGpuId = -1;
    private LocalDateTime startTime, finishTime, cancellationTime;
    private String cancelledBy = "", cancellationReason = "";
    private int chargedMinutes;
    private boolean settled;
    private int queuePosition;
    private LocalDateTime predictedStart, predictedFinish;

    public Job(int id, String owner, String title, String app, String description,
               String taskSize, LocalDateTime submission) {
        this.jobId = id;
        this.studentId = owner;
        this.title = title;
        this.applicationName = app;
        this.description = description;
        this.taskSize = taskSize;
        this.estimatedDuration = durationFor(taskSize);
        this.workloadUnits = estimatedDuration;
        this.submissionTime = submission;
    }

    public static int durationFor(String size) {
        if ("Small".equals(size)) return 5;
        if ("Medium".equals(size)) return 15;
        if ("Large".equals(size)) return 30;
        if ("Very Large".equals(size)) return 60;
        throw new IllegalArgumentException("Unknown task size.");
    }

    public void start(int gpu, LocalDateTime now) {
        if (status != Status.QUEUED) throw new IllegalStateException("Job cannot start.");
        status = Status.RUNNING;
        assignedGpuId = gpu;
        startTime = now;
        queuePosition = 0;
        predictedStart = now;
        predictedFinish = now.plusMinutes(estimatedDuration);
    }

    public void complete(LocalDateTime now) {
        if (status != Status.RUNNING || runtime(now) < estimatedDuration) {
            throw new IllegalStateException("Job cannot complete yet.");
        }
        status = Status.COMPLETED;
        finishTime = now;
    }

    public void cancel(LocalDateTime now, String actor, String reason) {
        if (status != Status.QUEUED && status != Status.RUNNING) {
            throw new IllegalStateException("Only active jobs may be cancelled.");
        }
        status = Status.CANCELLED;
        cancellationTime = now;
        cancelledBy = actor;
        cancellationReason = reason;
        if (startTime != null) finishTime = now;
        queuePosition = 0;
    }

    /** Used to guarantee settlement is performed exactly once. */
    public void markSettled(int charge) {
        if (settled) throw new IllegalStateException("Already charged.");
        if (charge < 0 || charge > estimatedDuration) throw new IllegalArgumentException("Bad charge.");
        chargedMinutes = charge;
        settled = true;
    }

    public int runtime(LocalDateTime now) {
        if (startTime == null) return 0;
        LocalDateTime until = finishTime == null ? now : finishTime;
        return Math.max(0, (int) Duration.between(startTime, until).toMinutes());
    }
    public int remainingRuntime(LocalDateTime now) {
        return status == Status.RUNNING ? Math.max(0, estimatedDuration - runtime(now)) : 0;
    }
    public int actualWait() {
        return startTime == null ? 0 : (int) Duration.between(submissionTime, startTime).toMinutes();
    }
    public void setPrediction(int position, LocalDateTime start, LocalDateTime end) {
        if (status != Status.QUEUED) return;
        queuePosition = position;
        predictedStart = start;
        predictedFinish = end;
    }
    public int remainingWait(LocalDateTime now) {
        return status == Status.QUEUED && predictedStart != null
                ? Math.max(0, (int) Duration.between(now, predictedStart).toMinutes()) : 0;
    }

    /** FCFS order: earlier submission first, then smaller job ID. */
    @Override public int compareTo(Job other) {
        int byTime = submissionTime.compareTo(other.submissionTime);
        return byTime != 0 ? byTime : Integer.compare(jobId, other.jobId);
    }

    @Override public String getId() { return Integer.toString(jobId); }
    public int getJobId() { return jobId; }
    public String getStudentId() { return studentId; }
    public String getTitle() { return title; }
    public String getApplicationName() { return applicationName; }
    public String getDescription() { return description; }
    public String getTaskSize() { return taskSize; }
    public int getWorkloadUnits() { return workloadUnits; }
    public int getEstimatedDuration() { return estimatedDuration; }
    public LocalDateTime getSubmissionTime() { return submissionTime; }
    public Status getStatus() { return status; }
    public int getAssignedGpuId() { return assignedGpuId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getFinishTime() { return finishTime; }
    public LocalDateTime getCancellationTime() { return cancellationTime; }
    public String getCancelledBy() { return cancelledBy; }
    public String getCancellationReason() { return cancellationReason; }
    public int getChargedMinutes() { return chargedMinutes; }
    public boolean isSettled() { return settled; }
    public int getQueuePosition() { return queuePosition; }
    public LocalDateTime getPredictedStart() { return predictedStart; }
    public LocalDateTime getPredictedFinish() { return predictedFinish; }
}
