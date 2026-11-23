package cat.tmbagent.miralincorporatiu;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.net.ssl.HttpsURLConnection;

/** Read only TMB's published HTML over HTTPS. Never redirect Basic credentials to a different origin. */
final class CorporateMiralinClient {
    static final String HOST = "https://aplicacions.tmb.cat";
    private static final String ALLOWED_HOST = "aplicacions.tmb.cat";
    private static final int MAX_HTML_BYTES = 1024 * 1024;
    private static final int MAX_REDIRECTS = 5;

    static class AccessDenied extends IOException {
        AccessDenied(int status) {
            super("Accés rebutjat (HTTP " + status + "). Revisa les credencials o els permisos.");
        }
    }

    /** Different login mechanisms cannot be treated as an empty board or retried every 20 seconds. */
    static final class AuthenticationRedirect extends IOException {
        AuthenticationRedirect(String reason) { super(reason); }
    }

    static String boardUrl(int stop) {
        return HOST + "/miralin/routes/4/stops/" + stop;
    }

    CorporateBoardParser.Board fetch(String username, String password, int stop) throws IOException {
        if (username == null || username.trim().isEmpty() || username.contains(":")
            || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Introdueix un usuari i una contrasenya corporatius vàlids.");
        }
        if (stop < 413 || stop > 434) throw new IllegalArgumentException("Estació de la L4 incorrecta.");

        URL current = new URL(boardUrl(stop));
        String basic = "Basic " + Base64.encodeToString(
            (username + ":" + password).getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP);
        // Explicitly handle 3xx. HttpsURLConnection's automatic redirect policy can move Authorization
        // across origins; we must never enable it for Basic credentials.
        for (int redirects = 0; redirects <= MAX_REDIRECTS; redirects++) {
            HttpsURLConnection connection = (HttpsURLConnection) current.openConnection();
            try {
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(12000);
                connection.setInstanceFollowRedirects(false);
                connection.setRequestMethod("GET");
                connection.setRequestProperty("Accept", "text/html");
                connection.setRequestProperty("Accept-Encoding", "identity");
                connection.setRequestProperty("Authorization", basic);
                int status = connection.getResponseCode();
                if (status == 401 || status == 403) throw new AccessDenied(status);
                if (status >= 300 && status < 400) {
                    String location = connection.getHeaderField("Location");
                    if (location == null || location.trim().isEmpty()) {
                        throw new AuthenticationRedirect(
                            "HTTP " + status + ": el servidor redirigeix sense indicar la destinació.");
                    }
                    URL next = new URL(current, location);
                    if (!allowedUrl(next)) {
                        // Only the hostname is included: Location may contain auth tickets or tokens.
                        String domain = next.getHost();
                        throw new AuthenticationRedirect(
                            "HTTP " + status + ": TMB redirigeix cap a " + domain
                            + ". No s'hi envien les credencials. Pot requerir un altre sistema d'accés.");
                    }
                    if (redirects == MAX_REDIRECTS) {
                        throw new AuthenticationRedirect(
                            "Massa redireccions dins de TMB (darrera resposta HTTP " + status + ").");
                    }
                    current = next;
                    continue;
                }
                if (status != 200) throw new IOException("Resposta del servidor: HTTP " + status);
                String contentType = connection.getContentType();
                if (contentType != null && !contentType.toLowerCase(Locale.ROOT).contains("html")) {
                    throw new IOException("La resposta no és HTML del Miralín corporatiu.");
                }
                byte[] bytes = readLimited(connection);
                Charset encoding = charset(contentType);
                try {
                    return CorporateBoardParser.parse(new String(bytes, encoding));
                } catch (IllegalArgumentException ex) {
                    // A 200 response with no board may be a corporate sign-in page.
                    throw new AuthenticationRedirect(
                        "TMB ha retornat una pàgina HTML sense les taules del Miralín. "
                        + "Podria ser una pantalla d'identificació o un canvi del format.");
                }
            } finally {
                connection.disconnect();
            }
        }
        throw new AuthenticationRedirect("Massa redireccions a la web corporativa.");
    }

    private static boolean allowedUrl(URL url) {
        return "https".equalsIgnoreCase(url.getProtocol())
            && ALLOWED_HOST.equalsIgnoreCase(url.getHost())
            && (url.getPort() == -1 || url.getPort() == 443)
            && url.getUserInfo() == null;
    }

    private static byte[] readLimited(HttpsURLConnection connection) throws IOException {
        try (InputStream stream = connection.getInputStream();
             ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            byte[] block = new byte[4096];
            int read;
            while ((read = stream.read(block)) != -1) {
                if (buffer.size() + read > MAX_HTML_BYTES)
                    throw new IOException("Resposta HTML massa gran.");
                buffer.write(block, 0, read);
            }
            return buffer.toByteArray();
        }
    }

    private static Charset charset(String contentType) {
        if (contentType == null) return StandardCharsets.UTF_8;
        java.util.regex.Matcher matcher = java.util.regex.Pattern
            .compile("charset\\s*=\\s*['\\\"]?([a-zA-Z0-9_-]+)",
                java.util.regex.Pattern.CASE_INSENSITIVE)
            .matcher(contentType);
        if (!matcher.find()) return StandardCharsets.UTF_8;
        try { return Charset.forName(matcher.group(1)); }
        catch (Exception ignored) { return StandardCharsets.UTF_8; }
    }
}
