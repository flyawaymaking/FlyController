package com.flyaway.flycontroller.listeners;

import com.flyaway.flycontroller.FlyPlugin;
import com.flyaway.flycontroller.managers.ConfigManager;
import com.flyaway.flycontroller.managers.FlightManager;
import com.flyaway.flycontroller.managers.PlayerManager;
import com.flyaway.flycontroller.models.FlightData;
import com.flyaway.flycontroller.utils.TimeFormatter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

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

        if (isFlyCommand(message) && !configManager.isWorldAllowed(player.getWorld())) {
            playerManager.sendMessage(player, configManager.getMessage("fly-command-not-allowed"));
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerChangeWorld(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();

        if (!configManager.isWorldAllowed(player.getWorld()) && flightManager.shouldManageFlight(player)) {
            flightManager.pauseActiveFlight(player, configManager.getMessage("disable-reason.change-world"));
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();

        if (flightManager.hasActiveFlightTimes(player.getUniqueId())) {
            flightManager.pauseActiveFlight(player, configManager.getMessage("disable-reason.quit"));
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        UUID playerId = player.getUniqueId();
        boolean isWorldAllowed = configManager.isWorldAllowed(player.getWorld());

        handlePausedFlight(player, playerId, isWorldAllowed);

        if (!isWorldAllowed) {
            flightManager.disableFlight(player);
        }
    }

    @EventHandler
    public void onPlayerAttack(EntityDamageByEntityEvent event) {
        if (!configManager.needPvpDisableFlight()) return;

        if (event.getDamager() instanceof Player attacker && event.getEntity() instanceof Player victim) {
            flightManager.handleCombat(attacker);
            flightManager.handleCombat(victim);
        }
    }

    // Вспомогательные методы
    private boolean isFlyCommand(String message) {
        if (message.startsWith("/")) {
            message = message.substring(1);
        }
        String command = message.split(" ")[0];
        return command.equals("fly") || command.equals("essentials:fly");
    }

    private void handlePausedFlight(Player player, UUID playerId, boolean isWorldAllowed) {
        FlightData data = plugin.getDataManager().loadPlayerData(playerId);
        if (data == null || data.getPausedTime() <= 0) return;

        long savedPausedTime = data.getPausedTime();
        flightManager.setPausedFlightTime(playerId, savedPausedTime);

        Map<String, String> timePlaceholders = Map.of("time", TimeFormatter.formatTime(savedPausedTime, configManager));

        if (isWorldAllowed) {
            if (flightManager.activatePausedFlight(player)) {
                playerManager.sendMessage(player, configManager.getMessage("flight-restored", timePlaceholders));
            } else {
                playerManager.sendMessage(player, configManager.getMessage("flight-restored-failed"));
            }
        } else {
            playerManager.sendMessage(player, configManager.getMessage("flight-saved-comeback", timePlaceholders));
        }
    }
}
