import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;


// Singleton scheduler that periodically scans every registered MonitorEntry per its Frequency.
public class TaskScheduler {
    private MonitorEntry[] registeredTasks;
    public volatile boolean stop = false;
    public volatile boolean running = false;

    private static TaskScheduler instance;
    private final Object lock = new Object();

    private TaskScheduler() {
        this.registeredTasks = new MonitorEntry[0];
    }

    // Lazy singleton accessor (not thread-safe — fine for current single-threaded use).
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
        if (running == false && stop == false) {
            running = true;
            Thread loop = new Thread(() -> {
                try {
                    this.start();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            });
            loop.setDaemon(true);
            loop.start();
        }
        return lenmin1;
    }

    // Remove a registered task that matches URL, frequency and channel.
    public void  removeTask(int index){
        synchronized(lock) {
            if (index != -1) {
                for (int i = 0; i < registeredTasks.length - index; i++) {
                    registeredTasks[i + index] = registeredTasks[i + index + 1];
                }

                registeredTasks = Arrays.copyOf(registeredTasks, registeredTasks.length - 1);
            }
        }
    }

    public void scanAndcheck(MonitorEntry entry) throws IOException, NoSuchAlgorithmException, InterruptedException {
        GetWebsite scaner = new GetWebsite();
        CheckDifference check = new CheckDifference();

        scaner.startScanner(entry);
        entry.lastChecked = Instant.now();
        if (check.checkHashDifference(entry)) {
            entry.notifyObservers();
        }

    }

    // Main loop: polls each task and triggers a scan once its frequency interval elapsed.
    public void start() throws IOException, NoSuchAlgorithmException, InterruptedException {
        while (!stop) {
            MonitorEntry[] snapshot;
            synchronized (lock) {
                snapshot = registeredTasks;   //save to avoid races
            }

            for (int i = 0; i < snapshot.length; i++) {
                if (snapshot[i].getFreq() == Frequency.high && Duration.between(snapshot[i].lastChecked, Instant.now()).toMinutes() > 1) {
                    scanAndcheck(snapshot[i]);
                }
                if (snapshot[i].getFreq() == Frequency.mid && Duration.between(snapshot[i].lastChecked, Instant.now()).toHours() > 1) {
                    scanAndcheck(snapshot[i]);
                }
                if (snapshot[i].getFreq() == Frequency.low && Duration.between(snapshot[i].lastChecked, Instant.now()).toHours() > 5) {
                    scanAndcheck(snapshot[i]);
                }
                //System.out.println(now + "Task execution checked for " + registeredTasks.length + " active Tasks.");
            }
            Thread.sleep(1000);
            
        }
        running = false;

    }

    public int existingTask(String url, Frequency freq){
        synchronized(lock) {
            for (int i = 0; i < registeredTasks.length; i++) {
                if (Objects.equals(registeredTasks[i].getUrl(), url) && Objects.equals(registeredTasks[i].getFreq(), freq)) {
                    return i;
                }
            }
        }
        return -1;
    }

    public void addSubscription(String url, Frequency freq, User user) throws IOException, NoSuchAlgorithmException, InterruptedException {
        int posExisting = existingTask(url, freq);
        synchronized(lock) {
            if (posExisting >= 0) {
                registeredTasks[posExisting].addUser(user);
            } else {
                registeredTasks[addTask(new MonitorEntry(url, freq))].addUser(user);
            }
        }
    }

    public void removeSubscription(String url, Frequency freq, User user) throws Exception {
        int index = existingTask(url, freq);
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
