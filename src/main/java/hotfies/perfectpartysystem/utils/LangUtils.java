package hotfies.perfectpartysystem.utils;

import hotfies.perfectpartysystem.PerfectPartySystem;
import net.md_5.bungee.api.connection.ProxiedPlayer;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;
import java.util.logging.Level;

public class LangUtils {
    public static String getPlayerLang(PerfectPartySystem plugin, ProxiedPlayer player) {
        return getPlayerLang(plugin, player.getUniqueId());
    }

    public static String getPlayerLang(PerfectPartySystem plugin, UUID playerUuid) {
        String lang = "En_en"; // Значение по умолчанию
        String sql = "SELECT lang FROM player_settings WHERE player_uuid = ?";

        try (Connection connection = plugin.getDatabaseManager().getConnection();
             PreparedStatement stmt = connection.prepareStatement(sql)) {

            stmt.setString(1, playerUuid.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    lang = rs.getString("lang");
                }
            }

        } catch (SQLException e) {
            // Логируем ошибку с уровнем SEVERE
            plugin.getLogger().log(Level.SEVERE, "SQL error while getting player language: " + e.getMessage(), e);
        }

        return lang;
    }
}