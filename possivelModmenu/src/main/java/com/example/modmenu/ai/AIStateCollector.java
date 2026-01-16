package com.example.modmenu.ai;

import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Responsible for gathering raw Minecraft state.
 * 
 * ARCHITECTURE ROLE:
 * This is a "dumb" sensor. It must ONLY output raw primitives.
 * - NO normalization (e.g. don't divide health by max_health)
 * - NO thresholds (e.g. don't categorize distance as "CLOSE")
 * - NO semantics (e.g. don't rename food_level to "energy")
 * 
 * Python's StateParser is the canonical authority for state semantics.
 */
public class AIStateCollector {
    private final Minecraft mc;

    private static final int STATE_VERSION = 1;

    public AIStateCollector(Minecraft mc) {
        this.mc = mc;
    }

    /**
     * Collects raw Minecraft state.
     * MUST strictly adhere to the StateParser schema in Python.
     */
    public JsonObject collect(Player player) {
        JsonObject state = new JsonObject();
        
        // 1. Metadata
        state.addProperty("state_version", STATE_VERSION);

        // 2. Observations (Normalized as per STATE_PROTOCOL.md)
        float maxHealth = player.getMaxHealth();
        float currentHealth = player.getHealth();
        float healthPercent = (maxHealth > 0) ? (currentHealth / maxHealth) : 0.0f;
        
        // health MUST be > 0 when agent is alive
        if (player.isAlive() && healthPercent <= 0.0f) {
            healthPercent = 0.01f;
        }
        state.addProperty("health", sanitize(healthPercent));
        
        float energyPercent = player.getFoodData().getFoodLevel() / 20.0f;
        state.addProperty("energy", sanitize(energyPercent));

        state.addProperty("pos_x", sanitize((float)player.getX()));
        state.addProperty("pos_y", sanitize((float)player.getY()));
        state.addProperty("pos_z", sanitize((float)player.getZ()));

        state.addProperty("yaw", sanitize(player.getYRot()));
        state.addProperty("pitch", sanitize(player.getXRot()));

        state.addProperty("is_colliding", player.horizontalCollision);
        state.addProperty("is_sprinting", player.isSprinting());
        state.addProperty("is_sneaking", player.isShiftKeyDown());
        state.addProperty("is_on_ground", player.onGround());
        state.addProperty("vertical_velocity", sanitize((float)player.getDeltaMovement().y));
        state.addProperty("is_underwater", player.isInWater());
        state.addProperty("is_in_hole", isInHole(player));
        
        float fallAhead = getFallDistance(player, 1, 0);
        state.addProperty("is_floor_ahead", fallAhead == 0);
        state.addProperty("fall_distance_ahead", sanitize(fallAhead));
        
        float fallFarAhead = getFallDistance(player, 2, 0);
        state.addProperty("is_floor_far_ahead", fallFarAhead == 0);
        state.addProperty("fall_distance_far_ahead", sanitize(fallFarAhead));
        state.addProperty("fall_distance_behind", sanitize(getFallDistance(player, 1, 180)));
        state.addProperty("fall_distance_left", sanitize(getFallDistance(player, 1, -90)));
        state.addProperty("fall_distance_right", sanitize(getFallDistance(player, 1, 90)));

        state.addProperty("is_colliding_ahead", isCollidingDir(player, 0));
        state.addProperty("is_colliding_behind", isCollidingDir(player, 180));
        state.addProperty("is_colliding_left", isCollidingDir(player, -90));
        state.addProperty("is_colliding_right", isCollidingDir(player, 90));

        
        state.addProperty("attack_cooldown", player.getAttackStrengthScale(0.0f));
        
        Entity target = getTarget();
        state.addProperty("target_id", target != null ? target.getId() : -1);
        state.addProperty("target_distance", target != null ? sanitize(player.distanceTo(target)) : 1000.0);
        
        if (target != null) {
            double dx = target.getX() - player.getX();
            double dy = (target.getY() + target.getBbHeight() * 0.5) - (player.getY() + player.getEyeHeight());
            double dz = target.getZ() - player.getZ();
            
            // Yaw
            float yawAngle = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
            float relativeYaw = Mth.wrapDegrees(yawAngle - player.getYRot());
            state.addProperty("target_yaw", sanitize(relativeYaw));
            
            // Pitch
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
            float pitchAngle = (float) -Math.toDegrees(Math.atan2(dy, horizontalDistance));
            float relativePitch = Mth.wrapDegrees(pitchAngle - player.getXRot());
            state.addProperty("target_pitch", sanitize(relativePitch));
            
            state.addProperty("target_height", sanitize(target.getBbHeight()));
        } else {
            state.addProperty("target_yaw", 0.0f);
            state.addProperty("target_pitch", 0.0f);
            state.addProperty("target_height", 0.0f);
        }

        // Inventory / Hotbar
        com.google.gson.JsonArray hotbar = new com.google.gson.JsonArray();
        for (int i = 0; i < 9; i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            JsonObject item = new JsonObject();
            item.addProperty("slot", i);
            item.addProperty("id", stack.getItem().toString());
            item.addProperty("count", stack.getCount());
            item.addProperty("is_weapon", stack.getItem() instanceof net.minecraft.world.item.SwordItem || 
                                          stack.getItem() instanceof net.minecraft.world.item.AxeItem);
            item.addProperty("is_ranged", stack.getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem);
            item.addProperty("is_food", stack.getItem().isEdible());
            item.addProperty("is_placeable", stack.getItem() instanceof net.minecraft.world.item.BlockItem);
            hotbar.add(item);
        }
        state.add("hotbar", hotbar);
        state.addProperty("selected_slot", player.getInventory().selected);
        state.addProperty("ammo_count", countAmmo(player));

        // 3. Nearby Entities
        state.add("nearby_entities", collectNearbyEntities(player));

        return state;
    }


    private int countAmmo(Player player) {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            net.minecraft.world.item.ItemStack stack = player.getInventory().getItem(i);
            if (stack.getItem() instanceof net.minecraft.world.item.ArrowItem) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private boolean isCollidingDir(Player player, float relativeYaw) {
        if (player.level() == null) return false;
        float yaw = player.getYRot() + relativeYaw;
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad) * 0.4;
        double dz = Math.cos(rad) * 0.4;
        net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(Mth.floor(player.getX() + dx), Mth.floor(player.getY()), Mth.floor(player.getZ() + dz));
        return isSolid(player.level(), pos) || isSolid(player.level(), pos.above());
    }

    private boolean isInHole(Player player) {
        if (player.level() == null) return false;
        net.minecraft.core.BlockPos pos = player.blockPosition();
        return isSolid(player.level(), pos.north()) && isSolid(player.level(), pos.south()) && isSolid(player.level(), pos.east()) && isSolid(player.level(), pos.west());
    }

    private boolean isSolid(net.minecraft.world.level.Level level, net.minecraft.core.BlockPos pos) {
        return level.getBlockState(pos).isViewBlocking(level, pos);
    }

    private float getFallDistance(Player player, int distance, float relativeYaw) {
        if (mc.level == null) return 0.0f;
        float yaw = player.getYRot() + relativeYaw;
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad) * (double)distance;
        double dz = Math.cos(rad) * (double)distance;

        int footY = Mth.floor(player.getY());
        int startX = Mth.floor(player.getX() + dx);
        int startZ = Mth.floor(player.getZ() + dz);
        
        int fall = 0;
        // Check up to 5 blocks down (Minecraft damage starts at 4)
        for (int y = footY - 1; y > footY - 6; y--) {
            net.minecraft.core.BlockPos pos = new net.minecraft.core.BlockPos(startX, y, startZ);
            if (!mc.level.getBlockState(pos).isAir()) {
                break;
            }
            fall++;
        }
        return (float)fall;
    }

    private boolean isBlockAt(Player player, int distance) {
        return getFallDistance(player, distance, 0) == 0;
    }

    private com.google.gson.JsonArray collectNearbyEntities(Player player) {
        com.google.gson.JsonArray entities = new com.google.gson.JsonArray();
        double radius = 32.0;
        java.util.List<Entity> list = player.level().getEntities(player, player.getBoundingBox().inflate(radius));
        
        for (Entity e : list) {
            if (e instanceof net.minecraft.world.entity.LivingEntity || e instanceof net.minecraft.world.entity.projectile.Projectile) {
                JsonObject entry = new JsonObject();
                entry.addProperty("id", e.getId());
                entry.addProperty("type", e.getType().toString());
                entry.addProperty("pos_x", sanitize((float)e.getX()));
                entry.addProperty("pos_y", sanitize((float)e.getY()));
                entry.addProperty("pos_z", sanitize((float)e.getZ()));
                entry.addProperty("distance", sanitize(player.distanceTo(e)));
                entry.addProperty("is_hostile", e instanceof net.minecraft.world.entity.monster.Enemy);
                entry.addProperty("is_on_ground", e.onGround());
                entry.addProperty("vertical_velocity", sanitize((float)e.getDeltaMovement().y));
                
                double dx = e.getX() - player.getX();
                double dz = e.getZ() - player.getZ();
                float angle = (float) (Mth.atan2(dz, dx) * 180.0 / Math.PI) - 90.0f;
                float relativeYaw = Mth.wrapDegrees(angle - player.getYRot());
                entry.addProperty("relative_yaw", sanitize(relativeYaw));
                
                entities.add(entry);
            }
        }
        return entities;
    }

    private float sanitize(float value) {
        if (Float.isNaN(value) || Float.isInfinite(value)) {
            return 0.0f;
        }
        return value;
    }

    private Entity getTarget() {
        HitResult hitResult = mc.hitResult;
        if (hitResult instanceof EntityHitResult entityHitResult) {
            return entityHitResult.getEntity();
        }
        
        // Fallback: Find nearest hostile entity if crosshair is empty
        if (mc.player != null && mc.level != null) {
            double radius = 32.0;
            return mc.level.getEntities(mc.player, mc.player.getBoundingBox().inflate(radius))
                .stream()
                .filter(e -> e instanceof net.minecraft.world.entity.monster.Enemy && e.isAlive())
                .min(java.util.Comparator.comparingDouble(mc.player::distanceTo))
                .orElse(null);
        }
        
        return null;
    }
}
