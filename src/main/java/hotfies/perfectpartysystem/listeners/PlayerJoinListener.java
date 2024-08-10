package hotfies.perfectpartysystem.listeners;

import hotfies.perfectpartysystem.PerfectPartySystem;
import hotfies.perfectpartysystem.managers.ConfigManager;
import hotfies.perfectpartysystem.managers.PartyManager;
import hotfies.perfectpartysystem.models.Party;
import hotfies.perfectpartysystem.models.PlayerState;
import hotfies.perfectpartysystem.utils.LangUtils;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.event.PlayerDisconnectEvent;
import net.md_5.bungee.api.event.PostLoginEvent;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.event.EventHandler;
import net.md_5.bungee.api.scheduler.ScheduledTask;
import net.md_5.bungee.api.chat.TextComponent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class PlayerJoinListener implements Listener {
    private final PerfectPartySystem plugin;
    private final PartyManager partyManager;
    private final ConfigManager configManager;
    private final Map<UUID, PlayerState> playerStates;

    public PlayerJoinListener(PerfectPartySystem plugin, PartyManager partyManager) {
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.configManager = plugin.getConfigManager();
        this.playerStates = new HashMap<>();
    }

    @EventHandler
    public void onPlayerQuit(PlayerDisconnectEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        // Используем асинхронную задачу для обработки события выхода
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            Party party = partyManager.getParty(playerUuid);

            if (party != null) {
                handlePlayerQuitAsync(player, party);
            }
        });
    }

    private void handlePlayerQuitAsync(ProxiedPlayer player, Party party) {
        int reconnectTime = configManager.getConfig().getInt("reconnectTime", 60) * 1000; // Convert seconds to milliseconds
        broadcastMessageToParty(party, "party.player_leave", new String[]{"player"}, new String[]{player.getName()});

        PlayerState playerState = playerStates.computeIfAbsent(player.getUniqueId(), uuid -> new PlayerState());
        playerState.setReconnecting(true);

        ScheduledTask task = plugin.getProxy().getScheduler().schedule(plugin, () -> {
            try {
                handlePlayerDisconnect(player.getUniqueId(), party);
            } catch (Exception e) {
                plugin.getLogger().severe("Error while handling player disconnect: " + e.getMessage());
            }
        }, reconnectTime, TimeUnit.MILLISECONDS);
        playerState.setReconnectTask(task);
    }

    private void handlePlayerDisconnect(UUID playerUuid, Party party) {
        PlayerState playerState = playerStates.get(playerUuid);
        if (playerState != null && playerState.isReconnecting()) {
            playerState.setReconnecting(false);
            ScheduledTask task = playerState.getReconnectTask();
            if (task != null) {
                task.cancel();
                playerState.setReconnectTask(null);
            }
            playerStates.put(playerUuid, playerState);

            // Оптимизируем обработку выхода лидера
            if (party.getLeaderUuid().equals(playerUuid)) {
                partyManager.handleLeaderDisconnect(playerUuid);
            } else {
                partyManager.handleMemberDisconnect(playerUuid);
            }
        }
    }

    @EventHandler
    public void onPlayerJoin(PostLoginEvent event) {
        ProxiedPlayer player = event.getPlayer();
        UUID playerUuid = player.getUniqueId();

        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            PlayerState playerState = playerStates.get(playerUuid);
            if (playerState != null && playerState.isReconnecting()) {
                playerState.setReconnecting(false);
                ScheduledTask task = playerState.getReconnectTask();
                if (task != null) {
                    task.cancel();
                    playerState.setReconnectTask(null);
                }
                playerStates.put(playerUuid, playerState);

                Party party = partyManager.getParty(playerUuid);
                if (party != null) {
                    broadcastMessageToParty(party, "party.player_rejoin", new String[]{"player"}, new String[]{player.getName()});
                }
            }
        });
    }

    private void broadcastMessageToParty(Party party, String messageKey, String[] placeholderTypes, String[] placeholderValues) {
        for (UUID memberUuid : party.getAllMemberUuidsIncludingLeader()) {
            ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
            if (member != null && member.isConnected()) {
                String lang = LangUtils.getPlayerLang(plugin, member);
                String message = configManager.getMessageWithPrefix(lang, messageKey);

                if (placeholderTypes != null && placeholderValues != null) {
                    for (int i = 0; i < placeholderTypes.length; i++) {
                        message = message.replace("{" + placeholderTypes[i] + "}", placeholderValues[i]);
                    }
                }

                String finalMessage = message; // Делаем переменную финальной
                plugin.getProxy().getScheduler().runAsync(plugin, () -> member.sendMessage(new TextComponent(TextComponent.fromLegacyText(finalMessage))));
            }
        }
    }
}