import java.io.IOException;
import java.security.NoSuchAlgorithmException;

// End-user of the service; holds contact data and manages their URL subscriptions.
public class User {
    private String mailAddress;
    private String phone;
    INotificationChannel notChan;

    public User(String mailAddress, String phone, INotificationChannel notChan){
        this.mailAddress = mailAddress;
        this.phone = phone;
        this.notChan = notChan;
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

    public void update(String url){
        notChan.send(this,url);
    }

}
