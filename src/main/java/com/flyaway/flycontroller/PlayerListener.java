package com.flyaway.flycontroller;

import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.jetbrains.annotations.NotNull;

import java.util.Map;
import java.util.UUID;


public class PlayerListener implements Listener {
    private final FlyPlugin plugin;
    private final ConfigManager configManager;

    public PlayerListener(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPlayerCommand(PlayerCommandPreprocessEvent event) {
        Player player = event.getPlayer();
        String message = event.getMessage().toLowerCase().trim();

        if (message.startsWith("/")) {
            message = message.substring(1);
        }

        String[] args = message.split(" ");
        String command = args[0];

        if (command.equals("fly") || command.equals("essentials:fly")) {
            if (!plugin.isWorldAllowed(player.getWorld())) {
                plugin.sendMessage(player, configManager.getMessage("fly-command-not-allowed"));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!plugin.isWorldAllowed(world)) {
            if (player.getAllowFlight() || plugin.getActiveFlightTimes().containsKey(player.getUniqueId())) {
                plugin.pauseActiveFlight(player, configManager.getMessage("disable-reason.change-world"));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (plugin.getActiveFlightTimes().containsKey(playerId)) {
            plugin.pauseActiveFlight(player, configManager.getMessage("disable-reason.quit"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        boolean isAllowedWorld = plugin.isWorldAllowed(player.getWorld());

        FlightData data = plugin.getDataManager().loadPlayerData(playerId);

        if (data != null && data.getPausedTime() > 0) {
            long savedPausedTime = data.getPausedTime();

            plugin.setPausedFlightTime(playerId, savedPausedTime);

            Map<@NotNull String, @NotNull String> timePlaceholders = Map.of("time", plugin.formatTime(savedPausedTime));
            if (isAllowedWorld) {
                if (plugin.activatePausedFlight(player)) {
                    plugin.sendMessage(player, configManager.getMessage("flight-restored", timePlaceholders));
                } else {
                    plugin.sendMessage(player, configManager.getMessage("flight-restored-failed"));
                }
            } else {
                plugin.sendMessage(player, configManager.getMessage("flight-saved-comeback", timePlaceholders));
            }
        }

        if (!isAllowedWorld) {
            plugin.disableFlight(player);
        }
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            plugin.handleCombat(attacker);
            plugin.handleCombat(victim);
        }
    }
}
