package dev.airz.inventorycleanup.client;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ContainerScreenEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

@EventBusSubscriber(modid = "inventorycleanup", value = Dist.CLIENT)
public final class InventoryScreenHandler {
    private static final String CURIOS_SCREEN =
            "top.theillusivec4.curios.client.gui.CuriosScreen";
    private static final String SKILL_SCREEN =
            "net.bandit.reskillable.client.screen.SkillScreen";
    private static final String RESKILLABLE_TAB =
            "net.bandit.reskillable.client.screen.buttons.TabButton";

    private static final java.util.Map<Slot, int[]> CURIO_SLOT_POSITIONS = new java.util.IdentityHashMap<>();

    private InventoryScreenHandler() {}


    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof CreativeModeInventoryScreen creativeScreen) {
            removeCuriosSlotsFromCreativeMenu(creativeScreen);
            return;
        }

        Player player = Minecraft.getInstance().player;
        if (screen instanceof InventoryScreen inventoryScreen && player != null) {
            // The custom Inventory tab opens InventoryScreen even in Creative.
            // Keep the menu's slot indices intact, but move Curios hitboxes/rendering off-screen.
            setCuriosSlotsVisible(inventoryScreen.getMenu(), !player.isCreative());
        }
        if (player != null && player.isCreative()) return;
        boolean inventory = screen instanceof InventoryScreen;
        boolean skills = screen.getClass().getName().equals(SKILL_SCREEN);
        boolean curios = screen.getClass().getName().equals(CURIOS_SCREEN);
        if (!inventory && !skills && !curios) return;

        for (GuiEventListener listener : List.copyOf(screen.children())) {
            String name = listener.getClass().getName();
            if ((inventory && listener.getClass() == ImageButton.class)
                    || name.equals(RESKILLABLE_TAB)
                    || name.equals("top.theillusivec4.curios.client.gui.button.CuriosButton")
                    || name.equals("top.theillusivec4.curios.client.gui.CuriosButton")
                    || name.equals("dev.shadowsoffire.apothic_attributes.client.AttributesGui")
                    || name.startsWith("dev.shadowsoffire.apothic_attributes.client.AttributesGui$")) {
                event.removeListener(listener);
            }
        }

        if (screen instanceof InventoryScreen inventoryScreen) {
            RecipeBookComponent recipeBook = inventoryScreen.getRecipeBookComponent();
            if (recipeBook.isVisible()) recipeBook.toggleVisibility();
            event.removeListener(recipeBook);
        } else if (curios) {
            removeCuriosRecipeBook(event, screen);
        }

        installCosmeticSlotVisibility();
        int left = getGuiLeft(screen);
        int top = getGuiTop(screen);
        event.addListener(new InventoryTabButton(screen, left, top - 28, true));
        event.addListener(new InventoryTabButton(screen, left + 31, top - 28, false));
    }

    private static void setCuriosSlotsVisible(AbstractContainerMenu menu, boolean visible) {
        for (Slot slot : menu.slots) {
            if (!(slot instanceof top.theillusivec4.curios.common.inventory.CurioSlot)) continue;
            if (visible) {
                int[] original = CURIO_SLOT_POSITIONS.remove(slot);
                if (original != null) {
                    slot.x = original[0];
                    slot.y = original[1];
                }
            } else {
                CURIO_SLOT_POSITIONS.putIfAbsent(slot, new int[] {slot.x, slot.y});
                slot.x = -10000;
                slot.y = -10000;
            }
        }
    }

    private static Slot findWrappedSlot(Slot wrapper) {
        // SlotWrapper's backing-slot field name differs between mappings/builds.
        // Find the actual Slot field instead of depending on one private name.
        for (Class<?> type = wrapper.getClass(); type != null; type = type.getSuperclass()) {
            for (Field field : type.getDeclaredFields()) {
                if (!Slot.class.isAssignableFrom(field.getType())) continue;
                try {
                    field.setAccessible(true);
                    Object value = field.get(wrapper);
                    if (value instanceof Slot target) return target;
                } catch (ReflectiveOperationException | RuntimeException ignored) {
                    // Try the next field if this mapping exposes an inaccessible member.
                }
            }
        }
        return null;
    }

    private static void removeCuriosSlotsFromCreativeMenu(CreativeModeInventoryScreen screen) {
        try {
            AbstractContainerMenu menu = screen.getMenu();
            List<Slot> slots = menu.slots;
            for (int i = slots.size() - 1; i >= 0; i--) {
                Slot slot = slots.get(i);
                if (!slot.getClass().getName().equals(
                        "net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen$SlotWrapper")) {
                    continue;
                }
                Slot wrappedSlot = findWrappedSlot(slot);
                if (!(wrappedSlot instanceof top.theillusivec4.curios.common.inventory.CurioSlot)) {
                    continue;
                }

                int originalSize = slots.size();
                slots.remove(i);
                Field originalSlotsField = CreativeModeInventoryScreen.class.getDeclaredField("originalSlots");
                originalSlotsField.setAccessible(true);
                Object originalSlotsValue = originalSlotsField.get(screen);
                if (originalSlotsValue instanceof List<?> originalSlots && originalSlots != slots) {
                    try { ((List<?>) originalSlots).remove(slot); } catch (UnsupportedOperationException ignored) {}
                }
                // Creative's wrapper menu is client-only. Keep its parallel slot snapshots aligned.
                for (Class<?> type = menu.getClass(); type != null; type = type.getSuperclass()) {
                    for (Field field : type.getDeclaredFields()) {
                        if (!field.getName().equals("lastSlots") && !field.getName().equals("remoteSlots")) continue;
                        if (!List.class.isAssignableFrom(field.getType())) continue;
                        field.setAccessible(true);
                        Object value = field.get(menu);
                        if (value instanceof List<?> list && list != slots && list.size() == originalSize) {
                            try {
                                ((List<?>) list).remove(i);
                            } catch (UnsupportedOperationException ignored) {
                                // Not a mutable slot snapshot.
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < slots.size(); i++) slots.get(i).index = i;
        } catch (ReflectiveOperationException ignored) {
            // Creative inventory remains usable if vanilla changes its slot wrapper implementation.
        }
    }

    private static void removeCuriosRecipeBook(ScreenEvent.Init.Post event, Screen screen) {
        for (String fieldName : new String[] {"recipeBookGui", "recipeBookButton"}) {
            try {
                Field field = screen.getClass().getDeclaredField(fieldName);
                field.setAccessible(true);
                Object component = field.get(screen);
                if (component instanceof RecipeBookComponent recipeBook && recipeBook.isVisible()) {
                    recipeBook.toggleVisibility();
                }
                if (component instanceof GuiEventListener listener) event.removeListener(listener);
            } catch (ReflectiveOperationException ignored) {
                // Curios may change its internal widget names in a future release.
            }
        }
    }

    private static int getGuiLeft(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) return container.getGuiLeft();
        return (screen.width - 176) / 2;
    }

    private static int getGuiTop(Screen screen) {
        if (screen instanceof AbstractContainerScreen<?> container) return container.getGuiTop();
        return (screen.height - 166) / 2;
    }

    private static void installCosmeticSlotVisibility() {
        try {
            Class<?> cosmeticSlot = Class.forName("com.airzy.cosmeticslots.CosmeticSlot");
            Class<?> client = Class.forName("com.airzy.cosmeticslots.CosmeticSlotsClient");
            Field visibleField = client.getDeclaredField("visibleSlot");
            visibleField.setAccessible(true);

            Predicate<EquipmentSlot> visibility = equipmentSlot -> {
                try {
                    if (visibleField.get(null) != equipmentSlot) return false;
                    Screen current = Minecraft.getInstance().screen;
                    if (current instanceof InventoryScreen inventory) {
                        return !inventory.getRecipeBookComponent().isVisible();
                    }
                    return current != null && current.getClass().getName().equals(CURIOS_SCREEN);
                } catch (IllegalAccessException ignored) {
                    return false;
                }
            };
            cosmeticSlot.getMethod("setClientVisibility", Predicate.class).invoke(null, visibility);
        } catch (ReflectiveOperationException ignored) {
            // Cosmetic Slots is an optional integration.
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onCuriosBackground(ContainerScreenEvent.Render.Background event) {
        AbstractContainerScreen<?> screen = event.getContainerScreen();
        if (!screen.getClass().getName().equals(CURIOS_SCREEN)) return;

        try {
            Class<?> armorClass = Class.forName("com.airzy.cosmeticslots.CosmeticArmor");
            EquipmentSlot[] slots = (EquipmentSlot[]) armorClass.getField("SLOTS").get(null);
            Class<?> client = Class.forName("com.airzy.cosmeticslots.CosmeticSlotsClient");
            Field visibleField = client.getDeclaredField("visibleSlot");
            visibleField.setAccessible(true);

            int left = screen.getGuiLeft();
            int top = screen.getGuiTop();
            int mouseX = event.getMouseX();
            int mouseY = event.getMouseY();
            int slotTop = top + 8;
            int slotBottom = slotTop + slots.length * 18;

            if (mouseX < left + 76 || mouseX >= left + 94
                    || mouseY < slotTop || mouseY >= slotBottom) {
                visibleField.set(null, null);
                return;
            }

            int index = (mouseY - slotTop) / 18;
            if (index < 0 || index >= slots.length) {
                visibleField.set(null, null);
                return;
            }

            visibleField.set(null, slots[index]);
            int y = slotTop + index * 18;
            var graphics = event.getGuiGraphics();
            graphics.fill(left + 75, y - 2, left + 94, y + 19, 0xFF000000);
            graphics.fill(left + 76, y - 1, left + 93, y + 18, 0xFFC6C6C6);
        } catch (ReflectiveOperationException ignored) {
            // Cosmetic Slots is optional.
        }
    }
}
