import java.time.Instant;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;

// One watched URL: scan settings, last/new response bodies, and the users to notify on change.
public class MonitorEntry {
    private final String url;
    private final Frequency freq;

    private final IContentType checkType;
    private String lastScan;
    private String newScan;
    public volatile Instant lastChecked;        // written by scan workers, read by the scheduler loop
    public volatile User[] subscribedUsers;     // copy-on-write: replaced as a whole, never mutated in place

    // guard so only one worker scans this entry at a time (see TaskScheduler.scanAndcheck)
    public final AtomicBoolean scanning = new AtomicBoolean(false);

    public MonitorEntry(String url, Frequency freq, IContentType checkType){
        this.url = url;
        this.freq = freq;
        this.checkType = checkType;
        this.subscribedUsers = new User[0];
        this.lastChecked = Instant.MIN;
    }

    public String getUrl() {
        return url;
    }

    public Frequency getFreq() {
        return freq;
    }

    public String getLastScan() {
        return lastScan;
    }

    public void setLastScan(String lastScan) {
        this.lastScan = lastScan;
    }

    public String getNewScan() {
        return newScan;
    }

    public void setNewScan(String newScan) {
        this.newScan = newScan;
    }

    public IContentType getCheckType() {
        return checkType;
    }
    
    // Append a subscriber to the notification list (copy-on-write: build a full copy, then publish it).
    public void addUser(User user){
        User[] copy = Arrays.copyOf(subscribedUsers, subscribedUsers.length + 1);
        copy[copy.length-1] = user;
        subscribedUsers = copy;   // publish only once fully built, so readers never see a half-filled array
    }
    
    // Remove a subscriber (matched by mail address); throws if not found.
    // Copy-on-write: shifts within a fresh copy and never touches the live array readers may be iterating.
    public void removeUser(User user) throws Exception {
        int index = -1;
        for (int i = 0; i < subscribedUsers.length; i++) {
            if (subscribedUsers[i].getMailAddress().equals(user.getMailAddress())) {
                index = i;
                break;
            }
        }

        if (index != -1){
            User[] copy = Arrays.copyOf(subscribedUsers, subscribedUsers.length);
            for (int i = 0; i < copy.length - index-1; i++){
                copy[i+index] = copy[i+index+1];
            }
            copy = Arrays.copyOf(copy, copy.length-1);
            subscribedUsers = copy;
        }
        else {
            throw new Exception("The user you are trying to delete was not found.");
        }
    }

    // Notify every subscribed user that this entry's URL has changed (observer pattern).
    // Runs on a scan worker thread; iterates the volatile subscribedUsers snapshot.
    public void notifyObservers(){
        for (User subscribedUser : subscribedUsers) {
            subscribedUser.update(this.url);
        }
    }

    public int getUsercount(){
        return subscribedUsers.length;
    }
}
