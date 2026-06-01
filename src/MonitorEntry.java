import java.time.Instant;
import java.util.Arrays;

// One watched URL: scan settings, last/new response bodies, and the users to notify on change.
public class MonitorEntry {
    private final String url;
    private final Frequency freq;



    private final IContentType checkType;
    private String lastScan;
    private String newScan;
    public Instant lastChecked;
    public User[] subscribedUsers;

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
    
    // Append a subscriber to the notification list.
    public void addUser(User user){
        subscribedUsers = Arrays.copyOf(subscribedUsers, subscribedUsers.length + 1);
        subscribedUsers[subscribedUsers.length-1] = user;
    }
    
    // Remove a subscriber (matched by mail address); throws if not found.
    public void removeUser(User user) throws Exception {
        int index = -1;
        for (int i = 0; i < subscribedUsers.length; i++) {
            if (subscribedUsers[i].getMailAddress().equals(user.getMailAddress())) {
                index = i;
                break;
            }
        }

        if (index != -1){
            for (int i = 0; i < subscribedUsers.length - index-1; i++){
                subscribedUsers[i+index] = subscribedUsers[i+index+1];
            }

            subscribedUsers = Arrays.copyOf(subscribedUsers, subscribedUsers.length-1);
        }
        else {
            throw new Exception("The user you are trying to delete was not found.");
        }
    }

    // Notify every subscribed user that this entry's URL has changed (observer pattern).
    public void notifyObservers(){
        for (User subscribedUser : subscribedUsers) {
            subscribedUser.update(this.url);
        }
    }

    public int getUsercount(){
        return subscribedUsers.length;
    }
}
