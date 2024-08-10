package hotfies.perfectpartysystem.managers;

import hotfies.perfectpartysystem.PerfectPartySystem;
import net.md_5.bungee.config.Configuration;
import net.md_5.bungee.config.ConfigurationProvider;
import net.md_5.bungee.config.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.logging.Level;

public class ConfigManager {
    private final PerfectPartySystem plugin;
    private Configuration config;
    private Configuration messagesConfig;

    public ConfigManager(PerfectPartySystem plugin) {
        this.plugin = plugin;
    }

    public void loadDefaultConfig() {
        if (!plugin.getDataFolder().exists()) {
            plugin.getDataFolder().mkdir();
        }
        File configFile = new File(plugin.getDataFolder(), "config.yml");
        if (!configFile.exists()) {
            try (InputStream in = plugin.getResourceAsStream("config.yml")) {
                if (in != null) {
                    Files.copy(in, configFile.toPath());
                } else {
                    plugin.getLogger().log(Level.WARNING, "Default config.yml not found in resources.");
                }
            } catch (IOException e) {
                plugin.getLogger().log(Level.SEVERE, "Could not save default config", e);
            }
        }
        try {
            config = ConfigurationProvider.getProvider(YamlConfiguration.class).load(configFile);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Could not load config.yml", e);
        }

        // Load language files from Lang directory
        File langDir = new File(plugin.getDataFolder(), "Lang");
        if (!langDir.exists()) {
            langDir.mkdir();
        }
        String[] languages = {"En_en", "Ru_ru"}; // Add more languages as needed
        for (String lang : languages) {
            File langFile = new File(langDir, "Messages_" + lang + ".yml");
            if (!langFile.exists()) {
                try (InputStream in = plugin.getResourceAsStream("Lang/Messages_" + lang + ".yml")) {
                    if (in != null) {
                        Files.copy(in, langFile.toPath());
                    } else {
                        plugin.getLogger().log(Level.WARNING, "Language file for " + lang + " not found in resources.");
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "Could not save default language file for " + lang, e);
                }
            }
        }
    }

    public Configuration getConfig() {
        return config;
    }

    public void loadMessagesConfig(String lang) {
        File messagesFile = new File(plugin.getDataFolder(), "Lang/Messages_" + lang + ".yml");
        if (!messagesFile.exists()) {
            plugin.getLogger().log(Level.WARNING, "Language file for " + lang + " not found in data folder.");
            return;
        }
        try {
            messagesConfig = ConfigurationProvider.getProvider(YamlConfiguration.class).load(messagesFile);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public String getMessage(String path) {
        return getMessage(path, "");
    }

    public String getMessage(String path, String defaultValue) {
        return messagesConfig.getString(path, defaultValue);
    }

    public String getMessageWithColor(String lang, String path) {
        loadMessagesConfig(lang);
        return getMessage(path).replaceAll("&", "§");
    }

    public String getMessageWithPrefix(String lang, String path) {
        loadMessagesConfig(lang);
        String prefix = getMessage("prefix").replaceAll("&", "§");
        String message = getMessage(path).replaceAll("&", "§");
        return prefix + " " + message;
    }
}