import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;


// Fetches a URL via HTTP and stores body for change detection.
public class GetWebsite {

    // Performs one scan: GET the URL, hand off to CheckDifference.
    public void startScanner(MonitorEntry toscan) throws IOException, InterruptedException{
        System.out.println("Starting scan for url: " + toscan.getUrl());
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(toscan.getUrl()))
                .GET()
                .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() == 200) {
            toscan.setLastScan(toscan.getNewScan());

            toscan.setNewScan(String.valueOf(response));
        }
        System.out.println("Scan completed. Got code " + response.statusCode());

    }
}





