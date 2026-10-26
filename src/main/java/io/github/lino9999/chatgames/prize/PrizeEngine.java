package io.github.lino9999.chatgames.prize;

import io.github.lino9999.chatgames.config.ConfigurationRegistry;
import io.github.lino9999.chatgames.text.ChatRenderer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.concurrent.ThreadLocalRandom;

public class PrizeEngine {
    private final JavaPlugin plugin;
    private final ConfigurationRegistry registry;

    public PrizeEngine(JavaPlugin plugin, ConfigurationRegistry registry) {
        this.plugin = plugin;
        this.registry = registry;
    }

    public void grantPuzzlePrizes(String categoryId, int podiumSlot, Player winner) {
        List<String> poolIds = registry.prizes().lines("podium-assignments." + categoryId + "." + podiumSlot);
        if (poolIds.isEmpty()) {
            poolIds = registry.prizes().lines("podium-assignments.default." + podiumSlot);
        }
        if (poolIds.isEmpty() && podiumSlot > 1) {
            poolIds = registry.prizes().lines("podium-assignments.default.1");
        }
        grantFromPoolList(poolIds, winner);
    }

    public void grantChallengePrizes(List<String> poolIds, Player winner) {
        if (poolIds == null || poolIds.isEmpty()) {
            poolIds = registry.prizes().lines("podium-assignments.default.1");
        }
        grantFromPoolList(poolIds, winner);
    }

    private void grantFromPoolList(List<String> poolIds, Player winner) {
        if (winner == null || poolIds == null || poolIds.isEmpty()) {
            return;
        }
        List<PrizePool> eligiblePools = new ArrayList<>();
        int onlineCount = Bukkit.getOnlinePlayers().size();

        for (String id : poolIds) {
            PrizePool pool = parsePool(id);
            if (pool == null) {
                continue;
            }
            if (onlineCount < pool.minOnline()) {
                continue;
            }
            if (!pool.requiredPermission().isBlank() && !winner.hasPermission(pool.requiredPermission())) {
                continue;
            }
            eligiblePools.add(pool);
        }

        if (eligiblePools.isEmpty()) {
            return;
        }

        PrizePool chosen = selectWeightedPool(eligiblePools);
        if (chosen != null) {
            dispatchPoolActions(chosen, winner);
        }
    }

    private PrizePool parsePool(String poolId) {
        ConfigurationSection sec = registry.prizes().section("pools." + poolId);
        if (sec == null) {
            return null;
        }
        double weight = Math.max(0.1, sec.getDouble("weight", 100.0));
        int minOnline = Math.max(0, sec.getInt("min-online", 1));
        String perm = sec.getString("required-permission", "");
        List<Map<?, ?>> rawActions = sec.getMapList("actions");
        List<PrizeAction> actions = new ArrayList<>();

        for (Map<?, ?> map : rawActions) {
            String type = map.get("type") != null ? String.valueOf(map.get("type")).toUpperCase(Locale.ROOT) : "CONSOLE_COMMAND";
            String value = map.get("value") != null ? String.valueOf(map.get("value")) : "";
            String mat = map.get("material") != null ? String.valueOf(map.get("material")) : "IRON_INGOT";
            int amount = 1;
            if (map.get("amount") instanceof Number num) {
                amount = num.intValue();
            }
            double prob = 100.0;
            if (map.get("probability") instanceof Number num) {
                prob = num.doubleValue();
            }
            actions.add(new PrizeAction(type, value, mat, Math.max(1, amount), prob));
        }

        return new PrizePool(poolId, weight, minOnline, perm != null ? perm : "", actions);
    }

    private PrizePool selectWeightedPool(List<PrizePool> pools) {
        if (pools.size() == 1) {
            return pools.get(0);
        }
        NavigableMap<Double, PrizePool> wheel = new TreeMap<>();
        double cumulative = 0.0;
        for (PrizePool pool : pools) {
            cumulative += pool.weight();
            wheel.put(cumulative, pool);
        }
        double roll = ThreadLocalRandom.current().nextDouble() * cumulative;
        var entry = wheel.higherEntry(roll);
        return entry != null ? entry.getValue() : pools.get(0);
    }

    private void dispatchPoolActions(PrizePool pool, Player winner) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            String prefix = registry.prefix();
            for (PrizeAction action : pool.actions()) {
                if (action.probability() < 100.0) {
                    double roll = ThreadLocalRandom.current().nextDouble() * 100.0;
                    if (roll > action.probability()) {
                        continue;
                    }
                }
                String resolvedText = action.value().replace("{player}", winner.getName());
                switch (action.actionType()) {
                    case "GIVE_ITEM" -> deliverItemSafely(winner, action.materialName(), action.itemAmount());
                    case "CONSOLE_COMMAND" -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolvedText);
                    case "PLAYER_COMMAND" -> winner.performCommand(resolvedText);
                    case "OP_COMMAND" -> {
                        boolean alreadyOp = winner.isOp();
                        try {
                            winner.setOp(true);
                            winner.performCommand(resolvedText);
                        } finally {
                            winner.setOp(alreadyOp);
                        }
                    }
                    case "PLAYER_MESSAGE" -> ChatRenderer.deliver(winner, resolvedText, prefix);
                    case "BROADCAST" -> {
                        for (Player online : Bukkit.getOnlinePlayers()) {
                            ChatRenderer.deliver(online, resolvedText, prefix);
                        }
                    }
                    default -> {
                    }
                }
            }
        });
    }

    private void deliverItemSafely(Player recipient, String materialName, int quantity) {
        Material mat = Material.matchMaterial(materialName);
        if (mat == null || mat == Material.AIR) {
            return;
        }
        ItemStack stack = new ItemStack(mat, Math.max(1, quantity));
        Map<Integer, ItemStack> leftover = recipient.getInventory().addItem(stack);
        if (!leftover.isEmpty()) {
            for (ItemStack drop : leftover.values()) {
                recipient.getWorld().dropItemNaturally(recipient.getLocation(), drop);
            }
            String overflowMsg = registry.lang().text("system.inventory-overflow", "");
            if (!overflowMsg.isBlank()) {
                ChatRenderer.deliver(recipient, overflowMsg, registry.prefix());
            }
        }
    }
}
