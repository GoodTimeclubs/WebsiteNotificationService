// Common contract for delivery channels (Mail, SMS, WhatsApp, ...).
public interface INotificationChannel {
    // Deliver a change-notification for the given URL to the given user.
    public void send(User user, String url);
}
