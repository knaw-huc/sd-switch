package nl.knaw.huc.sdswitch.server;

import io.javalin.Javalin;
import nl.knaw.huc.sdswitch.server.config.Router;
import nl.knaw.huc.sdswitch.server.config.Switch;
import nl.knaw.huc.sdswitch.server.config.SwitchLoader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class Application {
    private static final Logger LOGGER = LoggerFactory.getLogger(Application.class);

    public static void main(String[] args) throws Exception {
        final InputStream configStream = System.getenv().get("CONFIG") != null
                ? new FileInputStream(System.getenv().get("CONFIG"))
                : Application.class.getClassLoader().getResourceAsStream("config.yml");

        final SwitchLoader switchLoader = new SwitchLoader();
        final Map<String, Set<Switch<?>>> switches = switchLoader.loadSwitches(configStream);

        final int port = Integer.parseInt(System.getenv().getOrDefault("SERVER_PORT", "8080"));
        final Javalin app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        List<String> urlPatterns = switches.keySet().stream()
                .sorted((p1, p2) -> -1 * Integer.compare(p1.length(), p2.length())).toList();
        for (String urlPattern : urlPatterns) {
            new Router(app, urlPattern, switches.get(urlPattern));
            LOGGER.info("Configured a new switch for URL " + urlPattern);
        }
    }
}
