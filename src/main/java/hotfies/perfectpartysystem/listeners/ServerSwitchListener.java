package hotfies.perfectpartysystem.listeners;

import hotfies.perfectpartysystem.PerfectPartySystem;
import hotfies.perfectpartysystem.managers.PartyManager;
import hotfies.perfectpartysystem.models.Party;
import hotfies.perfectpartysystem.utils.LangUtils;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.ServerSwitchEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.config.ServerInfo;

import java.util.List;
import java.util.UUID;

public class ServerSwitchListener implements Listener {
    private final PerfectPartySystem plugin;
    private final PartyManager partyManager;

    public ServerSwitchListener(PerfectPartySystem plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
    }

    @EventHandler
    public void onServerSwitch(ServerSwitchEvent event) {
        ProxiedPlayer player = event.getPlayer();
        Party party = partyManager.getParty(player.getUniqueId());

        if (party != null && party.getLeaderUuid().equals(player.getUniqueId())) {
            String serverName = player.getServer().getInfo().getName();
            List<String> excludedServers = plugin.getConfigManager().getConfig().getStringList("exclude_servers");

            if (!excludedServers.contains(serverName)) {
                // Вызываем warpParty, которая теперь асинхронно выполняет все проверки и телепортации
                partyManager.warpParty(player.getUniqueId());
            }
        }
    }
}