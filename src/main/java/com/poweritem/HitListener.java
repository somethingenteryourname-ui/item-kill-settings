package com.poweritem;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.EntityEffect;
import org.bukkit.Material;
import org.bukkit.Statistic;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class HitListener implements Listener {

    private final PowerItem plugin;
    // victim -> killer name, used to set a custom death message
    private final Map<UUID, String> pendingKills = new HashMap<>();

    public HitListener(PowerItem plugin) {
        this.plugin = plugin;
    }

    // ignoreCancelled = true means if PvP is off or a region protects them, we do nothing.
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (event.getCause() != DamageCause.ENTITY_ATTACK) return; // direct melee hits only, not sweeps

        ItemStack weapon = attacker.getInventory().getItemInMainHand();
        if (weapon.getType().isAir()) return;
        ItemMeta meta = weapon.getItemMeta();
        if (meta == null) return;

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        if (!pdc.has(plugin.idKey(), PersistentDataType.STRING)) return; // not a power item

        Mode mode = Mode.from(pdc.get(plugin.modeKey(), PersistentDataType.STRING));

        if (mode == Mode.KILL || mode == Mode.POP) {
            // Cancelling the hit means no normal damage happens, so armor takes no durability damage.
            event.setCancelled(true);

            if (mode == Mode.POP && popTotem(victim, event.getDamage())) {
                return; // they had a totem and it popped
            }
            kill(victim, attacker);
            return;
        }

        // NORMAL mode: just add the bonus damage
        double bonus = pdc.getOrDefault(plugin.damageKey(), PersistentDataType.DOUBLE, 0.0);
        if (bonus > 0) {
            event.setDamage(event.getDamage() + bonus);
        }
    }

    private void kill(LivingEntity victim, Player attacker) {
        victim.setKiller(attacker); // so the kill counts for the attacker
        if (victim instanceof Player) {
            pendingKills.put(victim.getUniqueId(), attacker.getName());
        }
        // Setting health to 0 skips totems completely.
        victim.setHealth(0);
        pendingKills.remove(victim.getUniqueId());
    }

    // Returns true if the victim was holding a totem and it got popped.
    private boolean popTotem(LivingEntity victim, double hitDamage) {
        EntityEquipment equipment = victim.getEquipment();
        if (equipment == null) return false;

        EquipmentSlot slot = null;
        if (equipment.getItemInMainHand().getType() == Material.TOTEM_OF_UNDYING) {
            slot = EquipmentSlot.HAND;
        } else if (equipment.getItemInOffHand().getType() == Material.TOTEM_OF_UNDYING) {
            slot = EquipmentSlot.OFF_HAND;
        }
        if (slot == null) return false;

        // Use up the totem
        ItemStack totem = equipment.getItem(slot);
        totem.setAmount(totem.getAmount() - 1);
        equipment.setItem(slot, totem);

        // Same thing a real totem does
        victim.setHealth(1.0);
        for (PotionEffect effect : victim.getActivePotionEffects()) {
            victim.removePotionEffect(effect.getType());
        }
        victim.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 900, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 100, 1));
        victim.addPotionEffect(new PotionEffect(PotionEffectType.FIRE_RESISTANCE, 800, 0));
        victim.playEffect(EntityEffect.TOTEM_RESURRECT); // totem animation + sound

        if (victim instanceof Player player) {
            player.incrementStatistic(Statistic.USE_ITEM, Material.TOTEM_OF_UNDYING);
        }

        // Normal hit cooldown so totems can't be popped super fast
        victim.setNoDamageTicks(victim.getMaximumNoDamageTicks());
        victim.setLastDamage(hitDamage);
        return true;
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        String killer = pendingKills.get(event.getPlayer().getUniqueId());
        if (killer != null) {
            event.deathMessage(Component.text(
                    event.getPlayer().getName() + " was obliterated by " + killer, NamedTextColor.RED));
        }
    }
}
