import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;


// Singleton scheduler that periodically scans every registered MonitorEntry per its Frequency.
// The polling loop runs on one background thread and dispatches each due scan to its own worker thread.
public class TaskScheduler {
    private MonitorEntry[] registeredTasks;
    // volatile: shared between caller threads and the polling-loop thread.
    public volatile boolean stop = false;
    public volatile boolean running = false;

    private static TaskScheduler instance;
    private final Object lock = new Object();   // guards the registeredTasks array

    private TaskScheduler() {
        this.registeredTasks = new MonitorEntry[0];
    }

    // Thread-safe lazy singleton accessor (synchronized to avoid duplicate instances).
    public static synchronized TaskScheduler getInstance() {
        if (instance == null) {
            instance = new TaskScheduler();
        }
        return instance;
    }

    // Register a new monitor entry; auto-starts the scan loop on the first task.
    public int addTask(MonitorEntry entry) {
        int lenmin1;
        synchronized (lock) {
            registeredTasks = Arrays.copyOf(registeredTasks, registeredTasks.length + 1);
            lenmin1 = registeredTasks.length - 1;
            registeredTasks[lenmin1] = entry;

        }
        if (!running && !stop) {
            running = true;
            Thread loop = new Thread(() -> {
                try {
                    this.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            //loop.setDaemon(true);
            loop.start();
        }
        return lenmin1;
    }

    // Remove the registered task at the given index, shifting the remaining entries left.
    public void  removeTask(int index){
        synchronized(lock) {
            if (index != -1) {
                for (int i = 0; i < registeredTasks.length - index-1; i++) {
                    registeredTasks[i + index] = registeredTasks[i + index + 1];
                }

                registeredTasks = Arrays.copyOf(registeredTasks, registeredTasks.length - 1);
            }
        }
    }

    // Read-only snapshot of the currently registered tasks, for UI/inspection consumers.
    // Returns a copy taken under the lock so callers never observe a stale or mid-mutation array
    // (registeredTasks itself is not volatile and is rebuilt copy-on-write under this lock).
    public MonitorEntry[] getRegisteredTasks() {
        synchronized (lock) {
            return Arrays.copyOf(registeredTasks, registeredTasks.length);
        }
    }

    // Scan one entry, refresh its stored content, and notify subscribers if it changed.
    // Guarded so the same entry is never scanned by two threads at once.
    public void scanAndcheck(MonitorEntry entry) throws IOException, InterruptedException {
        // skip if a scan for this entry is already in flight; the winner releases the flag in finally
        if (!entry.scanning.compareAndSet(false,true)) {
            return;
        }
        try {
            GetWebsite scanner = new GetWebsite();
            CheckDifference check = new CheckDifference();
            scanner.startScanner(entry);
            entry.lastChecked = Instant.now();
            if (check.checkHashDifference(entry)) {
                entry.notifyObservers();
            }
        } finally {
            entry.scanning.set(false);
        }


    }

    // Main loop: polls each task every second and dispatches a scan (on its own thread) once a
    // task's frequency interval has elapsed; never scans inline, so one slow fetch can't stall the loop.
    public void start() throws InterruptedException {
        while (!stop) {
            MonitorEntry[] snapshot;
            synchronized (lock) {
                snapshot = registeredTasks;   //save to avoid races
            }

            for (MonitorEntry entry : snapshot) {
                if (entry.getFreq() == Frequency.high && Duration.between(entry.lastChecked, Instant.now()).toMinutes() > 1) {
                    startScanThread(entry);
                }
                if (entry.getFreq() == Frequency.mid && Duration.between(entry.lastChecked, Instant.now()).toHours() > 1) {
                    startScanThread(entry);
                }
                if (entry.getFreq() == Frequency.low && Duration.between(entry.lastChecked, Instant.now()).toHours() > 5) {
                    startScanThread(entry);
                }
                //System.out.println("Task execution checked for " + registeredTasks.length + " active Tasks.");
            }
            Thread.sleep(1000);
            
        }
        running = false;

    }

    // Bump lastChecked up front so the 1s poll won't re-trigger this entry while its scan is in flight,
    // then run the scan on a dedicated thread. A failed scan only ends its own worker; the loop keeps going.
    private void startScanThread (MonitorEntry entry){
        entry.lastChecked = Instant.now();
        new Thread(() -> {
            try {
                scanAndcheck(entry);
            } catch (IOException e) {
                // per-scan failure (e.g. site unreachable): log and let this worker end
                System.err.println("Scan failed for " +entry.getUrl());
                e.printStackTrace();
            }catch (InterruptedException e){
                // restore the interrupt flag that catching cleared
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // Return the index of a task matching url and frequency, or -1 if none exists.
    public int existingTask(String url, Frequency freq,  IContentType content){
        synchronized(lock) {
            for (int i = 0; i < registeredTasks.length; i++) {
                if (Objects.equals(registeredTasks[i].getUrl(), url) && Objects.equals(registeredTasks[i].getFreq(), freq)  && Objects.equals(registeredTasks[i].getCheckType(), content)){
                    return i;
                }
            }
        }
        return -1;
    }

    // Subscribe a user to a url: reuse the matching task if it exists, otherwise create one.
    public void addSubscription(String url, Frequency freq, User user, IContentType content) {
        int posExisting = existingTask(url, freq, content);
        synchronized(lock) {
            if (posExisting >= 0) {
                registeredTasks[posExisting].addUser(user);
            } else {
                int idx = addTask(new MonitorEntry(url, freq, content));
                registeredTasks[idx].addUser(user);
            }
        }
    }

    // Unsubscribe a user from a url; drops the whole task once it has no subscribers left.
    public void removeSubscription(String url, Frequency freq, User user, IContentType content) throws Exception {
        int index = existingTask(url, freq, content);
        synchronized(lock) {
            if (registeredTasks[index].getUsercount() >= 1) {
                registeredTasks[index].removeUser(user);
            }

            if (registeredTasks[index].getUsercount() == 0) {
                removeTask(index);
            }
        }
    }
}
