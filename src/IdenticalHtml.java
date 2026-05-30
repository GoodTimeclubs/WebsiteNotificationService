import java.util.Objects;

public class IdenticalHtml implements IContentType{
    @Override
    public boolean checkDifference(MonitorEntry entry) {
        return !Objects.equals(entry.getLastScan(), entry.getNewScan());
    }
}
