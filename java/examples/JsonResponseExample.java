package examples;

import voxygen.tts.Client;
import voxygen.tts.Client.MimeType;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.FileOutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

public class JsonResponseExample {

    public static void main(String[] args) {

        try {
            Map<String, String> parameters = new HashMap<>();
            parameters.put("voice", "Mary"); // Select a voice available on your subscription
            parameters.put("language", "en-US");
            parameters.put("text", "Hello. How can I help you today ?"); // Text to synthesize
            parameters.put("header", "wav-header");
            parameters.put("event", "2"); // Returned events information level

            Client client = new Client("YOUR_TOKEN", "");

            client.setAcceptContentType(MimeType.JSON);
            client.open();

            Map<String, String> request = client.buildRequest(parameters);
            client.send(request);

            JSONObject payload = client.readJson();

            // Display JSON response
            System.out.println("Duration: " + payload.opt("duration"));
            System.out.println("Warnings: " + payload.opt("warnings"));

            System.out.println("Events (first 5):");
            JSONArray events = payload.optJSONArray("events");
            if (events != null) {
                for (int i = 0; i < Math.min(events.length(), 5); i++) {
                    System.out.println(events.get(i));
                }
            }

            // Optional: Download the generated audio
            String audioUrl = payload.optString("url", null);
            try (var in = new URI(audioUrl).toURL().openStream();
                    var fos = new FileOutputStream("output.wav")) {
                in.transferTo(fos);
            }
            System.out.println("Audio saved to output.wav");

        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}
