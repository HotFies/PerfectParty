package hotfies.perfectpartysystem.commands;

import hotfies.perfectpartysystem.PerfectPartySystem;
import hotfies.perfectpartysystem.managers.ConfigManager;
import hotfies.perfectpartysystem.managers.PartyManager;
import hotfies.perfectpartysystem.models.Party;
import hotfies.perfectpartysystem.utils.LangUtils;
import net.md_5.bungee.api.CommandSender;
import net.md_5.bungee.api.chat.TextComponent;
import net.md_5.bungee.api.connection.ProxiedPlayer;
import net.md_5.bungee.api.plugin.Command;

import java.util.UUID;

public class PerfectPartyCommand extends Command {
    private final PerfectPartySystem plugin;
    private final PartyManager partyManager;
    private final ConfigManager configManager;

    public PerfectPartyCommand(PerfectPartySystem plugin, PartyManager partyManager, ConfigManager configManager) {
        super("perfectparty", null, "party", "p");
        this.plugin = plugin;
        this.partyManager = partyManager;
        this.configManager = configManager;
    }

    @Override
    public void execute(CommandSender sender, String[] args) {
        if (!(sender instanceof ProxiedPlayer)) {
            sender.sendMessage(new TextComponent("This command can only be used by players."));
            return;
        }

        ProxiedPlayer player = (ProxiedPlayer) sender;
        UUID playerUuid = player.getUniqueId();
        String lang = LangUtils.getPlayerLang(plugin, player);

        if (args.length == 0) {
            sender.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.usage")));
            return;
        }

        switch (args[0].toLowerCase()) {
            case "create":
                Party existingParty = partyManager.getParty(playerUuid);
                if (existingParty != null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.already_have_party")));
                } else {
                    partyManager.createParty(playerUuid, player.getName());
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.created")));
                }
                break;

            case "list":
                Party party = partyManager.getParty(playerUuid);
                if (party == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.no_party")));
                    return;
                }
                String listHeader = configManager.getMessageWithPrefix(lang, "party.list_header")
                        .replace("{leader}", partyManager.getPlayerName(party.getLeaderUuid()))
                        .replace("{currentSize}", String.valueOf(party.getAllMemberUuidsIncludingLeader().size()))
                        .replace("{maxSize}", String.valueOf(partyManager.getMaxPartySize(playerUuid)));
                player.sendMessage(new TextComponent(listHeader));

                for (UUID memberUuid : party.getAllMemberUuidsIncludingLeader()) {
                    String listItem = configManager.getMessageWithPrefix(lang, "party.list_member")
                            .replace("{member}", partyManager.getPlayerName(memberUuid));
                    player.sendMessage(new TextComponent(listItem));
                }
                break;

            case "info":
                party = partyManager.getParty(playerUuid);
                if (party == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.no_party")));
                    return;
                }
                String infoMessage = configManager.getMessageWithPrefix(lang, "party.info")
                        .replace("{currentSize}", String.valueOf(party.getAllMemberUuidsIncludingLeader().size()))
                        .replace("{maxSize}", String.valueOf(partyManager.getMaxPartySize(playerUuid)))
                        .replace("{leader}", partyManager.getPlayerName(party.getLeaderUuid()));
                player.sendMessage(new TextComponent(infoMessage));
                break;

            case "invite":
            case "i": // Сокращение для invite
                if (args.length < 2) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.usage_invite")));
                    return;
                }

                // Проверяем, не приглашает ли игрок сам себя
                if (args[1].equalsIgnoreCase(player.getName())) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.cannot_invite_self")));
                    return;
                }

                ProxiedPlayer invitee = plugin.getProxy().getPlayer(args[1]);
                if (invitee == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.player_not_found")));
                    return;
                }

                // Проверяем, может ли игрок получать приглашения в пати
                if (!partyManager.canReceivePartyInvites(invitee.getUniqueId())) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.invites_disabled")));
                    return;
                }

                // Получаем или создаем партию
                party = partyManager.getParty(playerUuid);
                if (party == null) {
                    partyManager.createParty(playerUuid, player.getName());
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.created")));
                }

                // Теперь приглашаем игрока
                partyManager.invitePlayer(playerUuid, invitee.getUniqueId(), invitee.getName());
                break;

            case "kick":
                party = partyManager.getParty(playerUuid);
                if (party == null || !party.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.not_leader")));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.usage_kick")));
                    return;
                }
                ProxiedPlayer memberToKick = plugin.getProxy().getPlayer(args[1]);
                if (memberToKick == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.player_not_found")));
                    return;
                }
                partyManager.kickPlayer(playerUuid, memberToKick.getUniqueId());
                break;

            case "promote":
                party = partyManager.getParty(playerUuid);
                if (party == null || !party.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.not_leader")));
                    return;
                }
                if (args.length < 2) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.usage_promote")));
                    return;
                }
                ProxiedPlayer newLeader = plugin.getProxy().getPlayer(args[1]);
                if (newLeader == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.player_not_found")));
                    return;
                }
                partyManager.promotePlayer(playerUuid, newLeader.getUniqueId());
                break;

            case "disband":
                party = partyManager.getParty(playerUuid);
                if (party == null || !party.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.not_leader")));
                    return;
                }
                partyManager.disbandParty(playerUuid);
                break;

            case "leave":
                party = partyManager.getParty(playerUuid);
                if (party == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.no_party")));
                    return;
                }
                if (party.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.leader_cannot_leave")));
                    return;
                }
                partyManager.leftMember(playerUuid);
                break;

            case "warp":
            case "w": // Сокращение для warp
                party = partyManager.getParty(playerUuid);
                if (party == null) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.no_party")));
                    return;
                }
                if (!party.getLeaderUuid().equals(playerUuid)) {
                    player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.not_leader")));
                    return;
                }
                partyManager.warpParty(playerUuid);
                break;

            case "accept":
                partyManager.acceptInvitation(playerUuid);
                break;

            case "deny":
                partyManager.denyInvitation(playerUuid);
                break;

            default:
                sender.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.usage")));
                break;
        }
    }
}