package io.th0rgal.oraxen.mechanics.provided.gameplay.custom_block.stringblock;

import io.papermc.paper.event.entity.EntityInsideBlockEvent;
import io.th0rgal.oraxen.OraxenPlugin;
import io.th0rgal.oraxen.api.OraxenBlocks;
import io.th0rgal.oraxen.api.OraxenFurniture;
import io.th0rgal.oraxen.api.events.custom_block.stringblock.OraxenStringBlockPlaceEvent;
import io.th0rgal.oraxen.utils.BlockHelpers;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class StringBlockMechanicListener implements Listener {

    public static class StringBlockMechanicPaperListener implements Listener {
        @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
        public void onEnteringTripwire(EntityInsideBlockEvent event) {
            if (event.getBlock().getType() == Material.TRIPWIRE)
                event.setCancelled(true);
        }
    }

    public static class StringBlockMechanicPhysicsListener implements Listener {

        @EventHandler(priority = EventPriority.MONITOR)
        public void tripwireEvent(BlockPhysicsEvent event) {
            Block block = event.getBlock();
            if (event.getChangedType() != Material.TRIPWIRE) return;
            if (event.getSourceBlock() == event.getBlock()) return;
            event.setCancelled(true);

            for (BlockFace f : BlockFace.values()) {
                if (!f.isCartesian() || f.getModY() != 0 || f == BlockFace.SELF) continue; // Only take N/S/W/E
                final Block changed = block.getRelative(f);
                if (changed.getType() != Material.TRIPWIRE) continue;

                final BlockData data = changed.getBlockData().clone();
                Bukkit.getScheduler().runTaskLater(OraxenPlugin.get(), Runnable ->
                        changed.setBlockData(data, false), 1L);
            }

            // Stores the pre-change blockdata and applies it on next tick to prevent the block from updating
            final BlockData blockData = block.getBlockData().clone();
            Bukkit.getScheduler().runTaskLater(OraxenPlugin.get(), Runnable -> {
                if (block.getType().isAir()) return;
                block.setBlockData(blockData, false);
            }, 1L);
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onPistonPush(BlockPistonExtendEvent event) {
            List<Block> tripwireList = event.getBlocks().stream().filter(block -> block.getType().equals(Material.TRIPWIRE)).toList();

            for (Block block : tripwireList) {
                final StringBlockMechanic mechanic = OraxenBlocks.getStringMechanic(block);
                if (mechanic == null) return;

                block.setType(Material.AIR, false);

                if (mechanic.hasLight())
                    mechanic.light().removeBlockLight(block);
                mechanic.drop().spawns(block.getLocation(), new ItemStack(Material.AIR));
            }
        }

        @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
        public void onBreak(BlockBreakEvent event) {
            final Block block = event.getBlock();
            final Block blockAbove = block.getRelative(BlockFace.UP);
            final Player player = event.getPlayer();

            for (BlockFace face : BlockFace.values()) {
                if (face == BlockFace.SELF && !face.isCartesian()) continue;
                if (block.getType() == Material.TRIPWIRE || block.getType() == Material.NOTE_BLOCK) break;
                if (OraxenFurniture.isFurniture(block.getLocation())) break;
                if (block.getRelative(face).getType() == Material.TRIPWIRE) {
                    if (player.getGameMode() != GameMode.CREATIVE) block.breakNaturally(player.getInventory().getItemInMainHand(), true);
                    else block.setType(Material.AIR);
                    if (BlockHelpers.isReplaceable(blockAbove.getType())) blockAbove.breakNaturally(true);
                    Bukkit.getScheduler().runTaskLater(OraxenPlugin.get(), Runnable ->
                            StringMechanicHelpers.fixClientsideUpdate(block.getLocation()), 1);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlacingString(PlayerInteractEvent event) {
        if (event.getItem() == null || event.getItem().getType() != Material.STRING) return;
        if (!StringBlockMechanicFactory.getInstance().disableVanillaString) return;

        event.setUseItemInHand(Event.Result.DENY);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreakingTall(final BlockBreakEvent event) {
        Block block = event.getBlock().getRelative(BlockFace.DOWN);
        if (event.getBlock().getType() != Material.TRIPWIRE || OraxenBlocks.isOraxenStringBlock(event.getBlock())) return;
        event.setDropItems(false);
        OraxenBlocks.remove(block.getLocation(), event.getPlayer());
    }

    @EventHandler
    public void onPlaceStringOnString(OraxenStringBlockPlaceEvent event) {
        Block block = event.getBlock();
        Block below = block.getRelative(BlockFace.DOWN);
        if (OraxenBlocks.isOraxenStringBlock(below)) event.setCancelled(true);
        if (below.getType() == Material.TRIPWIRE && OraxenBlocks.isOraxenStringBlock(below.getRelative(BlockFace.DOWN))) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onWaterUpdate(final BlockFromToEvent event) {
        final Block changed = event.getToBlock();
        final Block changedBelow = changed.getRelative(BlockFace.DOWN);
        if (!event.getBlock().isLiquid() || changed.getType() != Material.TRIPWIRE) return;

        event.setCancelled(true);
        StringBlockMechanic mechanicBelow = OraxenBlocks.getStringMechanic(changedBelow);
        if (OraxenBlocks.isOraxenStringBlock(changed))
            OraxenBlocks.remove(changed.getLocation(), null, true);
        else if (mechanicBelow != null && mechanicBelow.isTall())
            OraxenBlocks.remove(changedBelow.getLocation(), null, true);

    }
}