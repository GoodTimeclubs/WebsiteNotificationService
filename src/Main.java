// Entry point: wires up a sample user with one high-frequency mail subscription.
void main() throws IOException, NoSuchAlgorithmException, InterruptedException {
    User Test = new User("test@mail.com","+123456789",new  MailChannel());

    TaskScheduler scheduler = TaskScheduler.getInstance();

    scheduler.addSubscription("http://bengutzeit.de",Frequency.high,Test);

}
