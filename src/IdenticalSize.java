// Change-detection strategy: flags a change only when the response length differs.
public class IdenticalSize implements IContentType{

    @Override
    public boolean checkDifference(MonitorEntry entry) {
        return !(entry.getLastScan().length() == entry.getNewScan().length());
    }
}
