// Compares stored vs. new content hash and triggers a notification on change.
public class CheckDifference {

    // Returns true if the page changed since last scan (and fires the notification).
    public boolean checkHashDifference(MonitorEntry entry){
        if (entry.getLastScanHash() == null) {
            entry.setLastScanHash(entry.getNewScanHash());
            System.out.println("First scan for " + entry.getUrl() + " — baseline stored, no notification.");
            return false;
        }
        if(!(entry.getLastScanHash().equals(entry.getNewScanHash()))){
            Notify not = new Notify();
            not.sendNotification(entry);

            System.out.println("Difference fond for url" + entry.getUrl());

            return true;
        }

        System.out.println("Website " + entry.getUrl() + " has not changed!");

        return false;
    }
}
