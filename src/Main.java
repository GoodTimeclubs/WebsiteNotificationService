// Entry point: wires up a sample user with one high-frequency mail subscription.
void main() {
    User Test = new User("test@mail.com","+123456789");
    try {
        Test.addSubscription("http://bengutzeit.de/",Frequency.high,new MailChannel());
    } catch (IOException | NoSuchAlgorithmException | InterruptedException e) {
        throw new RuntimeException(e);
    }

}
