import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;


// Singleton scheduler that periodically scans every registered MonitorEntry per its Frequency.
public class TaskScheduler {
    private MonitorEntry[] registeredTasks;
    public boolean stop = false;
    public boolean running = false;

    private static TaskScheduler instance;

    private TaskScheduler() {
        this.registeredTasks = new MonitorEntry[0];
    }

    // Lazy singleton accessor (not thread-safe — fine for current single-threaded use).
    public static TaskScheduler getInstance() {
        if (instance == null) {
            instance = new TaskScheduler();
        }
        return instance;
    }

    // Register a new monitor entry; auto-starts the scan loop on the first task.
    public void addTask(MonitorEntry entry) throws IOException, NoSuchAlgorithmException, InterruptedException {
        registeredTasks = Arrays.copyOf(registeredTasks, registeredTasks.length + 1);
        registeredTasks[registeredTasks.length-1] = entry;
        if(running == false && stop == false){
            this.start();
            running = true;
        }
    }

    // Remove a registered task that matches URL, frequency and channel.
    public void removeTask(MonitorEntry entry){
        int index = -1;
        for (int i = 0; i < registeredTasks.length; i++) {
            if (registeredTasks[i].getUrl().equals(entry.getUrl()) && registeredTasks[i].getFreq() == entry.getFreq() && registeredTasks[i].getCommunicationChannel().equals(entry.getCommunicationChannel())) {
                index = i;
                break;
            }
        }

        if (index != -1){
            for (int i = 0; i < registeredTasks.length - index; i++){
                registeredTasks[i+index] = registeredTasks[i+index+1];
            }

            registeredTasks = Arrays.copyOf(registeredTasks, registeredTasks.length-1);
        }
    }

    // Main loop: polls each task and triggers a scan once its frequency interval elapsed.
    public void start() throws IOException, NoSuchAlgorithmException, InterruptedException {
        GetWebsite scaner = new GetWebsite();
        while (!stop) {
            Instant now = Instant.now();

            for (int i = 0; i < registeredTasks.length; i++) {
                if (registeredTasks[i].getFreq() == Frequency.high && Duration.between(registeredTasks[i].lastChecked, now).toMinutes() > 1) {
                    scaner.startScanner(registeredTasks[i]);
                    registeredTasks[i].lastChecked = now;
                }
                if (registeredTasks[i].getFreq() == Frequency.mid && Duration.between(registeredTasks[i].lastChecked, now).toHours() > 1) {
                    scaner.startScanner(registeredTasks[i]);
                    registeredTasks[i].lastChecked = now;
                }
                if (registeredTasks[i].getFreq() == Frequency.low && Duration.between(registeredTasks[i].lastChecked, now).toHours() > 5) {
                    scaner.startScanner(registeredTasks[i]);
                    registeredTasks[i].lastChecked = now;
                }
            //System.out.println(now + "Task execution checked for " + registeredTasks.length + " active Tasks.");
                Thread.sleep(1000);
            }
        }
        running = false;

    }
}
