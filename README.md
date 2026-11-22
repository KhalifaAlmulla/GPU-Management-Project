# GPU Utilization Management (COSC230, Fall 2026)

A Java Swing program that simulates four NVIDIA H100 GPUs. Students register, the administrator
approves them and gives them GPU minutes, students submit jobs, and the program schedules the jobs
with one FCFS queue. Nothing runs on a real GPU.

Team: Khalifa Omar, Zayed Sultan, Abdulla Rashed

## How to run (Eclipse)

1. File > Import > General > Existing Projects into Workspace > select this folder > Finish.
2. Open `src/Main.java` and click Run.
3. Run `tests/TestRunner.java` to run the automated tests (no extra library needed).

Requires Java 17 or newer.

## How to run (command line)

```
mkdir out
javac -d out src/*.java tests/*.java
java -cp out TestRunner
java -cp out Main
```

## Logins

| Who | Username | Password |
|---|---|---|
| Administrator | admin | gpu2026 |
| Demo students | S001 to S005 | demo123 |

## Sample data

`data/system.ser` already contains the demo data: 5 approved students (Ahmed Al Hammadi,
Mohammed Al Ali, Saeed Al Mazrouei, Hamad Al Ketbi, Rashid Al Nuaimi), 2 pending requests,
1 rejected request, 4 GPUs and 20 jobs (queued, running, completed and cancelled).

To start from an empty system, close the program and delete `data/system.ser`.
On the next start the program asks whether to load the demo data again.

## Files

| File | What it does |
|---|---|
| `User`, `Student`, `Administrator` | Accounts (inheritance and polymorphism) |
| `AccessRequest` | A registration request (Pending, Approved, Rejected) |
| `Job`, `GPU`, `TimeAllowance` | A job, one GPU, and a student's minutes |
| `Identifiable` | Interface for objects with an ID |
| `Scheduler` | All rules: FCFS queue, Advance Minute, predictions, charges, permissions |
| `Storage` | Saves and loads everything to `data/system.ser` |
| `SampleData` | Builds the demo data |
| `Main`, `LoginFrame` | Starts the program; login and registration |
| `DashboardFrame`, `AdminFrame`, `StudentFrame` | The two dashboards (GUI only, no rules) |
| `tests/TestRunner` | 70 automated tests |
| `diagrams/` | UML class diagram and job state diagram |
