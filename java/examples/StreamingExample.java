package examples;

import voxygen.tts.Client;

import java.util.HashMap;
import java.util.Map;

public class StreamingExample {

    public static void main(String[] args) {
        // TTS request parameters
        Map<String, String> parameters = new HashMap<>();
        parameters.put("voice", "Mary"); // Select a voice available on your subscription
        parameters.put("language", "en-US");
        parameters.put("text", "Hello. How can I help you today ?"); // Text to synthesize
        parameters.put("header", "headerless");

        try (Client client = new Client("YOUR_TOKEN", "")) {

            client.open();

            Map<String, String> request = client.buildRequest(parameters);
            client.send(request);

            byte[] chunk;
            while ((chunk = client.readData()).length > 0) {
                System.out.println("received chunk of audio data: " + chunk.length + " bytes");
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
