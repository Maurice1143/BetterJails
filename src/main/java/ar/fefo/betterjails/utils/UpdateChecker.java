package ar.fefo.betterjails.utils;

import org.bukkit.plugin.Plugin;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;
import java.util.function.Consumer;

public class UpdateChecker {
    private final Plugin plugin;
    private final int id;

    public UpdateChecker(Plugin plugin, int id) {
        this.plugin = plugin;
        this.id = id;
    }

    public void getVersion(final Consumer<String> consumer) {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (InputStream is = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + id).openStream(); Scanner scanner = new Scanner(is)) {
                if (scanner.hasNext())
                    consumer.accept(scanner.next());
            } catch (IOException e) {
                plugin.getLogger().warning("Cannot look for updates: " + e.getMessage());
            }
        });
    }
}
