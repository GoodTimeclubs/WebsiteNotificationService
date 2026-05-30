import org.jsoup.Jsoup;
import java.util.Objects;

// Change-detection strategy: compares visible text only (HTML stripped via Jsoup).
public class IdenticalText implements IContentType {

    @Override
    public boolean checkDifference(MonitorEntry entry) {

        String oldText = Jsoup.parse(entry.getLastScan()).text();
        String newText = Jsoup.parse(entry.getNewScan()).text();

        return !Objects.equals(oldText, newText);
    }
}
