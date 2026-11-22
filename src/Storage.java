import java.io.*;
import java.nio.file.*;

/** Writes one complete snapshot. Missing files start a new project; corrupt ones fail clearly. */
public class Storage {
    public static Scheduler load(Path file) throws IOException {
        if (!Files.exists(file)) return new Scheduler();
        try (ObjectInputStream stream = new ObjectInputStream(Files.newInputStream(file))) {
            Object value = stream.readObject();
            if (!(value instanceof Scheduler)) throw new IOException("Not a GPU project save file.");
            Scheduler result = (Scheduler) value;
            if (result.getGpus().size() != 4) throw new IOException("Saved GPU count is invalid.");
            result.refreshPredictions();
            return result;
        } catch (ClassNotFoundException | ClassCastException e) {
            throw new IOException("Cannot read save file: incompatible data.", e);
        } catch (IOException e) {
            throw new IOException("Cannot open save file '" + file + "': " + e.getMessage(), e);
        }
    }
    public static void save(Scheduler scheduler, Path file) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "gpu-save-", ".tmp");
        try {
            try (ObjectOutputStream stream = new ObjectOutputStream(Files.newOutputStream(temporary))) {
                stream.writeObject(scheduler);
            }
            try {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }
}
