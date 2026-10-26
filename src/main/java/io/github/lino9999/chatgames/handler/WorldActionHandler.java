package io.github.lino9999.chatgames.handler;

import io.github.lino9999.chatgames.api.ChallengeCategory;
import io.github.lino9999.chatgames.config.ConfigurationRegistry;
import io.github.lino9999.chatgames.session.RoundOrchestrator;
import io.github.lino9999.chatgames.session.WorldChallengeSession;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.inventory.CraftingInventory;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.plugin.java.JavaPlugin;

public class WorldActionHandler implements Listener {
    private static final String ARTIFICIAL_SPAWN_TAG = "cg_artificial_mob";

    private final JavaPlugin plugin;
    private final ConfigurationRegistry registry;
    private final RoundOrchestrator orchestrator;

    public WorldActionHandler(JavaPlugin plugin, ConfigurationRegistry registry, RoundOrchestrator orchestrator) {
        this.plugin = plugin;
        this.registry = registry;
        this.orchestrator = orchestrator;
    }

    private boolean isDisqualified(Player player) {
        if (!orchestrator.canParticipate(player)) {
            return true;
        }
        boolean allowCreative = registry.challenges().flag("global.allow-creative-mode", false);
        return !allowCreative && player.getGameMode() == GameMode.CREATIVE;
    }

    private void advanceChallenge(Player player, ChallengeCategory expectedCategory, int quantity) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge == null || challenge.getCategory() != expectedCategory) {
            return;
        }
        if (isDisqualified(player)) {
            return;
        }
        challenge.incrementProgress(player.getUniqueId(), quantity);
        if (challenge.hasCompletedQuota(player.getUniqueId())) {
            orchestrator.recordRoundVictory(player);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge != null && challenge.getCategory() == ChallengeCategory.MINING) {
            if (event.getBlock().getType() == challenge.getTargetMaterial()) {
                advanceChallenge(event.getPlayer(), ChallengeCategory.MINING, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBuild(BlockPlaceEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge != null && challenge.getCategory() == ChallengeCategory.BUILDING) {
            if (event.getBlock().getType() == challenge.getTargetMaterial()) {
                advanceChallenge(event.getPlayer(), ChallengeCategory.BUILDING, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMobSlay(EntityDeathEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge == null || challenge.getCategory() != ChallengeCategory.SLAYER) {
            return;
        }
        Player killer = event.getEntity().getKiller();
        if (killer == null || event.getEntityType() != challenge.getTargetEntity()) {
            return;
        }
        boolean allowSpawnerMobs = registry.challenges().flag("categories.slayer.count-spawner-entities", true);
        if (!allowSpawnerMobs && event.getEntity().hasMetadata(ARTIFICIAL_SPAWN_TAG)) {
            return;
        }
        advanceChallenge(killer, ChallengeCategory.SLAYER, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (!registry.challenges().flag("categories.slayer.count-spawner-entities", true)) {
            CreatureSpawnEvent.SpawnReason reason = event.getSpawnReason();
            if (reason == CreatureSpawnEvent.SpawnReason.SPAWNER || reason == CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) {
                event.getEntity().setMetadata(ARTIFICIAL_SPAWN_TAG, new FixedMetadataValue(plugin, Boolean.TRUE));
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFishingCatch(PlayerFishEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge == null || challenge.getCategory() != ChallengeCategory.ANGLING) {
            return;
        }
        if (event.getState() == PlayerFishEvent.State.CAUGHT_FISH && event.getCaught() instanceof Item itemEntity) {
            if (itemEntity.getItemStack().getType() == challenge.getTargetMaterial()) {
                advanceChallenge(event.getPlayer(), ChallengeCategory.ANGLING, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFoodConsume(PlayerItemConsumeEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge != null && challenge.getCategory() == ChallengeCategory.CULINARY) {
            if (event.getItem().getType() == challenge.getTargetMaterial()) {
                advanceChallenge(event.getPlayer(), ChallengeCategory.CULINARY, 1);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRecipeCraft(CraftItemEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge == null || challenge.getCategory() != ChallengeCategory.ARTISAN) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player) || isDisqualified(player)) {
            return;
        }
        ItemStack result = event.getRecipe().getResult();
        if (result.getType() != challenge.getTargetMaterial()) {
            return;
        }

        int produced = result.getAmount();
        ClickType click = event.getClick();
        if (click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT) {
            int maxByMatrix = computeMatrixYield(event.getInventory());
            int maxByStorage = computeInventoryCapacity(result, player.getInventory());
            produced = Math.min(maxByMatrix, maxByStorage);
        }

        if (produced > 0) {
            advanceChallenge(player, ChallengeCategory.ARTISAN, produced);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFurnaceExtract(InventoryClickEvent event) {
        WorldChallengeSession challenge = orchestrator.getActiveChallenge();
        if (challenge == null || challenge.getCategory() != ChallengeCategory.SMELTING) {
            return;
        }
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        if (event.getInventory() instanceof FurnaceInventory && event.getSlotType() == InventoryType.SlotType.RESULT) {
            ItemStack extracted = event.getCurrentItem();
            if (extracted != null && extracted.getType() == challenge.getTargetMaterial() && extracted.getAmount() > 0) {
                advanceChallenge(player, ChallengeCategory.SMELTING, extracted.getAmount());
            }
        }
    }

    private int computeMatrixYield(CraftingInventory craftingInventory) {
        ItemStack result = craftingInventory.getResult();
        if (result == null) {
            return 0;
        }
        int lowestStack = Integer.MAX_VALUE;
        for (ItemStack slot : craftingInventory.getMatrix()) {
            if (slot != null && slot.getType() != Material.AIR) {
                lowestStack = Math.min(lowestStack, slot.getAmount());
            }
        }
        return lowestStack == Integer.MAX_VALUE ? 0 : lowestStack * result.getAmount();
    }

    private int computeInventoryCapacity(ItemStack prototype, Inventory inventory) {
        int available = 0;
        int maxStack = prototype.getMaxStackSize();
        for (ItemStack slot : inventory.getStorageContents()) {
            if (slot == null || slot.getType() == Material.AIR) {
                available += maxStack;
            } else if (slot.isSimilar(prototype)) {
                available += Math.max(0, maxStack - slot.getAmount());
            }
        }
        return available;
    }
}
