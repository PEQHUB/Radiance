package com.radiance.client.material;

import com.radiance.client.option.Options;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.registry.Registries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Entity material system: assigns PBR material categories to entity types.
 * Entity materials occupy ordinals 200-254 in the MaterialClassMapping SSBO,
 * sharing the same pipeline as block materials (no shader changes needed).
 *
 * The vertex materialBlockType (bits 8-15) carries the entity ordinal,
 * set via PBRVertexConsumer.entityMaterialType ThreadLocal during render.
 */
public class EntityMaterial {
    private static final Logger LOGGER = LoggerFactory.getLogger(EntityMaterial.class);

    public static final int ENTITY_ORDINAL_BASE = 200;

    public enum Category {
        PASSIVE(0),
        HOSTILE(1),
        ARTHROPOD(2),
        UNDEAD(3),
        AQUATIC(4),
        GOLEM(5),
        VILLAGER(6),
        BLAZE(7),
        ENDERMAN(8),
        PLAYER(9),
        GUARDIAN(10),
        WARDEN(11),
        ENDER_DRAGON(12),
        WITHER(13),
        GHAST(14),
        SLIME(15),
        SHULKER(16),
        STRIDER(17),
        BREEZE(18),
        ARMOR_STAND(19);

        public final int index;

        Category(int index) {
            this.index = index;
        }

        public int getOrdinal() {
            return ENTITY_ORDINAL_BASE + index;
        }
    }

    private static final Map<EntityType<?>, Category> TYPE_MAP = new HashMap<>();
    private static boolean initialized = false;
    private static int activeCount = 0;

    public static void init() {
        if (initialized) return;
        initialized = true;

        // Passive mobs
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN,
                EntityType.HORSE, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA,
                EntityType.RABBIT, EntityType.FOX, EntityType.PARROT, EntityType.CAT,
                EntityType.WOLF, EntityType.OCELOT, EntityType.PANDA, EntityType.BEE,
                EntityType.MOOSHROOM, EntityType.FROG, EntityType.CAMEL, EntityType.SNIFFER,
                EntityType.ARMADILLO, EntityType.BAT}) {
            TYPE_MAP.put(type, Category.PASSIVE);
        }

        // Hostile mobs
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.ZOMBIE, EntityType.CREEPER, EntityType.WITCH,
                EntityType.PILLAGER, EntityType.VINDICATOR, EntityType.EVOKER,
                EntityType.RAVAGER, EntityType.PIGLIN, EntityType.PIGLIN_BRUTE,
                EntityType.HOGLIN, EntityType.ZOGLIN}) {
            TYPE_MAP.put(type, Category.HOSTILE);
        }

        // Arthropods
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.SPIDER, EntityType.CAVE_SPIDER, EntityType.SILVERFISH,
                EntityType.ENDERMITE}) {
            TYPE_MAP.put(type, Category.ARTHROPOD);
        }

        // Undead
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.SKELETON, EntityType.WITHER_SKELETON, EntityType.STRAY,
                EntityType.HUSK, EntityType.DROWNED, EntityType.ZOMBIE_VILLAGER,
                EntityType.PHANTOM, EntityType.ZOMBIFIED_PIGLIN, EntityType.BOGGED}) {
            TYPE_MAP.put(type, Category.UNDEAD);
        }

        // Aquatic
        for (EntityType<?> type : new EntityType<?>[]{
                EntityType.COD, EntityType.SALMON, EntityType.TROPICAL_FISH,
                EntityType.PUFFERFISH, EntityType.SQUID, EntityType.GLOW_SQUID,
                EntityType.DOLPHIN, EntityType.TURTLE, EntityType.AXOLOTL, EntityType.TADPOLE}) {
            TYPE_MAP.put(type, Category.AQUATIC);
        }

        // Golems
        TYPE_MAP.put(EntityType.IRON_GOLEM, Category.GOLEM);
        TYPE_MAP.put(EntityType.SNOW_GOLEM, Category.GOLEM);

        // Villagers
        TYPE_MAP.put(EntityType.VILLAGER, Category.VILLAGER);
        TYPE_MAP.put(EntityType.WANDERING_TRADER, Category.VILLAGER);

        // Unique mobs
        TYPE_MAP.put(EntityType.BLAZE, Category.BLAZE);
        TYPE_MAP.put(EntityType.ENDERMAN, Category.ENDERMAN);
        TYPE_MAP.put(EntityType.GUARDIAN, Category.GUARDIAN);
        TYPE_MAP.put(EntityType.ELDER_GUARDIAN, Category.GUARDIAN);
        TYPE_MAP.put(EntityType.WARDEN, Category.WARDEN);
        TYPE_MAP.put(EntityType.ENDER_DRAGON, Category.ENDER_DRAGON);
        TYPE_MAP.put(EntityType.WITHER, Category.WITHER);
        TYPE_MAP.put(EntityType.GHAST, Category.GHAST);
        TYPE_MAP.put(EntityType.SLIME, Category.SLIME);
        TYPE_MAP.put(EntityType.MAGMA_CUBE, Category.SLIME);
        TYPE_MAP.put(EntityType.SHULKER, Category.SHULKER);
        TYPE_MAP.put(EntityType.STRIDER, Category.STRIDER);
        TYPE_MAP.put(EntityType.BREEZE, Category.BREEZE);
        TYPE_MAP.put(EntityType.ARMOR_STAND, Category.ARMOR_STAND);
        TYPE_MAP.put(EntityType.PLAYER, Category.PLAYER);

        activeCount = Category.values().length;
        // Entity material ordinals 200-219 use the generic dielectric defaults
        // already set by the Options static initializer (roughness=80, metallic=0,
        // IOR=1.5, F0=0.04). User tunes per-category values via the UI.

        LOGGER.info("Entity material system initialized: {} categories, {} entity types mapped",
            activeCount, TYPE_MAP.size());
    }

    /**
     * Returns the material ordinal for the given entity, or -1 if not mapped.
     */
    public static int getOrdinalForEntity(Entity entity) {
        if (!initialized) return -1;
        Category cat = TYPE_MAP.get(entity.getType());
        return cat != null ? cat.getOrdinal() : -1;
    }

    /**
     * Returns the number of active entity material categories.
     */
    public static int getActiveCount() {
        return activeCount;
    }

    /**
     * Returns the category name for a given ordinal (200+), or null if out of range.
     */
    public static String getCategoryName(int ordinal) {
        int index = ordinal - ENTITY_ORDINAL_BASE;
        Category[] cats = Category.values();
        if (index >= 0 && index < cats.length) {
            return cats[index].name();
        }
        return null;
    }

    /**
     * Returns all categories for UI iteration.
     */
    public static Category[] getCategories() {
        return Category.values();
    }
}
