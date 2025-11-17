package com.flyaway.flycontroller.listeners;

import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.managers.ConfigManager;
import com.flyaway.flycontroller.managers.FlightManager;
import com.flyaway.flycontroller.managers.PlayerManager;
import com.flyaway.flycontroller.models.FlightData;
import com.flyaway.flycontroller.utils.TimeFormatter;
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
    private final PlayerManager playerManager;
    private final FlightManager flightManager;

    public PlayerListener(FlyPlugin plugin) {
        this.plugin = plugin;
        this.configManager = plugin.getConfigManager();
        this.playerManager = plugin.getPlayerManager();
        this.flightManager = plugin.getFlightManager();
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
            if (!configManager.isWorldAllowed(player.getWorld())) {
                playerManager.sendMessage(player, configManager.getMessage("fly-command-not-allowed"));
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!configManager.isWorldAllowed(world)) {
            if (player.getAllowFlight() || flightManager.hasActiveFlightTimes(player.getUniqueId())) {
                flightManager.pauseActiveFlight(player, configManager.getMessage("disable-reason.change-world"));
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        if (flightManager.hasActiveFlightTimes(playerId)) {
            flightManager.pauseActiveFlight(player, configManager.getMessage("disable-reason.quit"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();

        boolean isAllowedWorld = configManager.isWorldAllowed(player.getWorld());

        FlightData data = plugin.getDataManager().loadPlayerData(playerId);

        if (data != null && data.getPausedTime() > 0) {
            long savedPausedTime = data.getPausedTime();

            flightManager.setPausedFlightTime(playerId, savedPausedTime);

            Map<@NotNull String, @NotNull String> timePlaceholders = Map.of("time", TimeFormatter.formatTime(savedPausedTime, configManager));
            if (isAllowedWorld) {
                if (flightManager.activatePausedFlight(player)) {
                    playerManager.sendMessage(player, configManager.getMessage("flight-restored", timePlaceholders));
                } else {
                    playerManager.sendMessage(player, configManager.getMessage("flight-restored-failed"));
                }
            } else {
                playerManager.sendMessage(player, configManager.getMessage("flight-saved-comeback", timePlaceholders));
            }
        }

        if (!isAllowedWorld) {
            flightManager.disableFlight(player);
        }
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            flightManager.handleCombat(attacker);
            flightManager.handleCombat(victim);
        }
    }
}
