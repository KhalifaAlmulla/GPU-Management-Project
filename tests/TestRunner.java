import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.ArrayList;

/** Standalone beginner-friendly regression tests; no external JUnit dependency. */
public class TestRunner {
    private static int passed, failed;
    private static final LocalDateTime START = LocalDateTime.of(2026, 11, 1, 9, 0);
    @FunctionalInterface interface Case { void test() throws Exception; }

    static void test(String name, Case task) {
        try {
            task.test();
            passed++;
            System.out.println("PASS | " + name);
        } catch (Throwable e) {
            failed++;
            System.out.println("FAIL | " + name + " | " + e);
        }
    }
    static void yes(boolean condition) { if (!condition) throw new AssertionError("Expected true."); }
    static void equal(Object expected, Object actual) {
        if (!expected.equals(actual)) throw new AssertionError("Expected " + expected + " but got " + actual);
    }
    static void error(Class<? extends Throwable> type, Runnable r) {
        try { r.run(); }
        catch (Throwable e) { if (type.isInstance(e)) return; throw new AssertionError("Wrong error: " + e); }
        throw new AssertionError("Expected " + type.getSimpleName());
    }
    static Scheduler empty() { return new Scheduler(); }
    static Administrator admin(Scheduler s) { return s.loginAdmin("admin", "gpu2026"); }
    static Student student(Scheduler s, String id) {
        s.register(id, "Test Student", id + "@ku.ac.ae", "+971501234567", "abcd");
        s.reviewRequest(admin(s), id, true);
        return s.loginStudent(id, "abcd");
    }
    static Scheduler ready() {
        Scheduler s = new Scheduler();
        s.initializeClock(admin(s), START);
        return s;
    }
    static Job submit(Scheduler s, Student student, String size) {
        return s.submitJob(student, "Test job", "CUDA", "Test description", size);
    }
    static void advance(Scheduler s, int minutes) {
        for (int i = 0; i < minutes; i++) s.advanceMinute(admin(s));
    }
    public static void main(String[] args) throws Exception {
        test("admin login uses the published credentials", () -> {
            Scheduler s = empty();
            yes(s.loginAdmin("admin", "gpu2026") != null);
            yes(s.loginAdmin(" admin ", "gpu2026") != null);
            yes(s.loginAdmin("ADMIN", "gpu2026") != null);
        });
        test("admin login rejects wrong password and identity", () -> {
            Scheduler s = empty();
            yes(s.loginAdmin("admin", "wrong") == null);
            yes(s.loginAdmin("student", "gpu2026") == null);
            yes(s.loginAdmin(null, "gpu2026") == null);
            yes(s.loginAdmin("admin", null) == null);
        });
        test("administrator login survives save and reload", () -> {
            Scheduler s = ready();
            Path file = Files.createTempFile("gpu-admin-login-", ".ser");
            try {
                Storage.save(s, file);
                Scheduler loaded = Storage.load(file);
                Administrator a = loaded.loginAdmin("admin", "gpu2026");
                yes(a != null);
                loaded.advanceMinute(a);
                equal(START.plusMinutes(1), loaded.getNow());
            } finally { Files.deleteIfExists(file); }
        });
        test("registration remains pending", () -> {
            Scheduler s = empty();
            s.register("S001", "Student", "s@ku.ac.ae", "+971501234567", "abcd");
            equal("Pending", s.findRequest("S001").getStatus());
        });
        test("duplicate registration rejected", () -> {
            Scheduler s = empty(); s.register("S001", "Student", "s@ku.ac.ae", "+971501234567", "abcd");
            error(IllegalArgumentException.class, () -> s.register("S001", "Student", "s@ku.ac.ae", "+971501234567", "abcd"));
        });
        test("approval assigns 100", () -> { Scheduler s = empty(); equal(100, student(s,"S001").getAllowance().getAllocated()); });
        test("repeated approval blocked", () -> {
            Scheduler s = empty(); student(s,"S001");
            error(IllegalStateException.class, () -> s.reviewRequest(admin(s), "S001", true));
        });
        test("approved student login", () -> { Scheduler s = empty(); yes(student(s,"S001") != null); });
        test("wrong password rejected", () -> { Scheduler s = empty(); student(s,"S001"); yes(s.loginStudent("S001","bad") == null); });
        test("pending account cannot log in", () -> {
            Scheduler s = empty(); s.register("S001","Student","s@ku.ac.ae","+971501234567","abcd");
            yes(s.loginStudent("S001","abcd") == null);
        });
        test("rejection stored and blocks login", () -> {
            Scheduler s = empty(); s.register("S001","Student","s@ku.ac.ae","+971501234567","abcd");
            s.reviewRequest(admin(s),"S001",false);
            equal("Rejected",s.findRequest("S001").getStatus());
            yes(s.loginStudent("S001","abcd") == null);
        });
        test("bad IDs and emails rejected", () -> {
            Scheduler s = empty();
            error(IllegalArgumentException.class, () -> s.register("*","Student","s@ku.ac.ae","+971501234567","abcd"));
            error(IllegalArgumentException.class, () -> s.register("S001","Student","bad","+971501234567","abcd"));
        });
        test("jobs require initialized clock", () -> {
            Scheduler s = empty(); Student a = student(s,"S001");
            error(IllegalStateException.class, () -> submit(s,a,"Small"));
        });
        test("clock initialized once only", () -> {
            Scheduler s = ready();
            error(IllegalStateException.class, () -> s.initializeClock(admin(s),START.plusDays(1)));
        });
        test("admin credentials required", () -> {
            Scheduler s = empty(); yes(s.loginAdmin("admin","incorrect") == null);
            error(SecurityException.class, () -> s.initializeClock(null,START));
        });
        test("four IDs in order", () -> {
            Scheduler s = ready(); equal(4,s.getGpus().size());
            for (int i = 0; i < 4; i++) equal(i + 1,s.getGpus().get(i).getId());
        });
        test("four simultaneous running", () -> {
            Scheduler s = ready(); Student a = student(s,"S001");
            for (int i=0; i<4; i++) submit(s,a,"Small");
            equal(4,s.busyGpuCount()); equal(0,s.count(Job.Status.QUEUED));
        });
        test("fifth queued", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<5;i++) submit(s,a,"Small");
            equal(1,s.count(Job.Status.QUEUED));
        });
        test("same-minute FCFS and job IDs", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            Job first=submit(s,a,"Small"), second=submit(s,a,"Small");
            equal(1001,first.getJobId()); equal(1002,second.getJobId());
            equal(first.getSubmissionTime(),second.getSubmissionTime());
        });
        test("smallest GPU allocated first", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=1;i<=4;i++) equal(i,submit(s,a,"Small").getAssignedGpuId());
        });
        test("queued predictions include running", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job fifth=submit(s,a,"Small");
            equal(1,fifth.getQueuePosition()); equal(START.plusMinutes(5),fifth.getPredictedStart());
            equal(START.plusMinutes(10),fifth.getPredictedFinish());
        });
        test("prediction handles unequal GPU finishing times", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            s.setAllocation(admin(s),"S001",200);
            submit(s,a,"Small"); submit(s,a,"Medium"); submit(s,a,"Large"); submit(s,a,"Very Large");
            Job fifth=submit(s,a,"Small"), sixth=submit(s,a,"Small");
            equal(START.plusMinutes(5),fifth.getPredictedStart());
            equal(START.plusMinutes(10),sixth.getPredictedStart());
        });
        test("waiting shrinks with clock advancement", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job fifth=submit(s,a,"Small"); advance(s,2);
            equal(3,fifth.remainingWait(s.getNow()));
        });
        test("completed jobs charge full runtime", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            Job job=submit(s,a,"Small"); advance(s,5);
            equal(Job.Status.COMPLETED,job.getStatus()); equal(5,a.getAllowance().getUsed());
            equal(0,a.getAllowance().getReserved());
        });
        test("dispatch exactly when GPU frees", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job fifth=submit(s,a,"Small"); advance(s,5);
            equal(Job.Status.RUNNING,fifth.getStatus());
            equal(START.plusMinutes(5),fifth.getStartTime()); equal(1,fifth.getAssignedGpuId());
        });
        test("actual queued wait is five", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job fifth=submit(s,a,"Small"); advance(s,5);
            equal(5,fifth.actualWait());
        });
        test("queued cancellation costs zero", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job queued=submit(s,a,"Small");
            s.cancelJob(a,queued.getJobId(),"Not needed");
            equal(Job.Status.CANCELLED,queued.getStatus());
            equal(0,queued.getChargedMinutes()); equal(20,a.getAllowance().getReserved());
        });
        test("running cancellation charges elapsed only", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            Job job=submit(s,a,"Large"); advance(s,7);
            s.cancelJob(a,job.getJobId(),"Stop");
            equal(7,job.getChargedMinutes()); equal(7,a.getAllowance().getUsed());
            equal(0,a.getAllowance().getReserved()); equal(0,s.busyGpuCount());
        });
        test("admin cancels student's job", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Small");
            s.cancelJob(admin(s),job.getJobId(),"Admin decision");
            equal("admin",job.getCancelledBy()); equal("Admin decision",job.getCancellationReason());
        });
        test("other students forbidden from cancelling", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"), b=student(s,"S002");
            Job job=submit(s,a,"Small");
            error(SecurityException.class, () -> s.cancelJob(b,job.getJobId(),"Not mine"));
        });
        test("permissions block student reviewing requests", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            error(SecurityException.class, () -> s.reviewRequest(a,"S001",true));
        });
        test("exact balance matching accepted", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            submit(s,a,"Very Large"); submit(s,a,"Large");
            submit(s,a,"Small"); submit(s,a,"Small");
            equal(0,a.getAllowance().getAvailable());
        });
        test("insufficient balance rejected", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            submit(s,a,"Very Large"); submit(s,a,"Large");
            error(IllegalArgumentException.class, () -> submit(s,a,"Medium"));
        });
        test("zero balance blocks only NEW jobs", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            submit(s,a,"Very Large"); submit(s,a,"Large");
            submit(s,a,"Small"); submit(s,a,"Small");
            error(IllegalArgumentException.class, () -> submit(s,a,"Small"));
            advance(s,5); yes(a.getAllowance().getUsed() > 0);
        });
        test("no duplicate charges", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Small");
            advance(s,5);
            error(IllegalStateException.class, () -> s.cancelJob(a,job.getJobId(),"Again"));
            equal(5,a.getAllowance().getUsed());
        });
        test("allowance never goes below zero", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            submit(s,a,"Very Large");
            error(IllegalArgumentException.class, () -> s.setAllocation(admin(s),"S001",30));
            yes(a.getAllowance().getAvailable() >= 0);
        });
        test("terminal jobs cannot be restarted", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Small");
            advance(s,5);
            error(IllegalStateException.class, () -> job.start(1,s.getNow()));
        });
        test("running job cannot complete early", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Small");
            error(IllegalStateException.class, () -> job.complete(s.getNow()));
        });
        test("utilization N/A at minute zero", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); submit(s,a,"Small");
            equal("N/A",s.utilization());
        });
        test("utilization 100% with four running", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small"); advance(s,2);
            equal("100.0%",s.utilization()); equal(8L,s.busyMinutes());
        });
        test("cancelled runtime included in utilization", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Medium");
            advance(s,3); s.cancelJob(a,job.getJobId(),"Stop"); advance(s,2);
            equal(3L,s.busyMinutes()); equal("15.0%",s.utilization());
        });
        test("allowance grants record reason", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            s.grantMinutes(admin(s),"S001",25,"Final research demo");
            equal(125,a.getAllowance().getAllocated());
            yes(s.getAllowanceHistory().get(0).contains("Final research demo"));
        });
        test("grant missing reason rejected", () -> {
            Scheduler s=ready(); student(s,"S001");
            error(IllegalArgumentException.class, () -> s.grantMinutes(admin(s),"S001",25,""));
        });
        test("set allocation to used plus reserved is valid", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); submit(s,a,"Large");
            s.setAllocation(admin(s),"S001",30); equal(0,a.getAllowance().getAvailable());
        });
        test("snapshot save and restore retains running", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Medium");
            advance(s,4);
            Path file=Files.createTempFile("gpu-test-", ".ser");
            try {
                Storage.save(s,file); Scheduler restored=Storage.load(file);
                equal(START.plusMinutes(4),restored.getNow());
                equal(11,restored.findJob(job.getJobId()).remainingRuntime(restored.getNow()));
                equal(1,restored.busyGpuCount());
                advance(restored,11);
                equal(Job.Status.COMPLETED,restored.findJob(job.getJobId()).getStatus());
                equal(15,restored.findStudent("S001").getAllowance().getUsed());
            } finally { Files.deleteIfExists(file); }
        });
        test("save restores pending and rejected requests", () -> {
            Scheduler s=ready();
            s.register("P001","Pending","p@ku.ac.ae","+971501234567","abcd");
            s.register("R001","Rejected","r@ku.ac.ae","+971501234567","abcd");
            s.reviewRequest(admin(s),"R001",false);
            Path file=Files.createTempFile("gpu-test-", ".ser");
            try { Storage.save(s,file); Scheduler r=Storage.load(file);
                equal("Pending",r.findRequest("P001").getStatus());
                equal("Rejected",r.findRequest("R001").getStatus());
            } finally { Files.deleteIfExists(file); }
        });
        test("corrupt save is reported", () -> {
            Path file=Files.createTempFile("gpu-corrupt-", ".ser");
            try { Files.writeString(file,"not a Java object stream");
                try { Storage.load(file); throw new AssertionError("Expected IOException"); }
                catch (java.io.IOException expected) { yes(expected.getMessage().contains("Cannot open")); }
            } finally { Files.deleteIfExists(file); }
        });
        test("missing file starts empty without data loss", () -> {
            Path file=Paths.get("data", "missing-test-file-do-not-create.ser");
            equal(0,Storage.load(file).getJobs().size());
        });
        test("sample has five approved, 20 jobs and four states", () -> {
            Scheduler s=SampleData.build();
            equal(5,s.getStudents().size()); equal(20,s.getJobs().size());
            for(Job.Status status:Job.Status.values()) yes(s.count(status)>0);
            equal(4,s.getGpus().size());
        });
        test("student job view filters other accounts", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"), b=student(s,"S002");
            submit(s,a,"Small"); submit(s,b,"Small");
            equal(1,s.getJobsFor(a).size()); equal("S001",s.getJobsFor(a).get(0).getStudentId());
        });
        test("running cancellation dispatches queued job instantly", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            Job first=submit(s,a,"Medium");
            for(int i=0;i<3;i++) submit(s,a,"Medium");
            Job waiting=submit(s,a,"Small");
            advance(s,4);
            s.cancelJob(admin(s),first.getJobId(),"Free capacity");
            equal(Job.Status.RUNNING,waiting.getStatus());
            equal(s.getNow(),waiting.getStartTime());
            equal(1,waiting.getAssignedGpuId());
            equal(4,first.getChargedMinutes());
        });
        test("predictions never allocate queued jobs", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job waiting=submit(s,a,"Small");
            s.refreshPredictions();
            equal(Job.Status.QUEUED,waiting.getStatus());
            equal(-1,waiting.getAssignedGpuId());
            equal(4,s.busyGpuCount());
        });
        test("invalid job fields and cancellation reason blocked", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            error(IllegalArgumentException.class, () -> s.submitJob(a,"","CUDA","Description","Small"));
            Job job=submit(s,a,"Small");
            error(IllegalArgumentException.class, () -> s.cancelJob(a,job.getJobId(),""));
            equal(Job.Status.RUNNING,job.getStatus());
        });
        test("saved queued jobs retain predictions and advance correctly", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for(int i=0;i<4;i++) submit(s,a,"Small");
            Job waiting=submit(s,a,"Medium");
            Path file=Files.createTempFile("gpu-queue-", ".ser");
            try {
                Storage.save(s,file); Scheduler r=Storage.load(file);
                Job restored=r.findJob(waiting.getJobId());
                equal(1,restored.getQueuePosition());
                equal(START.plusMinutes(5),restored.getPredictedStart());
                advance(r,5);
                equal(Job.Status.RUNNING,restored.getStatus());
            } finally { Files.deleteIfExists(file); }
        });
        // Additional late-stage edge cases and regression checks.
        test("missing registration fields show validation error", () -> {
            Scheduler s = empty();
            error(IllegalArgumentException.class,
                    () -> s.register(null,"Student","s@ku.ac.ae","+971501234567","abcd"));
            error(IllegalArgumentException.class,
                    () -> s.register("S001",null,"s@ku.ac.ae","+971501234567","abcd"));
            error(IllegalArgumentException.class,
                    () -> s.register("S001","Student","s@ku.ac.ae","+971501234567",null));
        });
        test("blank and too-long registration passwords are rejected", () -> {
            Scheduler s = empty();
            error(IllegalArgumentException.class, () -> s.register("S001","Student","s@ku.ac.ae","+971501234567","     "));
            error(IllegalArgumentException.class, () -> s.register("S001","Student","s@ku.ac.ae","+971501234567","a".repeat(81)));
        });
        test("oversize registration email is rejected", () -> {
            Scheduler s = empty();
            error(IllegalArgumentException.class, () -> s.register("S001","Student", "a".repeat(115)+"@ku.ac.ae","+971501234567","abcd"));
        });
        test("duplicate IDs ignore letter case", () -> {
            Scheduler s = empty();
            s.register("S001","Student","s@ku.ac.ae","+971501234567","abcd");
            error(IllegalArgumentException.class, () -> s.register("s001","Other","x@ku.ac.ae","+971500000000","abcd"));
        });
        test("invalid phone number is rejected", () -> {
            Scheduler s = empty();
            error(IllegalArgumentException.class, () -> s.register("S001","Student","s@ku.ac.ae","phone unknown","abcd"));
            error(IllegalArgumentException.class, () -> s.register("S001","Student","s@ku.ac.ae","+++++++","abcd"));
        });
        test("two simultaneous completions start next jobs in GPU ID order", () -> {
            Scheduler s = ready(); Student a = student(s,"S001");
            for (int i=0;i<4;i++) submit(s,a,"Small");
            Job fifth=submit(s,a,"Small"), sixth=submit(s,a,"Small");
            advance(s,5);
            equal(Job.Status.RUNNING,fifth.getStatus()); equal(1,fifth.getAssignedGpuId());
            equal(Job.Status.RUNNING,sixth.getStatus()); equal(2,sixth.getAssignedGpuId());
            equal(START.plusMinutes(5),fifth.getStartTime());
            equal(START.plusMinutes(5),sixth.getStartTime());
        });
        test("queue prediction respects ties and earlier queued jobs", () -> {
            Scheduler s = ready(); Student a=student(s,"S001");
            for (int i=0;i<4;i++) submit(s,a,"Small");
            Job[] wait = new Job[5];
            for (int i=0;i<5;i++) wait[i]=submit(s,a,"Small");
            for (int i=0;i<4;i++) {
                equal(i+1,wait[i].getQueuePosition());
                equal(START.plusMinutes(5),wait[i].getPredictedStart());
            }
            equal(START.plusMinutes(10),wait[4].getPredictedStart());
        });
        test("queued cancellation updates positions and leaves no GPU", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            for (int i=0;i<4;i++) submit(s,a,"Medium");
            Job fifth=submit(s,a,"Small"), sixth=submit(s,a,"Small");
            s.cancelJob(a,fifth.getJobId(),"Remove queued job");
            equal(Job.Status.CANCELLED,fifth.getStatus());
            equal(-1,fifth.getAssignedGpuId()); yes(fifth.getStartTime()==null);
            equal(1,sixth.getQueuePosition());
            equal(START.plusMinutes(15),sixth.getPredictedStart());
            equal(0,fifth.getChargedMinutes());
        });
        test("allocation history shows original and new values", () -> {
            Scheduler s=ready(); student(s,"S001");
            s.setAllocation(admin(s),"S001",130);
            String record=s.getAllowanceHistory().get(0);
            yes(record.contains("100 -> 130")); yes(record.contains("by admin"));
        });
        test("extra grant history includes reason and both totals", () -> {
            Scheduler s=ready(); student(s,"S001");
            s.grantMinutes(admin(s),"S001",30,"Supervisor approved");
            String record=s.getAllowanceHistory().get(0);
            yes(record.contains("100 -> 130")); yes(record.contains("Supervisor approved"));
        });
        test("save restores cancellation records and minute charges", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            Job job=submit(s,a,"Medium"); advance(s,4);
            s.cancelJob(admin(s),job.getJobId(),"Demo interruption");
            Path file=Files.createTempFile("gpu-cancel-", ".ser");
            try {
                Storage.save(s,file); Scheduler r=Storage.load(file);
                Job loaded=r.findJob(job.getJobId());
                equal(Job.Status.CANCELLED,loaded.getStatus());
                equal("admin",loaded.getCancelledBy());
                equal("Demo interruption",loaded.getCancellationReason());
                equal(START.plusMinutes(4),loaded.getCancellationTime());
                equal(4,loaded.getChargedMinutes());
                equal(4,r.findStudent("S001").getAllowance().getUsed());
                equal(0,r.findStudent("S001").getAllowance().getReserved());
            } finally { Files.deleteIfExists(file); }
        });
        test("students cannot edit allocations or advance clock", () -> {
            Scheduler s=ready(); Student a=student(s,"S001");
            error(SecurityException.class, () -> s.setAllocation(a,"S001",500));
            error(SecurityException.class, () -> s.grantMinutes(a,"S001",5,"not admin"));
            error(SecurityException.class, () -> s.advanceMinute(a));
        });
        test("running cancellation requires recorded reason", () -> {
            Scheduler s=ready(); Student a=student(s,"S001"); Job job=submit(s,a,"Medium");
            error(IllegalArgumentException.class, () -> s.cancelJob(a,job.getJobId(),"  "));
            equal(Job.Status.RUNNING,job.getStatus());
            equal(15,a.getAllowance().getReserved());
        });
        test("Job.compareTo orders by submission time, then job ID", () -> {
            Job a = new Job(1002, "S1", "t", "a", "d", "Small", START);
            Job b = new Job(1001, "S1", "t", "a", "d", "Small", START);
            Job c = new Job(1000, "S1", "t", "a", "d", "Small", START.plusMinutes(1));
            ArrayList<Job> list = new ArrayList<>(java.util.List.of(c, a, b));
            java.util.Collections.sort(list);
            equal(1001, list.get(0).getJobId());
            equal(1002, list.get(1).getJobId());
            equal(1000, list.get(2).getJobId());
        });
        test("canManage: admin any job, student only own jobs", () -> {
            Scheduler s = ready();
            Student one = student(s, "S001");
            Student two = student(s, "S002");
            Job job = submit(s, one, "Small");
            yes(admin(s).canManage(job));
            yes(one.canManage(job));
            yes(!two.canManage(job));
            error(SecurityException.class, () -> s.cancelJob(two, job.getJobId(), "not mine"));
        });
        test("demo data uses the five named students", () -> {
            Scheduler s = SampleData.build();
            equal("Ahmed Al Hammadi", s.findStudent("S001").getName());
            equal("Rashid Al Nuaimi", s.findStudent("S005").getName());
            yes(s.loginStudent("S003", "demo123") != null);
        });
        System.out.println("RESULT: " + passed + " passed; " + failed + " failed; total " + (passed+failed));
        if (failed != 0) System.exit(1);
    }
}
