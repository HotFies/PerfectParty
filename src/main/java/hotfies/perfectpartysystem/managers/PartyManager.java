package hotfies.perfectpartysystem.managers;

import hotfies.perfectpartysystem.PerfectPartySystem;
import hotfies.perfectpartysystem.models.Party;
import hotfies.perfectpartysystem.models.PartyInvitation;
import hotfies.perfectpartysystem.utils.LangUtils;
import net.md_5.bungee.api.chat.ClickEvent;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Listener;
import net.md_5.bungee.api.config.ServerInfo;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class PartyManager implements Listener {
    private final PerfectPartySystem plugin;
    private final DatabaseManager databaseManager;
    private final ConfigManager configManager;
    private final Map<UUID, PartyInvitation> invitationMap;

    public PartyManager(PerfectPartySystem plugin, DatabaseManager databaseManager, ConfigManager configManager) {
        this.plugin = plugin;
        this.databaseManager = databaseManager;
        this.configManager = configManager;
        this.invitationMap = new HashMap<>();
    }

    public Party getParty(UUID playerUuid) {
        Party party = null;
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT * FROM perfect_party WHERE leader_uuid IN (SELECT leader_uuid FROM perfect_party WHERE member_uuid = ?)")) {
            stmt.setString(1, playerUuid.toString());

            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                UUID leaderUuid = UUID.fromString(rs.getString("leader_uuid"));
                String leaderName = rs.getString("leader_name");
                UUID memberUuid = UUID.fromString(rs.getString("member_uuid"));
                String memberName = rs.getString("member_name");

                if (party == null) {
                    party = new Party(leaderUuid, leaderName);
                }
                if (!leaderUuid.equals(memberUuid)) {
                    party.addMember(memberUuid, memberName);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while getting party: " + e.getMessage());
            e.printStackTrace();
        }
        return party;
    }

    public void createParty(UUID leaderUuid, String leaderName) {
        Party party = new Party(leaderUuid, leaderName);
        plugin.getLogger().info("Party created for leader: " + leaderName);
        savePartyToDatabase(party);
    }

    public void addMemberToParty(UUID leaderUuid, UUID memberUuid, String memberName) {
        Party party = getParty(leaderUuid);
        if (party != null) {
            party.addMember(memberUuid, memberName);
            savePartyToDatabase(party);
        }
    }

    public void removeMember(UUID playerUuid) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(playerUuid);
        Party party = getParty(playerUuid);
        if (party == null) return;

        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("DELETE FROM perfect_party WHERE member_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            stmt.executeUpdate();

            if (player != null) {
                player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, player), "party.kicked")));
            }
            broadcastMessage(party, "party.player_kicked", new String[]{"kickedPlayer"}, new String[]{getPlayerName(playerUuid)});
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while removing member: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void disbandParty(UUID leaderUuid) {
        Party party = getParty(leaderUuid);
        if (party != null && party.getLeaderUuid().equals(leaderUuid)) {
            broadcastMessage(party, "party.party_disbanded", null, null);
            deletePartyFromDatabase(party);
        }
    }

    public void warpParty(UUID leaderUuid) {
        plugin.getProxy().getScheduler().runAsync(plugin, () -> {
            Party party = getParty(leaderUuid);
            if (party == null) return;

            ProxiedPlayer leader = plugin.getProxy().getPlayer(leaderUuid);
            if (leader == null) return;

            String leaderServer = leader.getServer().getInfo().getName();
            ServerInfo serverInfo = leader.getServer().getInfo();

            // Получаем максимальное количество слотов на сервере из конфига
            int maxPlayers = plugin.getConfigManager().getConfig().getInt("server_slots." + leaderServer, Integer.MAX_VALUE);
            int onlinePlayers = serverInfo.getPlayers().size();
            int partySize = party.getAllMemberUuidsIncludingLeader().size();

            if (onlinePlayers + partySize > maxPlayers) {
                // Если места не хватает, отправляем сообщение об ошибке
                for (UUID memberUuid : party.getAllMemberUuidsIncludingLeader()) {
                    ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
                    if (member != null) {
                        String lang = LangUtils.getPlayerLang(plugin, member);
                        member.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.not_enough_space")));
                    }
                }
                return; // Прекращаем выполнение, если места недостаточно
            }

            for (UUID memberUuid : party.getMemberUuids()) {
                ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
                if (member != null && !member.getServer().getInfo().getName().equals(leaderServer)) {
                    member.connect(serverInfo);
                    member.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, member), "party.warped")));
                }
            }
            leader.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, leader), "party.warp_initiated")));
        });
    }

    public boolean canReceivePartyInvites(UUID playerUuid) {
        boolean canReceive = true; // По умолчанию считаем, что игрок может получать приглашения
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT get_party_invites FROM player_settings WHERE player_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    canReceive = rs.getBoolean("get_party_invites");
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while checking party invites status: " + e.getMessage());
            e.printStackTrace();
        }
        return canReceive;
    }

    public void leftMember(UUID playerUuid) {
        Party party = getParty(playerUuid);
        if (party == null) return;

        String playerName = getPlayerName(playerUuid);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);

            // Удаляем участника из пати в БД
            try (PreparedStatement stmt = connection.prepareStatement("DELETE FROM perfect_party WHERE member_uuid = ?")) {
                stmt.setString(1, playerUuid.toString());
                stmt.executeUpdate();
            }

            connection.commit();

            // Отправляем сообщение участнику о выходе из пати
            ProxiedPlayer player = plugin.getProxy().getPlayer(playerUuid);
            if (player != null) {
                player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, player), "party.you_left")));
            }

            // Отправляем сообщение остальным участникам о выходе игрока
            broadcastMessage(party, "party.player_left", new String[]{"player"}, new String[]{playerName});

        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while removing member: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void promotePlayer(UUID currentLeaderUuid, UUID newLeaderUuid) {
        Party party = getParty(currentLeaderUuid);
        if (party == null) {
            return;
        }

        if (currentLeaderUuid.equals(newLeaderUuid)) {
            ProxiedPlayer currentLeader = plugin.getProxy().getPlayer(currentLeaderUuid);
            if (currentLeader != null) {
                currentLeader.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, currentLeader), "party.cannot_promote_self")));
            }
            return;
        }

        if (!party.getMemberUuids().contains(newLeaderUuid)) {
            ProxiedPlayer currentLeader = plugin.getProxy().getPlayer(currentLeaderUuid);
            if (currentLeader != null) {
                currentLeader.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, currentLeader), "party.player_not_in_party")));
            }
            return;
        }

        String newLeaderName = getPlayerName(newLeaderUuid);
        String currentLeaderName = getPlayerName(currentLeaderUuid);

        // Отправляем сообщение всем участникам пати о смене лидерства
        broadcastMessage(party, "party.leadership_transferred", new String[]{"newLeader"}, new String[]{newLeaderName});

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);

            try (PreparedStatement updateLeaderStmt = connection.prepareStatement("UPDATE perfect_party SET leader_uuid = ?, leader_name = ? WHERE leader_uuid = ?")) {
                updateLeaderStmt.setString(1, newLeaderUuid.toString());
                updateLeaderStmt.setString(2, newLeaderName);
                updateLeaderStmt.setString(3, currentLeaderUuid.toString());
                updateLeaderStmt.executeUpdate();
            }

            try (PreparedStatement updateMemberStmt = connection.prepareStatement("UPDATE perfect_party SET leader_uuid = ? WHERE member_uuid = ?")) {
                updateMemberStmt.setString(1, newLeaderUuid.toString());
                updateMemberStmt.setString(2, currentLeaderUuid.toString());
                updateMemberStmt.executeUpdate();
            }

            connection.commit();

            party.setLeader(newLeaderUuid, newLeaderName);
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while promoting player: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void kickPlayer(UUID leaderUuid, UUID memberUuid) {
        if (leaderUuid.equals(memberUuid)) {
            ProxiedPlayer leader = plugin.getProxy().getPlayer(leaderUuid);
            if (leader != null) {
                leader.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, leader), "party.cannot_kick_self")));
            }
            return;
        }

        Party party = getParty(leaderUuid);
        if (party == null || !party.getMemberUuids().contains(memberUuid)) {
            ProxiedPlayer leader = plugin.getProxy().getPlayer(leaderUuid);
            if (leader != null) {
                leader.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, leader), "party.player_not_in_party")));
            }
            return;
        }

        // Сохраняем имя участника перед удалением
        String memberName = getPlayerName(memberUuid);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);

            // Удаляем участника из базы данных
            try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM perfect_party WHERE member_uuid = ?")) {
                deleteStmt.setString(1, memberUuid.toString());
                deleteStmt.executeUpdate();
            }

            connection.commit();

            // Обновляем локальные данные пати
            party.removeMember(memberUuid);

        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while kicking player: " + e.getMessage());
            e.printStackTrace();
        }

        // Отправляем сообщение кикнутому участнику
        ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
        if (member != null) {
            member.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, member), "party.kicked")));
        }

        // Отправляем сообщение всем членам пати о том, что участник был кикнут
        broadcastMessage(party, "party.player_kicked", new String[]{"kickedPlayer"}, new String[]{memberName});
    }
    public void invitePlayer(UUID inviterUuid, UUID inviteeUuid, String inviteeName) {
        Party party = getParty(inviterUuid);
        if (party == null) {
            createParty(inviterUuid, getPlayerName(inviterUuid));
            party = getParty(inviterUuid);
        }

        if (!party.getLeaderUuid().equals(inviterUuid)) {
            ProxiedPlayer inviter = plugin.getProxy().getPlayer(inviterUuid);
            if (inviter != null) {
                inviter.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, inviter), "party.not_leader")));
            }
            return;
        }

        String lang = LangUtils.getPlayerLang(plugin, inviterUuid);
        String inviteeLang = LangUtils.getPlayerLang(plugin, inviteeUuid);

        if (party.getAllMemberUuidsIncludingLeader().size() >= getMaxPartySize(inviterUuid)) {
            ProxiedPlayer inviter = plugin.getProxy().getPlayer(inviterUuid);
            if (inviter != null) {
                inviter.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.size_limit")));
            }
            return;
        }

        if (getParty(inviteeUuid) != null) {
            ProxiedPlayer inviter = plugin.getProxy().getPlayer(inviterUuid);
            if (inviter != null) {
                inviter.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.already_in_party")));
            }
            return;
        }

        PartyInvitation invitation = new PartyInvitation(inviterUuid, inviteeUuid, inviteeName);
        invitationMap.put(inviteeUuid, invitation);

        String inviteMessage = configManager.getMessageWithPrefix(lang, "party.invite_message")
                .replace("{invitedPlayer}", inviteeName);

        for (UUID memberUuid : party.getAllMemberUuidsIncludingLeader()) {
            ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(new TextComponent(inviteMessage));
            }
        }

        ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
        if (invitee != null) {
            TextComponent message = new TextComponent(configManager.getMessageWithPrefix(inviteeLang, "party.invited")
                    .replace("{inviter}", getPlayerName(inviterUuid)));
            message.setClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, "/perfectparty accept"));
            invitee.sendMessage(message);
        }
        plugin.getProxy().getScheduler().schedule(plugin, () -> invitationMap.remove(inviteeUuid), 60, TimeUnit.SECONDS);
    }
    public void acceptInvitation(UUID inviteeUuid) {
        PartyInvitation invitation = invitationMap.get(inviteeUuid);
        if (invitation == null) {
            ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
            if (invitee != null) {
                invitee.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, invitee), "party.no_pending_invitations")));
            }
            return;
        }

        Party party = getParty(invitation.getInviterUuid());
        if (party == null) {
            ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
            if (invitee != null) {
                invitee.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, invitee), "party.no_longer_exists")));
            }
            invitationMap.remove(inviteeUuid);
            return;
        }

        if (party.getAllMemberUuidsIncludingLeader().size() >= getMaxPartySize(inviteeUuid)) {
            ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
            if (invitee != null) {
                invitee.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, invitee), "party.size_limit")));
            }
            return;
        }

        addMemberToParty(party.getLeaderUuid(), inviteeUuid, invitation.getInviteeName());
        invitationMap.remove(inviteeUuid);

        ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
        if (invitee != null) {
            invitee.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, invitee), "party.joined")
                    .replace("{leader}", getPlayerName(party.getLeaderUuid()))));
        }

        broadcastMessage(party, "party.player_joined", new String[]{"player"}, new String[]{invitation.getInviteeName()});
    }

    public void denyInvitation(UUID inviteeUuid) {
        if (!invitationMap.containsKey(inviteeUuid)) {
            ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
            if (invitee != null) {
                invitee.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, invitee), "party.no_pending_invitations")));
            }
            return;
        }
        invitationMap.remove(inviteeUuid);

        ProxiedPlayer invitee = plugin.getProxy().getPlayer(inviteeUuid);
        if (invitee != null) {
            invitee.sendMessage(new TextComponent(configManager.getMessageWithPrefix(LangUtils.getPlayerLang(plugin, invitee), "party.denied")));
        }
    }

    public int getMaxPartySize(UUID playerUuid) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(playerUuid);
        if (player != null) {
            if (player.hasPermission("perfectparty.size.21")) return 21;
            if (player.hasPermission("perfectparty.size.17")) return 17;
            if (player.hasPermission("perfectparty.size.11")) return 11;
            if (player.hasPermission("perfectparty.size.7")) return 7;
            if (player.hasPermission("perfectparty.size.4")) return 4;
        }
        return 0;
    }

    private void savePartyToDatabase(Party party) {
        plugin.getLogger().info("Saving party to database for leader: " + party.getLeaderName());
        try (Connection connection = databaseManager.getConnection()) {
            try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM perfect_party WHERE leader_uuid = ?")) {
                deleteStmt.setString(1, party.getLeaderUuid().toString());
                int deleteCount = deleteStmt.executeUpdate();
                plugin.getLogger().info("Deleted " + deleteCount + " old records for leader: " + party.getLeaderName());
            }
            try (PreparedStatement insertStmt = connection.prepareStatement("INSERT INTO perfect_party (leader_uuid, leader_name, member_uuid, member_name) VALUES (?, ?, ?, ?)")) {
                Set<UUID> uniqueMembers = new HashSet<>(party.getMemberUuids());
                uniqueMembers.add(party.getLeaderUuid());
                for (UUID memberUuid : uniqueMembers) {
                    insertStmt.setString(1, party.getLeaderUuid().toString());
                    insertStmt.setString(2, party.getLeaderName());
                    insertStmt.setString(3, memberUuid.toString());
                    insertStmt.setString(4, getPlayerName(memberUuid));
                    insertStmt.addBatch();
                }
                int[] insertCounts = insertStmt.executeBatch();
                plugin.getLogger().info("Inserted " + insertCounts.length + " new records for leader: " + party.getLeaderName());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while saving party: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void deletePartyFromDatabase(Party party) {
        plugin.getLogger().info("Deleting party from database for leader: " + party.getLeaderName());
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("DELETE FROM perfect_party WHERE leader_uuid = ?")) {
            stmt.setString(1, party.getLeaderUuid().toString());
            int deleteCount = stmt.executeUpdate();
            plugin.getLogger().info("Deleted " + deleteCount + " records for leader: " + party.getLeaderName());
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while deleting party: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void broadcastMessage(Party party, String messageKey, String[] placeholderTypes, String[] placeholderValues) {
        Set<UUID> uniqueMembers = new HashSet<>(party.getAllMemberUuidsIncludingLeader());

        for (UUID memberUuid : uniqueMembers) {
            ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
            if (member != null) {
                String lang = LangUtils.getPlayerLang(plugin, member);
                String message = configManager.getMessageWithPrefix(lang, messageKey);
                if (placeholderTypes != null && placeholderValues != null) {
                    for (int i = 0; i < placeholderTypes.length; i++) {
                        message = message.replace("{" + placeholderTypes[i] + "}", placeholderValues[i]);
                    }
                }
                member.sendMessage(new TextComponent(message));
            }
        }
    }

    public String getPlayerName(UUID playerUuid) {
        ProxiedPlayer player = plugin.getProxy().getPlayer(playerUuid);
        if (player != null) {
            return player.getName();
        }
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("SELECT member_name FROM perfect_party WHERE member_uuid = ?")) {
            stmt.setString(1, playerUuid.toString());
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("member_name");
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while getting player name: " + e.getMessage());
            e.printStackTrace();
        }
        return "Unknown";
    }

    public void handleLeaderDisconnect(UUID leaderUuid) {
        Party party = getParty(leaderUuid);
        if (party == null) {
            return;
        }

        if (party.getMemberUuids().isEmpty()) {
            disbandParty(leaderUuid);
        } else {
            UUID newLeaderUuid = party.getMemberUuids().get(0);
            String newLeaderName = getPlayerName(newLeaderUuid);

            // Сохраняем данные старого лидера перед изменением
            String oldLeaderName = getPlayerName(leaderUuid);

            try (Connection connection = databaseManager.getConnection()) {
                connection.setAutoCommit(false);

                // Обновляем лидера в БД
                try (PreparedStatement updateLeaderStmt = connection.prepareStatement("UPDATE perfect_party SET leader_uuid = ?, leader_name = ? WHERE leader_uuid = ?")) {
                    updateLeaderStmt.setString(1, newLeaderUuid.toString());
                    updateLeaderStmt.setString(2, newLeaderName);
                    updateLeaderStmt.setString(3, leaderUuid.toString());
                    updateLeaderStmt.executeUpdate();
                }

                // Удаляем старого лидера из списка участников
                try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM perfect_party WHERE member_uuid = ?")) {
                    deleteStmt.setString(1, leaderUuid.toString());
                    deleteStmt.executeUpdate();
                }

                connection.commit();

                // Обновляем локальные данные пати
                party.setLeader(newLeaderUuid, newLeaderName);

                // Отправляем сообщение о смене лидерства
                broadcastMessage(party, "party.leader_left", new String[]{"oldLeader", "newLeader"}, new String[]{oldLeaderName, newLeaderName});

            } catch (SQLException e) {
                plugin.getLogger().severe("SQL error while handling leader disconnect: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public void handleMemberDisconnect(UUID memberUuid) {
        Party party = getParty(memberUuid);
        if (party == null) {
            return;
        }

        // Сохраняем имя участника перед удалением
        String memberName = getPlayerName(memberUuid);

        try (Connection connection = databaseManager.getConnection()) {
            connection.setAutoCommit(false);

            // Удаляем участника из базы данных
            try (PreparedStatement deleteStmt = connection.prepareStatement("DELETE FROM perfect_party WHERE member_uuid = ?")) {
                deleteStmt.setString(1, memberUuid.toString());
                deleteStmt.executeUpdate();
            }

            connection.commit();

            // Обновляем локальные данные пати
            party.removeMember(memberUuid);

            // Отправляем сообщение всем членам пати о том, что участник был кикнут
            broadcastMessage(party, "party.player_left", new String[]{"player"}, new String[]{memberName});

        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while handling member disconnect: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void clearDatabase() {
        plugin.getLogger().info("Clearing party data from database.");
        try (Connection connection = databaseManager.getConnection();
             PreparedStatement stmt = connection.prepareStatement("DELETE FROM perfect_party")) {
            int deleteCount = stmt.executeUpdate();
            plugin.getLogger().info("Deleted " + deleteCount + " records from perfect_party table.");
        } catch (SQLException e) {
            plugin.getLogger().severe("SQL error while clearing party data: " + e.getMessage());
            e.printStackTrace();
        }
    }
}