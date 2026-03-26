package voxygen.url.data;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLDecoder;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class URLDataConnection extends URLConnection {

    private static final String DATA_PROTO_RE = "data:((.*?/.*?)?(?:;(.*?)=(.*?))?)(?:;(base64)?)?,(.*)";

    private final Matcher m;

    public URLDataConnection(final URL u) throws MalformedURLException {
        super(u);
        Pattern re = Pattern.compile(DATA_PROTO_RE);
        m = re.matcher(url.toString());
        connected = m.matches();
        if (!connected) {
            throw new MalformedURLException("Wrong data protocol URL");
        }
    }

    @Override
    public void connect() {
    }

    @Override
    public InputStream getInputStream() throws IOException {
        if (!connected) {
            throw new IOException();
        }
        return new ByteArrayInputStream(getData());
    }

    @Override
    public String getContentType() {
        if (!connected) {
            return null;
        }
        return m.group(1);
    }

    private byte[] getData() throws UnsupportedEncodingException {
        String type = m.group(2);
        String attribute = m.group(3);
        String charset = m.group(4);
        String base64 = m.group(5);
        String data = m.group(6);
        if ("base64".equals(base64)) {
            return Base64.getDecoder().decode(data);
        }
        if (!"charset".equals(attribute)) {
            return getText(data, "UTF-8");
        }
        if (type != null && type.startsWith("text/")) {
            return getText(data, charset);
        }
        return new byte[0];
    }

    private static byte[] getText(final String data, final String charset) throws UnsupportedEncodingException {
        return URLDecoder.decode(data, charset).getBytes();
    }
}
