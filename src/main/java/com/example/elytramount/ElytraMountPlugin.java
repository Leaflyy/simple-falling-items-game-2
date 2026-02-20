package com.example.elytramount;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

public class ElytraMountPlugin extends JavaPlugin implements Listener {

    private static final int EFFECT_DURATION_TICKS = 20 * 10;
    private final Set<UUID> activeRiders = new HashSet<>();
    private BukkitTask effectRefreshTask;

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        startEffectRefreshTask();
        getLogger().info("ElytraMount enabled.");
    }

    @Override
    public void onDisable() {
        if (effectRefreshTask != null) {
            effectRefreshTask.cancel();
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player rider)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("dismount")) {
            if (rider.isInsideVehicle()) {
                rider.leaveVehicle();
                clearVisualEffects(rider);
                rider.sendMessage(ChatColor.YELLOW + "You dismounted.");
            } else {
                rider.sendMessage(ChatColor.GRAY + "You are not mounted on anyone.");
            }
            activeRiders.remove(rider.getUniqueId());
            return true;
        }

        return false;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (!(event.getRightClicked() instanceof Player carrier)) {
            return;
        }

        Player rider = event.getPlayer();

        if (rider.equals(carrier)) {
            return;
        }

        if (rider.isInsideVehicle()) {
            rider.sendMessage(ChatColor.RED + "You are already mounted.");
            return;
        }

        if (!hasElytraEquipped(carrier)) {
            rider.sendMessage(ChatColor.RED + carrier.getName() + " is not wearing an Elytra.");
            return;
        }

        if (!carrier.getPassengers().isEmpty()) {
            rider.sendMessage(ChatColor.RED + carrier.getName() + " already has a rider.");
            return;
        }

        carrier.addPassenger(rider);
        activeRiders.add(rider.getUniqueId());

        applyClearVision(rider);
        applyClearVision(carrier);

        rider.sendMessage(ChatColor.GREEN + "You mounted " + carrier.getName() + ".");
        carrier.sendMessage(ChatColor.AQUA + rider.getName() + " mounted you. Fly with your Elytra!");
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        Entity dismounted = event.getEntity();
        if (!(dismounted instanceof Player rider)) {
            return;
        }

        if (activeRiders.remove(rider.getUniqueId())) {
            clearVisualEffects(rider);
            if (event.getDismounted() instanceof Player carrier) {
                clearVisualEffects(carrier);
                carrier.sendMessage(ChatColor.GRAY + rider.getName() + " dismounted.");
            }
        }
    }

    private void startEffectRefreshTask() {
        effectRefreshTask = Bukkit.getScheduler().runTaskTimer(this, () -> {
            Set<UUID> staleRiders = new HashSet<>();

            for (UUID riderId : activeRiders) {
                Player rider = Bukkit.getPlayer(riderId);
                if (rider == null || !rider.isOnline()) {
                    staleRiders.add(riderId);
                    continue;
                }

                Entity vehicle = rider.getVehicle();
                if (!(vehicle instanceof Player carrier) || !hasElytraEquipped(carrier)) {
                    clearVisualEffects(rider);
                    if (vehicle instanceof Player playerCarrier) {
                        clearVisualEffects(playerCarrier);
                    }
                    staleRiders.add(riderId);
                    continue;
                }

                applyClearVision(rider);
                applyClearVision(carrier);
            }

            activeRiders.removeAll(staleRiders);
        }, 20L, 20L * 5L);
    }

    private boolean hasElytraEquipped(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        return chestplate != null && chestplate.getType() == Material.ELYTRA;
    }

    private void applyClearVision(Player player) {
        player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, EFFECT_DURATION_TICKS, 0, true, false, false));
    }

    private void clearVisualEffects(Player player) {
        player.removePotionEffect(PotionEffectType.NIGHT_VISION);
    }
}
