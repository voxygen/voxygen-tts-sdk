package voxygen.tts;

import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetAddress;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.json.JSONException;
import org.json.JSONObject;

/** @noinspection SpellCheckingInspection **/
public class Client implements AutoCloseable {
    public enum MimeType {
        PLAIN_TEXT,
        AUDIO,
        JSON,
        URL_ENCODED
    }

    private final String _token;
    private final URI _uri;
    private int _max_retries;
    private int _retry_after; // seconds
    private HttpURLConnection _connection = null;
    private MimeType _body_type;
    private MimeType _accept_type;

    public Client(String token, String uri) throws IllegalArgumentException, MalformedURLException, URISyntaxException {
        if (token == null || token.isEmpty()) {
            throw new IllegalArgumentException("token must be provided");
        }
        _token = token;
        URI baseURI = new URI("https://api.voxygen.fr/tts/");
        if (uri == null) {
            _uri = baseURI;
        } else {
            _uri = baseURI.resolve(uri);
        }
        // default retry policy : 6 times with a 10 second wait
        setRetryPolicy(6, 10);
        // default request content type : JSON
        setRequestContentType(MimeType.JSON);
        // default accept content type : AUDIO
        setAcceptContentType(MimeType.AUDIO);
    }

    public final void setRetryPolicy(int max_retries, int retry_after) {
        _max_retries = max_retries;
        _retry_after = retry_after;
    }

    public final void setRequestContentType(MimeType mime_type) {
        _body_type = mime_type;
    }

    public final void setAcceptContentType(MimeType mime_type) {
        _accept_type = mime_type;
    }

    public void open() throws IllegalArgumentException, IOException, URISyntaxException {
        if (_connection != null) {
            return;
        }
        if ("https".equalsIgnoreCase(_uri.getScheme())) {
            _connection = (HttpsURLConnection) _uri.toURL().openConnection();
            try {
                InetAddress ip = InetAddress.getByName(_uri.getHost());
                if (ip.isSiteLocalAddress() || ip.isLoopbackAddress()) {
                    disableCertificateValidation((HttpsURLConnection) _connection);
                }
            } catch (UnknownHostException | NoSuchAlgorithmException | KeyManagementException e) {
                // Do nothing
            }
        } else if ("http".equalsIgnoreCase(_uri.getScheme())) {
            _connection = (HttpURLConnection) _uri.toURL().openConnection();
        } else {
            throw new IllegalArgumentException("Unsupported URL scheme \"" + _uri.getScheme() + "\": Expected https or http.");
        }
        _connection.setRequestMethod("POST");
        _connection.setDoOutput(true);
        _connection.setRequestProperty("User-Agent", "Voxygen-TTS-Client/1.4.0 (java)");
        if (_token != null && !_token.isEmpty()) {
            _connection.setRequestProperty("Authorization", "Bearer " + _token);
        }
    }

    @Override
    public void close() {
        if (_connection != null) {
            _connection.disconnect();
            _connection = null;
        }
    }

    /*
     * code to disable hostname and X509 certification validation (use for local
     * network only)
     */
    private static void disableCertificateValidation(HttpsURLConnection connection)
            throws NoSuchAlgorithmException, KeyManagementException {
        HttpsURLConnection.setDefaultHostnameVerifier((arg0, arg1) -> true);
        SSLContext ctx = SSLContext.getInstance("TLS");
        ctx.init(null, new TrustManager[] { new NullX509TrustManager() }, null);
        connection.setHostnameVerifier((hostname, session) -> true);
        connection.setSSLSocketFactory(ctx.getSocketFactory());
    }

    private static class NullX509TrustManager implements X509TrustManager {
        public void checkClientTrusted(X509Certificate[] chain, String authType) {
            // do nothing
        }

        public void checkServerTrusted(X509Certificate[] chain, String authType) {
            // do nothing
        }

        public X509Certificate[] getAcceptedIssuers() {
            return new X509Certificate[0];
        }
    }

