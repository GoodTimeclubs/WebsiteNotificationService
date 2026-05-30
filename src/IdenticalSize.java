public class IdenticalSize implements IContentType{

    @Override
    public boolean checkDifference(MonitorEntry entry) {
        return !(entry.getLastScan().length() == entry.getNewScan().length());
    }
}
