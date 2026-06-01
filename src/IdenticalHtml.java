import java.util.Objects;

// Change-detection strategy: byte-for-byte comparison of the full raw HTML response.
public class IdenticalHtml implements IContentType{
    @Override
    public boolean checkDifference(MonitorEntry entry) {
        return !Objects.equals(entry.getLastScan(), entry.getNewScan());
    }
}
