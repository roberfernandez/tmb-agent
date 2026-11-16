package cat.tmbagent.miralincorporatiu;

import android.util.Base64;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;

/** Direct HTTPS Basic Auth to the fixed TMB hostname, never to third parties. */
final class CorporateMiralinClient {
    static final String HOST = "https://aplicacions.tmb.cat";
    private static final int MAX_HTML_BYTES = 1024 * 1024;

    static final class AccessDenied extends IOException {
        AccessDenied(int status) { super("Accés rebutjat (" + status + "). Revisa les credencials o els permisos."); }
    }

    CorporateBoardParser.Board fetch(String username, String password, int stop) throws IOException {
        if (username == null || username.isBlank() || username.contains(":") || password == null || password.isEmpty()) {
            throw new IllegalArgumentException("Introdueix un usuari i una contrasenya corporatius vàlids.");
        }
        if (stop < 413 || stop > 434) throw new IllegalArgumentException("Estació de la L4 incorrecta.");
        URL url = new URL(HOST + "/miralin/routes/4/stops/" + stop);
        HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
        try {
            connection.setConnectTimeout(10000);
            connection.setReadTimeout(12000);
            connection.setInstanceFollowRedirects(false); // Never forward Basic credentials to another host.
            connection.setRequestMethod("GET");
            connection.setRequestProperty("Accept", "text/html");
            connection.setRequestProperty("Accept-Encoding", "identity");
            String basic = username + ":" + password;
            connection.setRequestProperty("Authorization", "Basic " +
                Base64.encodeToString(basic.getBytes(StandardCharsets.UTF_8), Base64.NO_WRAP));

            int status = connection.getResponseCode();
            if (status == 401 || status == 403) throw new AccessDenied(status);
            if (status >= 300 && status < 400)
                throw new IOException("El servidor ha redirigit la consulta. Comprova l'accés corporatiu.");
            if (status != 200) throw new IOException("Resposta del servidor: HTTP " + status);
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.toLowerCase(java.util.Locale.ROOT).contains("html")) {
                throw new IOException("La resposta no és HTML del Miralín corporatiu.");
            }
            byte[] bytes;
            try (InputStream stream = connection.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
                byte[] block = new byte[4096];
                int read;
                while ((read = stream.read(block)) != -1) {
                    if (buffer.size() + read > MAX_HTML_BYTES) throw new IOException("Resposta massa gran.");
                    buffer.write(block, 0, read);
                }
                bytes = buffer.toByteArray();
            }
            Charset encoding = StandardCharsets.UTF_8;
            if (contentType != null) {
                java.util.regex.Matcher matcher = java.util.regex.Pattern
                    .compile("charset\\s*=\\s*['\\\"]?([a-zA-Z0-9_-]+)",
                        java.util.regex.Pattern.CASE_INSENSITIVE).matcher(contentType);
                if (matcher.find()) {
                    try { encoding = Charset.forName(matcher.group(1)); }
                    catch (Exception ignored) { /* UTF-8 by default. */ }
                }
            }
            try {
                return CorporateBoardParser.parse(new String(bytes, encoding));
            } catch (IllegalArgumentException ex) {
                throw new IOException(ex.getMessage());
            }
        } finally {
            connection.disconnect();
        }
    }
}
