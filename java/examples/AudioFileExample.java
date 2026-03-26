package examples;

import voxygen.tts.Client;

import java.io.FileOutputStream;
import java.util.HashMap;
import java.util.Map;

public class AudioFileExample {

    public static void main(String[] args) {

        Map<String, String> parameters = new HashMap<>();
        parameters.put("voice", "Mary"); // Select a voice available on your subscription
        parameters.put("language", "en-US");
        parameters.put("text", "Hello. How can I help you today ?"); // Text to synthesize
        parameters.put("header", "wav-header");

        try (Client client = new Client("YOUR_TOKEN", "")) {

            client.open();

            Map<String, String> request = client.buildRequest(parameters);
            client.send(request);

            byte[] audio = client.readAllData();

            try (FileOutputStream fos = new FileOutputStream("output.wav")) {
                fos.write(audio);
            }

            System.out.println("Audio saved to output.wav");

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
