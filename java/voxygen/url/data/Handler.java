package voxygen.url.data;

import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** @noinspection SpellCheckingInspection **/
public class Handler extends URLStreamHandler {
    private static final String HANDLER_PKS = "java.protocol.handler.pkgs";

    public static void register() {
        String prop = System.getProperty(HANDLER_PKS);
        List<String> pks = prop == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(prop.split("[|]")));
        String mypkg = Handler.class.getPackageName().replace(".data", "");
        if (!pks.contains(mypkg)) {
            pks.add(mypkg);
            System.setProperty(HANDLER_PKS, String.join("|", pks));
        }
    }

    @Override
    protected URLConnection openConnection(final URL u) throws MalformedURLException {
        return new URLDataConnection(u);
    }
}
