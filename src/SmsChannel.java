// Notification channel that delivers via SMS (currently stubbed to console output).
public class SmsChannel implements INotificationChannel {
    @Override
    public void send(User user, String url) {
        System.out.println("Empfänger: " + user.getPhone());
        System.out.println("Änderung an der Website: " + url);
        System.out.println("Benachrichtigung über Sms channel");
    }
}
