import java.io.IOException;
import java.security.NoSuchAlgorithmException;

// End-user of the service; holds contact data and manages their URL subscriptions.
public class User {
    private MonitorEntry[] subscribed;
    private String mailAddress;
    private String phone;

    public User(String mailAddress, String phone){
        this.mailAddress = mailAddress;
        this.phone = phone;
    }

    public String getMailAddress() {
        return mailAddress;
    }

    public void setMailAddress(String mailAddress) {
        this.mailAddress = mailAddress;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    // Subscribe this user to a URL and register the monitoring task with the scheduler.
    public void addSubscription(String url, Frequency freq, INotificationChannel channel) throws IOException, NoSuchAlgorithmException, InterruptedException {
        MonitorEntry entry = new MonitorEntry(url, freq, channel);
        entry.addUser(this);
        TaskScheduler.getInstance().addTask(entry);
    }

    // Cancel a subscription matching the given URL/frequency/channel triple.
    public void removeSubscription(String url, Frequency freq, INotificationChannel channel){
        MonitorEntry entry = new MonitorEntry(url, freq, channel);
        TaskScheduler.getInstance().removeTask(entry);
    }
}
