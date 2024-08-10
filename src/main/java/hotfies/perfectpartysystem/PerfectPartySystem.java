package hotfies.perfectpartysystem;

import hotfies.perfectpartysystem.commands.PartyChatCommand;
import hotfies.perfectpartysystem.commands.PerfectPartyCommand;
import hotfies.perfectpartysystem.listeners.PlayerJoinListener;
import hotfies.perfectpartysystem.listeners.ServerSwitchListener;
import hotfies.perfectpartysystem.managers.ConfigManager;
import hotfies.perfectpartysystem.managers.DatabaseManager;
import hotfies.perfectpartysystem.managers.PartyManager;
import net.md_5.bungee.api.plugin.Plugin;

public class PerfectPartySystem extends Plugin {

    private ConfigManager configManager;
    private DatabaseManager databaseManager;
    private PartyManager partyManager;


    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.loadDefaultConfig();
        databaseManager = new DatabaseManager(this, configManager);
        partyManager = new PartyManager(this, databaseManager, configManager);

        getProxy().getPluginManager().registerCommand(this, new PerfectPartyCommand(this, partyManager, configManager));
        getProxy().getPluginManager().registerCommand(this, new PartyChatCommand(this, partyManager, configManager));
        getProxy().getPluginManager().registerListener(this, new PlayerJoinListener(this, partyManager));
        getProxy().getPluginManager().registerListener(this, new ServerSwitchListener(this, partyManager));
        partyManager.clearDatabase();


        getLogger().info("PerfectPartySystem has been enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("PerfectPartySystem has been disabled.");
    }

    public ConfigManager getConfigManager() {
        return configManager;
    }

    public DatabaseManager getDatabaseManager() {
        return databaseManager;
    }

    public PartyManager getPartyManager() {
        return partyManager;
    }

}