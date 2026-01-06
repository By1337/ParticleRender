package dev.by1337.particle;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.google.gson.stream.JsonReader;
import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public record Version(@NotNull String id, int protocolVersion) {
    private static final Logger log = LoggerFactory.getLogger(Version.class);

    public static final Version VERSION;

    static {
        String var = System.getProperty("fparticle.protocol");
        if (var != null) {
            log.info("use protocol version from system property: {}", var);
            VERSION = new Version(
                    var,
                    Integer.parseInt(var)
            );
        } else {
            try (InputStream stream = Bukkit.getServer().getClass().getResourceAsStream("/version.json")) {
                if (stream == null) {
                    throw new FileNotFoundException("not found version.json file!");
                } else {
                    try (InputStreamReader reader = new InputStreamReader(stream, StandardCharsets.UTF_8)) {
                        Gson gson = new Gson();
                        JsonReader jsonReader = new JsonReader(reader);
                        JsonObject object = gson.getAdapter(JsonObject.class).read(jsonReader);
                        VERSION = new Version(
                                object.get("id").getAsString(),
                                object.get("protocol_version").getAsInt()
                        );
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }

    }
}
