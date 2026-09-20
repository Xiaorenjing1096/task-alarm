import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.regex.Pattern;

/**
 * Tiny JVM-based HTTP helper. The DSH pwsh sandbox cannot use schannel
 * (SEC_E_NO_CREDENTIALS), so shell-based HTTP is unusable; the JVM's own JSSE
 * stack works fine.
 *
 * Usage: java Fetch.java <url> [regexFilter]
 */
public class Fetch {
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("usage: java Fetch.java <url> [regexFilter]");
            System.exit(2);
        }
        HttpClient client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(20))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
        HttpRequest req = HttpRequest.newBuilder(URI.create(args[0]))
                .timeout(Duration.ofSeconds(45))
                .GET().build();
        HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
        System.out.println("HTTP " + resp.statusCode() + "  bytes=" + resp.body().length());
        Pattern p = args.length > 1 ? Pattern.compile(args[1]) : null;
        if (p == null) {
            System.out.println(resp.body());
        } else {
            resp.body().lines().filter(l -> p.matcher(l).find()).forEach(System.out::println);
        }
    }
}
