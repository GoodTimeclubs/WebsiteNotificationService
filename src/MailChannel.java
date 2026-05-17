// Notification channel that delivers via e-mail (currently stubbed to console output).
public class MailChannel implements INotificationChannel {
    @Override
    public void send(User user, String url) {
        System.out.println("Empfänger: " + user.getMailAddress());
        System.out.println("Änderung an der Website: " + url);
        System.out.println("Benachrichtigung über Mail channel");
    }
}
