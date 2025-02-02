package com.magmaguy.elitemobs.events;

import com.magmaguy.elitemobs.MetadataHandler;
import com.magmaguy.elitemobs.api.CustomEventStartEvent;
import com.magmaguy.elitemobs.config.EventsConfig;
import com.magmaguy.elitemobs.config.customevents.CustomEventsConfig;
import com.magmaguy.elitemobs.config.customevents.CustomEventsConfigFields;
import com.magmaguy.elitemobs.mobconstructor.custombosses.CustomBossEntity;
import com.magmaguy.elitemobs.utils.VersionChecker;
import com.magmaguy.elitemobs.utils.WarningMessage;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

public class ActionEvent extends CustomEvent {

    public static ArrayList<ActionEvent> blueprintEvents = new ArrayList<>();
    public static ArrayList<ActionEvent> actionEvents = new ArrayList<>();
    private static HashSet<Player> playerCooldowns = new HashSet<>();
    public double chance;
    public List<Material> breakableMaterials;
    private Player player;

    public ActionEvent(CustomEventsConfigFields customEventsConfigFields) {
        super(customEventsConfigFields);
        this.chance = customEventsConfigFields.getChance();
        this.breakableMaterials = customEventsConfigFields.getBreakableMaterials();
        setPrimaryCustomBossFilenames(primaryCustomBossFilenames);
    }

    /**
     * Initializes events directly from the configuration files, and keeps a copy to run checks over in order to instantiate
     * events for the server to run
     *
     * @return Instantiated ActionEvent
     */
    public static void initializeBlueprintEvents() {
        if (!EventsConfig.actionEventsEnabled) return;
        for (CustomEventsConfigFields customEventsConfigFields : CustomEventsConfig.getCustomEvents().values()) {
            if (customEventsConfigFields.isEnabled())
                switch (customEventsConfigFields.getEventType()) {
                    case BREAK_BLOCK:
                    case FISH:
                    case TILL_SOIL:
                        blueprintEvents.add(new ActionEvent(customEventsConfigFields));
                }
        }
    }

    public boolean checkBlockBreakStartConditions(Material material) {
        if (ThreadLocalRandom.current().nextDouble() >= chance) return false;
        return breakableMaterials.contains(material);
    }

    public boolean checkFishStartConditions() {
        return !(ThreadLocalRandom.current().nextDouble() >= chance);
    }

    public boolean checkTillSoilStartConditions() {
        return !(ThreadLocalRandom.current().nextDouble() >= chance);
    }

    private void instantiateEvent(Location location, Player player) {
        this.player = player;
        ActionEvent actionEvent = new ActionEvent(customEventsConfigFields);
        actionEvent.setEventStartLocation(location);
        CustomEventStartEvent customEventStartEvent = new CustomEventStartEvent(actionEvent);
        if (customEventStartEvent.isCancelled()) return;
        if (!actionEvent.startConditions.areValid()) return;

        for (String filename : primaryCustomBossFilenames) {
            CustomBossEntity customBossEntity = CustomBossEntity.createCustomBossEntity(filename);
            if (customBossEntity == null) {
                new WarningMessage("Failed to generate custom boss " + filename + " ! This has cancelled action event " + customEventsConfigFields.getFilename() + " !");
                return;
            }
            customBossEntity.spawn(getEventStartLocation(), false);
            primaryEliteMobs.add(customBossEntity);
        }

        actionEvents.add(actionEvent);

        actionEvent.start();
    }

    @Override
    public void startModifiers() {
        playerCooldowns.add(player);
        Bukkit.getScheduler().runTaskLater(MetadataHandler.PLUGIN, () -> playerCooldowns.remove(player), 20L * 60L * EventsConfig.actionEventMinimumCooldown);
    }

    @Override
    public void eventWatchdog() {
    }

    @Override
    public void endModifiers() {
        actionEvents.remove(this);
    }

    public static class ActionEventEvents implements Listener {
        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
        public void onBlockBreakEvent(BlockBreakEvent event) {
            if (playerCooldowns.contains(event.getPlayer())) return;
            if (!CustomEvent.isLocationValid(event.getBlock().getLocation())) return;
            for (ActionEvent actionEvent : blueprintEvents)
                if (actionEvent.eventType.equals(EventType.BREAK_BLOCK))
                    if (actionEvent.checkBlockBreakStartConditions(event.getBlock().getType()))
                        actionEvent.instantiateEvent(event.getBlock().getLocation().clone().add(new Vector(0.5, 0, 0.5)), event.getPlayer());
        }


        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
        public void onFishEvent(PlayerFishEvent event) {
            if (event.getCaught() == null) return;
            if (playerCooldowns.contains(event.getPlayer())) return;
            if (!CustomEvent.isLocationValid(event.getCaught().getLocation())) return;
            for (ActionEvent actionEvent : blueprintEvents)
                if (actionEvent.eventType.equals(EventType.FISH))
                    if (actionEvent.checkFishStartConditions())
                        actionEvent.instantiateEvent(event.getCaught().getLocation(), event.getPlayer());
        }

        @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
        public void onTillSoil(PlayerInteractEvent event) {
            if (!event.getAction().equals(Action.RIGHT_CLICK_BLOCK)) return;
            if (event.getClickedBlock() == null) return;
            if (playerCooldowns.contains(event.getPlayer())) return;
            Location location = event.getClickedBlock().getLocation().clone().add(new Vector(0.5, 1, 0.5));
            if (!CustomEvent.isLocationValid(location)) return;
            if (!(event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.DIAMOND_HOE) ||
                    event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.IRON_HOE) ||
                    event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.GOLDEN_HOE) ||
                    event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.STONE_HOE) ||
                    event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.WOODEN_HOE) ||
                    !VersionChecker.serverVersionOlderThan(16, 0) &&
                            event.getPlayer().getInventory().getItemInMainHand().getType().equals(Material.NETHERITE_HOE)))
                return;
            if (!(event.getClickedBlock().getType().equals(Material.DIRT) ||
                    event.getClickedBlock().getType().equals(Material.GRASS_BLOCK))) return;
            for (ActionEvent actionEvent : blueprintEvents)
                if (actionEvent.eventType.equals(EventType.TILL_SOIL))
                    if (actionEvent.checkTillSoilStartConditions())
                        actionEvent.instantiateEvent(location, event.getPlayer());
        }
    }
}