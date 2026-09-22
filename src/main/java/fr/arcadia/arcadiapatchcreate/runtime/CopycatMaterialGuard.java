package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Constructor;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Fallback material for Copycats+ multistate blocks.
 *
 * <p>{@code MaterialItemStorage.getMaterialItem(String)} returns null when the requested
 * property is missing, which happens while a multistate storage is empty. Every caller in
 * Copycats+ 3.0.9 dereferences the result without a null check, so an empty storage is a
 * crash: {@code IMultiStateCopycatBlockEntity.getMaterial()} asks for the first stored
 * property and falls back to the literal name {@code "material"}, which an empty storage
 * never holds.
 *
 * <p>Copycats+ clears that storage when a chunk unloads or a block is removed (3.0.6). On a
 * client, chunk meshing reads the neighbour's material from a worker thread, so a storage
 * cleared mid-build is read empty and the render thread dies with a NullPointerException in
 * {@code CopycatSlidingDoorBlock.hidesNeighborFace}. Reported on Arcadia twice on
 * 2026-09-22, on two different builds, with Copycats+ 3.0.9 and Sodium 0.8.13.
 *
 * <p>This guard answers those lookups with a copycat base material instead of null. The
 * block renders as an unfilled copycat for the frame, then redraws normally once the
 * storage is populated again. Nothing is written back into the storage: the miss is a
 * transient state of another mod's data, and a render worker must not mutate it.
 */
public final class CopycatMaterialGuard {

    private static final ResourceLocation COPYCAT_BASE_ID =
        ResourceLocation.fromNamespaceAndPath("create", "copycat_base");

    private static final AtomicLong FALLBACKS = new AtomicLong();

    private static volatile boolean initialised;
    private static volatile Constructor<?> materialItemConstructor;

    private CopycatMaterialGuard() {
    }

    /**
     * @return a {@code MaterialItemStorage.MaterialItem} holding the copycat base material,
     *         or null when the class layout is not the expected one, in which case the
     *         caller leaves the original null in place.
     */
    public static Object fallbackItem() {
        Constructor<?> constructor = resolveConstructor();
        if (constructor == null) {
            return null;
        }

        try {
            Object item = constructor.newInstance(baseMaterial(), ItemStack.EMPTY);
            long count = FALLBACKS.incrementAndGet();
            if (count == 1L || count % 1000L == 0L) {
                ArcadiaPatchCreate.LOGGER.debug(
                    "[ArcadiaPatchCreate] Answered {} Copycats+ material lookups on an empty storage.",
                    count
                );
            }
            return item;
        } catch (ReflectiveOperationException | RuntimeException error) {
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Could not build a Copycats+ fallback material, leaving the lookup as it was.",
                error
            );
            materialItemConstructor = null;
            return null;
        }
    }

    public static long fallbackCount() {
        return FALLBACKS.get();
    }

    private static BlockState baseMaterial() {
        Block copycatBase = BuiltInRegistries.BLOCK.get(COPYCAT_BASE_ID);
        if (copycatBase == null || copycatBase == Blocks.AIR) {
            return Blocks.AIR.defaultBlockState();
        }
        return copycatBase.defaultBlockState();
    }

    private static Constructor<?> resolveConstructor() {
        if (initialised) {
            return materialItemConstructor;
        }

        synchronized (CopycatMaterialGuard.class) {
            if (initialised) {
                return materialItemConstructor;
            }

            try {
                Class<?> materialItem = Class.forName(
                    "com.copycatsplus.copycats.foundation.copycat.multistate.MaterialItemStorage$MaterialItem",
                    false,
                    CopycatMaterialGuard.class.getClassLoader()
                );
                Constructor<?> constructor =
                    materialItem.getConstructor(BlockState.class, ItemStack.class);
                constructor.setAccessible(true);
                materialItemConstructor = constructor;
            } catch (ReflectiveOperationException | RuntimeException error) {
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] Copycats+ MaterialItem(BlockState, ItemStack) not found, the material guard stays off."
                );
                materialItemConstructor = null;
            }

            initialised = true;
            return materialItemConstructor;
        }
    }
}
