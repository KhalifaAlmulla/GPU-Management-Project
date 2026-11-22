import java.io.Serializable;

/** One simulated H100. An ID of -1 means the GPU is free. */
public class GPU implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int id;
    private int currentJobId = -1;
    public GPU(int id) { this.id = id; }
    public int getId() { return id; }
    public int getCurrentJobId() { return currentJobId; }
    public boolean isFree() { return currentJobId == -1; }
    public void assign(int jobId) {
        if (!isFree()) throw new IllegalStateException("GPU is busy.");
        currentJobId = jobId;
    }
    public void release() { currentJobId = -1; }
}
