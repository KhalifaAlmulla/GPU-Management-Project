import java.io.Serializable;

/** Tracks allocated, used and reserved GPU minutes without negative balances. */
public class TimeAllowance implements Serializable {
    private static final long serialVersionUID = 1L;
    private int allocated, used, reserved;

    public TimeAllowance(int allocated) {
        if (allocated < 0) throw new IllegalArgumentException("Negative allocation.");
        this.allocated = allocated;
    }

    public int getAllocated() { return allocated; }
    public int getUsed() { return used; }
    public int getReserved() { return reserved; }
    public int getAvailable() { return allocated - used - reserved; }

    public void reserve(int minutes) {
        if (minutes <= 0 || minutes > getAvailable()) {
            throw new IllegalArgumentException("Not enough available GPU minutes.");
        }
        reserved += minutes;
    }

    /** Release the FULL reservation, and charge only time actually executed. */
    public void settle(int reservation, int actualRuntime) {
        if (reservation < 0 || actualRuntime < 0 || actualRuntime > reservation
                || reservation > reserved) {
            throw new IllegalArgumentException("Invalid allowance settlement.");
        }
        reserved -= reservation;
        used += actualRuntime;
    }

    public void setAllocated(int minutes) {
        // Already-used minutes and outstanding reservations must remain funded.
        if (minutes < used + reserved) {
            throw new IllegalArgumentException("Allocation cannot be less than used + reserved.");
        }
        allocated = minutes;
    }

    public void grant(int extra) {
        if (extra <= 0 || extra > Integer.MAX_VALUE - allocated) {
            throw new IllegalArgumentException("Grant must be positive and within range.");
        }
        allocated += extra;
    }
}
