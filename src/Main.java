// Entry point: wires up a sample user with one high-frequency mail subscription.
void main(){
    User test1 = new User("test@mail.com","+123456789",new  MailChannel());
    User test2 = new User("teswt@mail.com","+1234565789",new SmsChannel());
    User test3 = new User("tessdt@mail.com","+12345646789",new WhatsAppChannel());



    TaskScheduler scheduler = TaskScheduler.getInstance();

    scheduler.addSubscription("http://bengutzeit.de",Frequency.high,test1);
    scheduler.addSubscription("https://www.tagesschau.de/",Frequency.high,test2);
    scheduler.addSubscription("https://de.wikipedia.org/wiki/Tagesschau_(ARD)",Frequency.high,test3);

}
