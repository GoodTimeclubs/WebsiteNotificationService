// Entry point: wires up three sample users (Mail/SMS/WhatsApp) with high-frequency subscriptions
// covering each change-detection strategy (HTML, text, size), then hands off to the scheduler.
void main(String[] args){
    TaskScheduler scheduler = TaskScheduler.getInstance();
    if (args.length > 0 && !args[0].isBlank()) {
        User arg = new User("test@mail.com", "+123456789", new MailChannel());
        scheduler.addSubscription(args[0].trim(), Frequency.high, arg, new IdenticalHtml());
    }else {

        User test1 = new User("test@mail.com", "+123456789", new MailChannel());
        User test2 = new User("teswt@mail.com", "+1234565789", new SmsChannel());
        User test3 = new User("tessdt@mail.com", "+12345646789", new WhatsAppChannel());

        scheduler.addSubscription("https://bengutzeit.de", Frequency.high, test1, new IdenticalHtml());
        scheduler.addSubscription("https://www.tagesschau.de/", Frequency.high, test2, new IdenticalText());
        scheduler.addSubscription("https://de.wikipedia.org/wiki/Tagesschau_(ARD)", Frequency.high, test3, new IdenticalSize());
    }
}