    /*
     * Build URL request query string.
     * Keyword arguments:
     * arguments -- a map of TTS control parameters e.g.
     * {'voice':'Jenny','text':'Hello world!',...}
     */
    public Map<String, String> buildRequest(Map<String, String> arguments)
            throws NoSuchAlgorithmException, InvalidKeyException {
        Map<String, String> query = new HashMap<>();
        // initialize query from host url
        String url_query = _uri.getQuery();
        if (url_query != null) {
            String[] queryStrings = url_query.split("&");
            for (String q : queryStrings) {
                String[] parts = q.split("=", 2);
                query.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                        parts.length == 2 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "");
            }
        }
        // add arguments to request query (argument values take priority over existing parameters)
        query.putAll(arguments);
        // NOTE: rfc2046 section-4.1.1 "MUST always represent a line break as a CRLF sequence"
        for (Map.Entry<String, String> entry : query.entrySet()) {
            String value = entry.getValue();
            if (value != null) {
                query.put(entry.getKey(), value.replaceAll("(\r?\n)", "\r\n"));
            }
        }
        return query;
    }

    /** @noinspection BusyWait **/
    public void send(Map<String, String> request) throws IOException, URISyntaxException {
        byte[] body;
        switch (_body_type) {
            case JSON -> {
                // build JSON representation
                JSONObject jsonQuery = new JSONObject(request);
                body = jsonQuery.toString().getBytes(StandardCharsets.UTF_8);
                _connection.setRequestProperty("Content-Type", "application/json");
            }
            case URL_ENCODED -> {
                // build the query string
                StringBuilder queryString = new StringBuilder();
                for (Map.Entry<String, String> entry : request.entrySet()) {
                    if (!queryString.isEmpty()) {
                        queryString.append("&");
                    }
                    queryString.append(URLEncoder.encode(entry.getKey(), StandardCharsets.UTF_8))
                            .append("=")
                            .append(URLEncoder.encode(entry.getValue(), StandardCharsets.UTF_8));
                }
                body = queryString.toString().getBytes(StandardCharsets.UTF_8);
                _connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=utf-8");
            }
            default -> throw new IllegalArgumentException("unsupported body type");
        }
        switch (_accept_type) {
            case AUDIO -> _connection.setRequestProperty("Accept", "audio/*; q=1.0, application/octet-stream; q=0.8, */*; q=0.1");
            case JSON -> _connection.setRequestProperty("Accept", "application/json, */*; q=0.1");
            default -> throw new IllegalArgumentException("unsupported accept content type");
        }
        try (OutputStream os = _connection.getOutputStream()) {
            os.write(body);
        }
        var retries = 0;
        while (_connection.getResponseCode() == HttpURLConnection.HTTP_UNAVAILABLE && retries < _max_retries) {
            close();
            try {
                Thread.sleep(_retry_after * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            open();
            try (OutputStream os = _connection.getOutputStream()) {
                os.write(body);
            }
            retries += 1;
        }
    }

    public int getStatus() throws IOException {
        return _connection.getResponseCode();
    }

    public String getReason() throws IOException {
        return _connection.getResponseMessage();
    }

    public MimeType getContentType() {
        String contentType = _connection.getContentType();
        if (contentType != null) {
            if (contentType.startsWith("audio/") || contentType.equals("application/octet-stream")) {
                return MimeType.AUDIO;
            } else if (contentType.equals("application/json")) {
                return MimeType.JSON;
            }
        }
        return MimeType.PLAIN_TEXT;
    }

    public byte[] readData() throws IOException {
        return _connection.getInputStream().readNBytes(0x2000);
    }

    public byte[] readAllData() throws IOException {
        return _connection.getInputStream().readAllBytes();
    }

    public JSONObject readJson() throws IOException, JSONException {
        return new JSONObject(new String(_connection.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
    }

    public Map<String, List<String>> getTrailerFields() {
        String trailer = _connection.getHeaderField("Trailer");
        if (trailer != null) {
            String[] trailers = trailer.split(", *");
            return _connection.getHeaderFields().entrySet()
                    .stream()
                    .filter(entry -> Arrays.asList(trailers).contains(entry.getKey()))
                    .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        }
        return Collections.emptyMap();
    }

}
