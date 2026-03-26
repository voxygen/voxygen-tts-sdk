package voxygen.speak;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.json.JSONException;
import org.json.JSONObject;

import voxygen.tts.Client;
import voxygen.url.data.Handler;

/** @noinspection SpellCheckingInspection **/
public class Main {
    @SuppressWarnings("unchecked")  // options Map values of class Object have unchecked cast to Boolean, String or List<String>
    public static void main(String[] argv) {
        // register support for URL data: scheme
        Handler.register();

        // URL to TTS server
        String url = "https://api.voxygen.fr/tts"; // may be overwritten by command line positional argument e.g. "https://localhost:8443/tts"
        // Credentials
        String token = "token_provided_by_voxygen"; // may be overwritten by --token|-t token

        // Default TTS parameters, may be overwritten by command line --param|-p key=value
        Map<String, String> ttsParameters = new HashMap<>();
        ttsParameters.put("text", "Enter some text.");
        ttsParameters.put("voice", "Jenny");
        ttsParameters.put("header", "wav-stream-header");

        // Command line
        final String defaultParams = ttsParameters.keySet().stream()
                .map(p -> "'" + p + "=" + ttsParameters.get(p) + "'")
                .collect(Collectors.joining(", "));
        final String usage = """
                usage: speak [-h] [-j] [[-p key=value ] ...] [-i filename] [-o filename] [-t token] [url]

                Client to TTS Server via HTTP(S)

                positional arguments:
                  url                   URL to TTS server (default:\s"""
                + url
                + """
                )

                optional arguments:
                  -h, --help            show this help message and exit
                  -j, --json            request JSON content type in response from server (default: audio)
                  -p key=value, --param key=value, ...
                                        set request parameters (default: ["""
                + defaultParams
                + """
                ])
                  -i filename           input file containing text to be read (takes precedence over -p text="Something to read." (default: None)
                  -o filename           output audio file (default: None)
                  -t token, --token token
                                        authorization token
                """;

        // parse command line arguments
        final Map<String, Object> options = new HashMap<>();
        try {
            final Pattern optRe = Pattern.compile("-([hjpiot])|--(h)elp|--(j)son|--(p)aram|--(t)oken");
            int index = 0;
            while (index < argv.length) {
                final String arg = argv[index++];
                Matcher m = optRe.matcher(arg);
                if (m.matches()) {
                    String shortOpt = null;
                    for (int g = 1; g <= m.groupCount(); g++) {
                        if (m.group(g) != null) {
                            shortOpt = m.group(g);
                            break;
                        }
                    }
                    assert shortOpt != null;
                    if ("hj".contains(shortOpt)) { // flag options => boolean
                        options.put(shortOpt, true);
                    } else { // options with a following argument
                        if (index >= argv.length || argv[index].startsWith("-")) {
                            throw new IllegalArgumentException(arg + " missing argument value");
                        }
                        if ("p".contains(shortOpt)) { // repeatable option => list
                            ((List<String>) options.computeIfAbsent(shortOpt, v -> new ArrayList<>(1))).add(argv[index++]);
                        } else { // non-repeatable option => string
                            if (options.containsKey(shortOpt)) {
                                throw new IllegalArgumentException(arg + " repeated argument");
                            } else {
                                options.put(shortOpt, argv[index++]);
                            }
                        }
                    }
                } else if (arg.startsWith("-")) {
                    throw new IllegalArgumentException(arg + " unknown argument");
                } else if (index != 0) {
                    index--;
                    break;
                }
            }
            if (index < argv.length) { // positional arguments
                url = argv[index++];
            }
            if (index < argv.length) {
                throw new IllegalArgumentException("too many positional arguments: " + argv[index] + " ...");
            }
        } catch (Exception error) {
            System.err.println("error: " + error);
            System.out.print(usage);
            System.exit(64);
        }

        // -help or -h requested from command line
        if (options.get("h") != null) {
            System.out.print(usage);
            System.exit(0);
        }

        // --token/-t token given optionally from command line
        final String opt_t = (String) options.get("t");
        if (opt_t != null)
            token = opt_t;

        // --param or -p key=value update dictionary of TTS parameters
        List<String> params = (List<String>) options.get("p");
        if (params != null) {
            for (String param : params) {
                String[] parts = param.split("=", 2);
                ttsParameters.put(parts[0], parts.length == 2 ? parts[1] : "");
            }
        }

        // -i file content overrides 'text' parameter
        final String opt_i = (String) options.get("i");
        if (opt_i != null) {
            try {
                byte[] in = Files.readAllBytes(Paths.get(opt_i));
                ttsParameters.put("text", new String(in, StandardCharsets.UTF_8));
            } catch (IOException error) {
                System.err.println("error: " + error);
                System.exit(1);
            }
        }

        // (optional) output audio file
        final String opt_o = (String) options.get("o");
        final Path outputFile = opt_o != null ? Paths.get(opt_o) : null;

        // Create a client instance
        try (Client client = new Client(token, url)) {
            // (optional) change retry policy
            //client.setRetryPolicy(2, 30);
            // (optional) change request content type
            //client.setRequestContentType(Client.MimeType.URL_ENCODED);
            if (options.get("j") != null) {
                // (optional) change preferred accepted content type
                client.setAcceptContentType(Client.MimeType.JSON);
            }

            // open client's connection
            client.open();

            // Build request query string from map of TTS parameters
            Map<String, String> ttsRequest = client.buildRequest(ttsParameters);
            System.out.println("Request: " + ttsRequest);

            client.send(ttsRequest);
            System.out.println("Received response status: " + client.getStatus() + " " + client.getReason());

            Client.MimeType ttsContentType = client.getContentType();
            System.out.println("Response content type: " + ttsContentType);

            switch (ttsContentType) {
                case AUDIO -> {
                    try (OutputStream fout = outputFile != null ? Files.newOutputStream(outputFile) : null) {
                        do {
                            byte[] data = client.readData();
                            if (data.length == 0) // end of connection input stream
                                break;
                            if (fout != null) {
                                fout.write(data);
                            } else {
                                System.out.println("  received chunk of audio data: " + data.length + " bytes");
                            }
                        } while (true);
                    }
                }
                case JSON -> {
                    JSONObject jsonReply = client.readJson();
                    if (jsonReply.has("url")) {
                        if (outputFile != null) {
                            try (InputStream faudio = new URI(jsonReply.getString("url")).toURL().openStream()) {
                                try (OutputStream fout = Files.newOutputStream(outputFile)) {
                                    fout.write(faudio.readAllBytes());
                                }
                            }
                        } else {
                            System.out.println(jsonReply.getString("url"));
                        }
                        System.out.println("  received audio signal, duration: " + jsonReply.getDouble("duration"));
                        System.out.println("  warnings: " + jsonReply.get("warnings"));
                    } else {
                        System.out.println(jsonReply);
                    }
                }
                default ->
                        System.out.println(new String(client.readAllData(), StandardCharsets.UTF_8));
            }
            // Display trailing headers
            for (Map.Entry<String, List<String>> entries : client.getTrailerFields().entrySet()) {
                System.out.println(entries.getKey() + ": " + String.join(", ", entries.getValue()));
            }
        } catch (IOException | NoSuchAlgorithmException | InvalidKeyException | JSONException | URISyntaxException e) {
            System.err.println("error: " + e);
        }
    }
}
