package com.example.elytramount;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

public class ElytraMountPlugin extends JavaPlugin implements Listener {

    private final Set<UUID> activeRiders = new HashSet<>();
    private final Map<UUID, UUID> riderToCarrier = new HashMap<>();

    @Override
    public void onEnable() {
        Bukkit.getPluginManager().registerEvents(this, this);
        getLogger().info("ElytraMount enabled.");
    }

    @Override
    public void onDisable() {
        for (UUID riderId : new HashSet<>(activeRiders)) {
            Player rider = Bukkit.getPlayer(riderId);
            if (rider != null && rider.isOnline()) {
                cleanupRide(rider);
            }
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player rider)) {
            sender.sendMessage(ChatColor.RED + "Only players can use this command.");
            return true;
        }

        if (!command.getName().equalsIgnoreCase("dismount")) {
            return false;
        }

        if (rider.isInsideVehicle()) {
            rider.leaveVehicle();
            cleanupRide(rider);
            rider.sendMessage(ChatColor.YELLOW + "You dismounted.");
        } else {
            rider.sendMessage(ChatColor.GRAY + "You are not mounted on anyone.");
        }
        return true;
    }

    @EventHandler
    public void onPlayerInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

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

        Player existingPlayerRider = getMountedPlayerRider(carrier);
        if (existingPlayerRider != null && !existingPlayerRider.getUniqueId().equals(rider.getUniqueId())) {
            rider.sendMessage(ChatColor.RED + carrier.getName() + " already has a rider.");
            return;
        }

        // Duplicate interaction packets (common with some proxies/clients) can fire immediately
        // after a successful mount. Ignore if this rider is already on this carrier.
        if (carrier.getPassengers().contains(rider)) {
            return;
        }

        if (!carrier.addPassenger(rider)) {
            rider.sendMessage(ChatColor.RED + "Could not mount " + carrier.getName() + ".");
            return;
        }

        activeRiders.add(rider.getUniqueId());
        riderToCarrier.put(rider.getUniqueId(), carrier.getUniqueId());
        hideMountedPair(rider, carrier);

        rider.sendMessage(ChatColor.GREEN + "You mounted " + carrier.getName() + ".");
        carrier.sendMessage(ChatColor.AQUA + rider.getName() + " mounted you. Fly with your Elytra!");
    }

    @EventHandler
    public void onEntityDismount(EntityDismountEvent event) {
        Entity dismounted = event.getEntity();
        if (!(dismounted instanceof Player rider)) {
            return;
        }

        if (activeRiders.contains(rider.getUniqueId())) {
            cleanupRide(rider);

            if (event.getDismounted() instanceof Player carrier) {
                carrier.sendMessage(ChatColor.GRAY + rider.getName() + " dismounted.");
            }
        }
    }

    private Player getMountedPlayerRider(Player carrier) {
        for (Entity passenger : carrier.getPassengers()) {
            if (passenger instanceof Player passengerPlayer) {
                return passengerPlayer;
            }
        }
        return null;
    }

    private boolean hasElytraEquipped(Player player) {
        ItemStack chestplate = player.getInventory().getChestplate();
        return chestplate != null && chestplate.getType() == Material.ELYTRA;
    }

    private void hideMountedPair(Player rider, Player carrier) {
        rider.hidePlayer(this, carrier);
        carrier.hidePlayer(this, rider);
    }

    private void showMountedPair(Player rider, Player carrier) {
        rider.showPlayer(this, carrier);
        carrier.showPlayer(this, rider);
    }

    private void cleanupRide(Player rider) {
        UUID carrierId = riderToCarrier.remove(rider.getUniqueId());
        activeRiders.remove(rider.getUniqueId());

        if (carrierId == null) {
            return;
        }

        Player carrier = Bukkit.getPlayer(carrierId);
        if (carrier != null && carrier.isOnline()) {
            showMountedPair(rider, carrier);
        }
    }
}
