package me.server.raidcores;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class RaidCores extends JavaPlugin implements Listener {

    private NamespacedKey coreTypeKey;
    private NamespacedKey coreHpKey;
    private NamespacedKey coreMaxHpKey;

    @Override
    public void onEnable() {
        this.coreTypeKey = new NamespacedKey(this, "core_type");
        this.coreHpKey = new NamespacedKey(this, "core_hp");
        this.coreMaxHpKey = new NamespacedKey(this, "core_max_hp");

        getServer().getPluginManager().registerEvents(this, this);
        registerRecipes();

        // Проверка дистанции 20х20
        Bukkit.getScheduler().runTaskTimer(this, this::updateHologramVisibility, 10L, 10L);
    }

    private void registerRecipes() {
        ItemStack basicCore = getBasicCoreItem();
        ItemStack reinforcedCore = getReinforcedCoreItem();
        NamespacedKey recipeKey = new NamespacedKey(this, "reinforced_core_recipe");
        ShapedRecipe recipe = new ShapedRecipe(recipeKey, reinforcedCore);
        recipe.shape(
                " D ",
                "CSC",
                " D "
        );
        recipe.setIngredient('D', Material.DIAMOND);
        recipe.setIngredient('S', Material.NETHERITE_SCRAP);
        recipe.setIngredient('C', new org.bukkit.inventory.RecipeChoice.ExactChoice(basicCore));

        Bukkit.addRecipe(recipe);
    }

    public ItemStack getBasicCoreItem() {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Ядро привата");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Прочность: " + ChatColor.GREEN + "4/4");
            lore.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + "15x15");
            meta.setLore(lore);
            meta.getPersistentDataContainer().set(coreTypeKey, PersistentDataType.STRING, "BASIC");
            item.setItemMeta(meta);
        }
        return item;
    }

    public ItemStack getReinforcedCoreItem() {
        ItemStack item = new ItemStack(Material.GOLD_BLOCK);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.GOLD + "" + ChatColor.BOLD + "Укреплённое ядро");
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Прочность: " + ChatColor.GREEN + "5/5");
            lore.add(ChatColor.GRAY + "Радиус защиты: " + ChatColor.YELLOW + "15x15");
            lore.add(ChatColor.DARK_GRAY + "• " + ChatColor.RED + "Паранойя: " + ChatColor.GRAY + "30% шанс звука брони при взрыве");
            meta.setLore(lore);
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(coreTypeKey, PersistentDataType.STRING, "REINFORCED");
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(coreTypeKey, PersistentDataType.STRING)) return;

        String type = pdc.get(coreTypeKey, PersistentDataType.STRING);
        int maxHp = "REINFORCED".equals(type) ? 5 : 4;

        Block block = event.getBlockPlaced();
        Location textLoc = block.getLocation().add(0.5, 1.4, 0.5);
        block.getWorld().spawn(textLoc, TextDisplay.class, entity -> {
            entity.setText(formatHp(maxHp, maxHp));
            entity.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            entity.setSeeThrough(false);
            entity.setVisibleByDefault(false);
            entity.getPersistentDataContainer().set(coreTypeKey, PersistentDataType.STRING, type);
            entity.getPersistentDataContainer().set(coreHpKey, PersistentDataType.INTEGER, maxHp);
            entity.getPersistentDataContainer().set(coreMaxHpKey, PersistentDataType.INTEGER, maxHp);
        });

        event.getPlayer().sendMessage(ChatColor.GREEN + "Ядро установлено! Прочность: " + maxHp + "/" + maxHp);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : new ArrayList<>(event.blockList())) {
            if (block.getType() != Material.GOLD_BLOCK) continue;

            TextDisplay display = findDisplayAbove(block);
            if (display == null) continue;

            event.blockList().remove(block);

            PersistentDataContainer pdc = display.getPersistentDataContainer();
            int currentHp = pdc.getOrDefault(coreHpKey, PersistentDataType.INTEGER, 4);
            int maxHp = pdc.getOrDefault(coreMaxHpKey, PersistentDataType.INTEGER, 4);
            String type = pdc.getOrDefault(coreTypeKey, PersistentDataType.STRING, "BASIC");

            boolean isTsarTnt = isTsarTntEntity(event.getEntity());

            if (currentHp > 1) {
                currentHp--;
                pdc.set(coreHpKey, PersistentDataType.INTEGER, currentHp);
                display.setText(formatHp(currentHp, maxHp));
                block.getWorld().playSound(block.getLocation(), Sound.BLOCK_ANVIL_LAND, 1.0f, 0.8f);

                if ("REINFORCED".equals(type) && ThreadLocalRandom.current().nextInt(100) < 30) {
                    if (event.getEntity() != null) {
                        block.getWorld().playSound(event.getEntity().getLocation(), Sound.ITEM_ARMOR_EQUIP_NETHERITE, 1.2f, 1.0f);
                    }
                }
            } else if (currentHp == 1) {
                if (isTsarTnt) {
                    display.remove();
                    block.setType(Material.AIR);
                    block.getWorld().spawnParticle(Particle.EXPLOSION_EMITTER, block.getLocation().add(0.5, 0.5, 0.5), 2);
                    block.getWorld().playSound(block.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.7f);
                } else {
                    block.getWorld().spawnParticle(Particle.ENCHANTED_HIT, block.getLocation().add(0.5, 0.5, 0.5), 15);
                    block.getWorld().playSound(block.getLocation(), Sound.ITEM_SHIELD_BLOCK, 1.0f, 0.5f);
                }
            }
        }
    }

    private void updateHologramVisibility() {
        for (World world : Bukkit.getWorlds()) {
            for (TextDisplay display : world.getEntitiesByClass(TextDisplay.class)) {
                if (!display.getPersistentDataContainer().has(coreHpKey, PersistentDataType.INTEGER)) continue;

                Location loc = display.getLocation();
                for (Player player : world.getPlayers()) {
                    boolean insideZone = Math.abs(player.getLocation().getX() - loc.getX()) <= 10
                            && Math.abs(player.getLocation().getZ() - loc.getZ()) <= 10
                            && Math.abs(player.getLocation().getY() - loc.getY()) <= 10
