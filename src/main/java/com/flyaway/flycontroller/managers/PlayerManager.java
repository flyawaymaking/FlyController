package com.flyaway.flycontroller.managers;

import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.commands.FlySpeedCommand;
import com.flyaway.flycontroller.commands.MFlyCommand;
import com.flyaway.flycontroller.listeners.PlayerListener;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.CommandSender;

public class PlayerManager {
    private final FlyPlugin plugin;
    private final ConfigManager configManager;
    private final MiniMessage miniMessage = MiniMessage.miniMessage();

    public PlayerManager(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    public void registerListeners() {
        plugin.getServer().getPluginManager().registerEvents(new PlayerListener(plugin), plugin);
    }

    public void registerCommands() {
        MFlyCommand mFlyCommand = new MFlyCommand(plugin);
        plugin.getCommand("mfly").setExecutor(mFlyCommand);
        plugin.getCommand("mfly").setTabCompleter(mFlyCommand);

        FlySpeedCommand flySpeedCommand = new FlySpeedCommand(plugin);
        plugin.getCommand("flyspeed").setExecutor(flySpeedCommand);
        plugin.getCommand("flyspeed").setTabCompleter(flySpeedCommand);
    }

    public void sendMessage(CommandSender sender, String message) {
        if (message.isEmpty()) return;
        String prefix = configManager.getPrefix();
        sender.sendMessage(miniMessage.deserialize(prefix + " " + message));
    }
}
