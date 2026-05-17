// Fans out a change-notification to every user subscribed to a MonitorEntry.
public class Notify {
    public void sendNotification(MonitorEntry entry){
        for(int i = 0; i < entry.subscribedUsers.length; i++){
            entry.getCommunicationChannel().send(entry.subscribedUsers[i], entry.getUrl());
        }
    }
}
