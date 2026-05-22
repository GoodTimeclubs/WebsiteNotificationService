
// End-user of the service; holds contact data and manages their URL subscriptions.
public class User {
    private final String mailAddress;
    private final String phone;
    INotificationChannel notChan;

    public User(String mailAddress, String phone, INotificationChannel notChan){
        this.mailAddress = mailAddress;
        this.phone = phone;
        this.notChan = notChan;
    }

    public String getMailAddress() {
        return mailAddress;
    }

    public String getPhone() {
        return phone;
    }

    // Observer callback: deliver a change-notification for url via this user's channel.
    public void update(String url){
        notChan.send(this,url);
    }

}
