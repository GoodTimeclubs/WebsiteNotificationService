import java.time.Instant;
import java.util.Arrays;

// One watched URL: scan settings, last/new content hashes, and the users to notify on change.
public class MonitorEntry {
    private String url;
    private Frequency freq;
    private String lastScanHash;
    private String newScanHash;
    public Instant lastChecked;
    public User[] subscribedUsers;

    public MonitorEntry(String url, Frequency freq){
        this.url = url;
        this.freq = freq;
        this.subscribedUsers = new User[0];
        this.lastChecked = Instant.MIN;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public Frequency getFreq() {
        return freq;
    }

    public void setFreq(Frequency freq) {
        this.freq = freq;
    }


    public String getLastScanHash() {
        return lastScanHash;
    }

    public void setLastScanHash(String lastScanHash) {
        this.lastScanHash = lastScanHash;
    }

    public String getNewScanHash() {
        return newScanHash;
    }

    public void setNewScanHash(String newScanHash) {
        this.newScanHash = newScanHash;
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
            for (int i = 0; i < subscribedUsers.length - index; i++){
                subscribedUsers[i+index] = subscribedUsers[i+index+1];
            }

            subscribedUsers = Arrays.copyOf(subscribedUsers, subscribedUsers.length-1);
        }
        else {
            throw new Exception("The user you are trying to delete was not found.");
        }
    }

    public void notifyObservers(){
        for (int i = 0; i < subscribedUsers.length;i++){
            subscribedUsers[i].update(this.url);
        }
    }

    public int getUsercount(){
        return subscribedUsers.length;
    }
}
