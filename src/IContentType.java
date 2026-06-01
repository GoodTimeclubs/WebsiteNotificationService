// Strategy contract for deciding whether a MonitorEntry's content has changed between scans.
public interface IContentType {
    // Return true if the new scan differs from the last scan under this comparison strategy.
    boolean checkDifference(MonitorEntry entry);
}
