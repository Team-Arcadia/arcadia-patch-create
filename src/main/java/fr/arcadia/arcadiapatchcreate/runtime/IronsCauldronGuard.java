package fr.arcadia.arcadiapatchcreate.runtime;

import fr.arcadia.arcadiapatchcreate.ArcadiaPatchCreate;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * Keeps the Iron's Spells alchemist cauldron alive when it recycles a scroll with no spell.
 *
 * <p>{@code AlchemistCauldronTile.getInkFromScroll(ItemStack)} reads the scroll's spell
 * container and dereferences it without a null check. A scroll item that carries no
 * {@code irons_spellbooks:spell_container} component therefore throws a
 * NullPointerException from the block entity tick, which takes the whole server down. The
 * cauldron is usually fed by hoppers, so the same scroll comes back after every restart
 * until it is consumed. On Arcadia Server1 this looped six times on 2026-09-22 between
 * 23:03 and 23:24, each crash followed by a five minute shutdown hang.
 *
 * <p>A blank scroll is recycled into common ink instead, the lowest rarity: it gives the
 * player nothing a real scroll would not, and the cauldron keeps working.
 */
public final class IronsCauldronGuard {

    private static final ResourceLocation SPELL_CONTAINER_ID =
        ResourceLocation.fromNamespaceAndPath("irons_spellbooks", "spell_container");

    private static final AtomicLong BLANK_SCROLLS = new AtomicLong();

    private static volatile boolean initialised;
    private static volatile Object commonInk;

    private IronsCauldronGuard() {
    }

    /** @return true when the stack has no spell container component at all. */
    public static boolean isBlankScroll(ItemStack stack) {
        DataComponentType<?> type = BuiltInRegistries.DATA_COMPONENT_TYPE.get(SPELL_CONTAINER_ID);
        return type != null && stack.get(type) == null;
    }

    /**
     * @return the common ink item, or null when the Iron's Spells layout is not the expected
     *         one, in which case the original method runs unchanged.
     */
    public static Object commonInk() {
        Object ink = resolveCommonInk();
        if (ink != null) {
            long count = BLANK_SCROLLS.incrementAndGet();
            ArcadiaPatchCreate.LOGGER.warn(
                "[ArcadiaPatchCreate] Alchemist cauldron recycled a scroll with no spell into common ink ({} so far).",
                count
            );
        }
        return ink;
    }

    public static long blankScrollCount() {
        return BLANK_SCROLLS.get();
    }

    private static Object resolveCommonInk() {
        if (initialised) {
            return commonInk;
        }

        synchronized (IronsCauldronGuard.class) {
            if (initialised) {
                return commonInk;
            }

            try {
                ClassLoader loader = IronsCauldronGuard.class.getClassLoader();
                Class<?> rarity = Class.forName("io.redspace.ironsspellbooks.api.spells.SpellRarity", false, loader);
                Class<?> inkItem = Class.forName("io.redspace.ironsspellbooks.item.InkItem", false, loader);
                Object common = rarity.getField("COMMON").get(null);
                Method getInkForRarity = inkItem.getMethod("getInkForRarity", rarity);
                commonInk = getInkForRarity.invoke(null, common);
            } catch (ReflectiveOperationException | RuntimeException error) {
                ArcadiaPatchCreate.LOGGER.warn(
                    "[ArcadiaPatchCreate] Iron's Spells ink lookup not found, the cauldron guard stays off."
                );
                commonInk = null;
            }

            initialised = true;
            return commonInk;
        }
    }
}
