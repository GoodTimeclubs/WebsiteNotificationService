// Delegates to the entry's IContentType strategy to decide whether a page changed.
public class CheckDifference {

    // Returns true if the page changed since the last scan; false on the first (baseline) scan.
    public boolean checkHashDifference(MonitorEntry entry){
        if (entry.getLastScan() == null) {
            entry.setLastScan(entry.getNewScan());
            System.out.println("First scan for " + entry.getUrl());
            return false;
        }

        if(entry.getCheckType().checkDifference(entry)){

            System.out.println("Difference fond for url" + entry.getUrl());

            return true;
        }

        System.out.println("Website " + entry.getUrl() + " has not changed!");

        return false;
    }
}
