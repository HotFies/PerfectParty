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

public class PartyChatCommand extends Command {
    private final PerfectPartySystem plugin;
    private final PartyManager partyManager;
    private final ConfigManager configManager;

    public PartyChatCommand(PerfectPartySystem plugin, PartyManager partyManager, ConfigManager configManager) {
        super("pc");
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

        Party party = partyManager.getParty(playerUuid);
        if (party == null) {
            player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "party.no_party")));
            return;
        }

        if (args.length == 0) {
            player.sendMessage(new TextComponent(configManager.getMessageWithPrefix(lang, "command.usage_pc")));
            return;
        }

        // Объединяем аргументы в строку сообщения
        String message = String.join(" ", args);

        // Получаем формат сообщения и заменяем & на § для поддержки цветовых кодов
        String formattedMessage = configManager.getMessageWithColor(lang, "party.chat_format")
                .replace("%player%", player.getName())
                .replace("%message%", message);

        for (UUID memberUuid : party.getAllMemberUuidsIncludingLeader()) {
            ProxiedPlayer member = plugin.getProxy().getPlayer(memberUuid);
            if (member != null) {
                member.sendMessage(new TextComponent(formattedMessage));
            }
        }
    }
}