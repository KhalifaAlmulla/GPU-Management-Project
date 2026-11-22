import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;

/** Create realistic, isolated demonstration data without overwriting real saves. */
public class SampleData {
    public static Scheduler build() {
        Scheduler s = new Scheduler();
        Administrator admin = s.loginAdmin("admin", "gpu2026");
        s.initializeClock(admin, LocalDateTime.of(2026, 11, 12, 9, 0));
        String[] names = {"Ahmed Al Hammadi", "Mohammed Al Ali", "Saeed Al Mazrouei",
                "Hamad Al Ketbi", "Rashid Al Nuaimi"};
        for (int i = 1; i <= 5; i++) {
            String id = String.format("S%03d", i);
            s.register(id, names[i - 1], id.toLowerCase() + "@ku.ac.ae", "+97150000000" + i, "demo123");
            s.reviewRequest(admin, id, true);
            s.setAllocation(admin, id, 300 - 10 * i);
        }
        s.register("P001", "Sultan Al Shamsi", "pending1@ku.ac.ae", "+971501234567", "sample123");
        s.register("P002", "Majid Al Falasi", "pending2@ku.ac.ae", "+971501234568", "sample123");
        s.register("R001", "Faisal Al Marri", "rejected@ku.ac.ae", "+971501234569", "sample123");
        s.reviewRequest(admin, "R001", false);
        String[] size = {"Small", "Medium", "Large", "Very Large"};
        String[] apps = {"PyTorch", "TensorFlow", "CUDA Demo", "Data Analysis", "Simulation"};
        for (int i = 0; i < 9; i++) {
            Student student = s.findStudent(String.format("S%03d", i % 5 + 1));
            s.submitJob(student, "Experiment " + (i + 1), apps[i % 5],
                    "University GPU practice workload " + (i + 1), size[i % 4]);
        }
        for (int i = 0; i < 16; i++) s.advanceMinute(admin);
        for (int i = 9; i < 14; i++) {
            Student student = s.findStudent(String.format("S%03d", i % 5 + 1));
            s.submitJob(student, "Experiment " + (i + 1), apps[i % 5],
                    "University GPU practice workload " + (i + 1), size[i % 4]);
        }
        for (int i = 0; i < 5; i++) s.advanceMinute(admin);
        for (Job job : s.getJobs()) {
            if (job.getStatus() == Job.Status.RUNNING) {
                s.cancelJob(s.findStudent(job.getStudentId()), job.getJobId(), "Demo: student stops experiment");
                break;
            }
        }
        for (Job job : s.getJobs()) {
            if (job.getStatus() == Job.Status.QUEUED) {
                s.cancelJob(admin, job.getJobId(), "Demo: administrator frees queue allowance");
                break;
            }
        }
        for (int i = 14; i < 20; i++) {
            Student student = s.findStudent(String.format("S%03d", i % 5 + 1));
            s.submitJob(student, "Experiment " + (i + 1), apps[i % 5],
                    "University GPU practice workload " + (i + 1), size[i % 4]);
        }
        for (int i = 0; i < 6; i++) s.advanceMinute(admin);
        return s;
    }
    public static void main(String[] args) throws Exception {
        Path output = Main.SAVE_FILE;
        if (Files.exists(output) && (args.length == 0 || !"--force".equals(args[0]))) {
            System.out.println("Demo save exists. Not overwriting. Use --force only after a backup.");
            return;
        }
        Scheduler sample = build();
        Storage.save(sample, output);
        System.out.println("Demo data saved to " + output + ", jobs=" + sample.getJobs().size()
                + ", running=" + sample.count(Job.Status.RUNNING)
                + ", queued=" + sample.count(Job.Status.QUEUED)
                + ", completed=" + sample.count(Job.Status.COMPLETED)
                + ", cancelled=" + sample.count(Job.Status.CANCELLED));
    }
}
