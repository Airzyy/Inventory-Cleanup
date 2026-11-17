package dev.airz.inventorycleanup.client;


import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import top.theillusivec4.curios.common.inventory.CurioSlot;
import top.theillusivec4.curios.common.network.client.CPacketToggleRender;
import net.neoforged.neoforge.network.PacketDistributor;
import java.util.ArrayList;
import java.util.TreeSet;
import dev.airz.inventorycleanup.mixin.client.SlotPositionAccessor;
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
        if (inventory) {
            addCuriosRenderToggle(event, (InventoryScreen) screen, left, top);
        }
    }

    private static void setCuriosSlotsVisible(AbstractContainerMenu menu, boolean visible) {
        for (Slot slot : menu.slots) {
            if (!(slot instanceof top.theillusivec4.curios.common.inventory.CurioSlot)) continue;
            if (visible) {
                int[] original = CURIO_SLOT_POSITIONS.remove(slot);
                if (original != null) {
                    ((SlotPositionAccessor) (Object) slot).inventorycleanup$setX(original[0]);
                    ((SlotPositionAccessor) (Object) slot).inventorycleanup$setY(original[1]);
                }
            } else {
                CURIO_SLOT_POSITIONS.putIfAbsent(slot, new int[] {slot.x, slot.y});
                ((SlotPositionAccessor) (Object) slot).inventorycleanup$setX(-10000);
                ((SlotPositionAccessor) (Object) slot).inventorycleanup$setY(-10000);
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

    private static void addCuriosRenderToggle(ScreenEvent.Init.Post event,
                                               InventoryScreen screen, int left, int top) {
        List<CurioSlot> allCurioSlots = screen.getMenu().slots.stream()
                .filter(slot -> slot instanceof CurioSlot)
                .map(slot -> (CurioSlot) slot)
                .toList();
        List<CurioSlot> toggleableSlots = allCurioSlots.stream()
                .filter(CurioSlot::canToggleRender)
                .toList();
        if (toggleableSlots.isEmpty()) return;

        // Place the control in the next cell after the final Curios slot.
        TreeSet<Integer> columns = new TreeSet<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        for (CurioSlot slot : allCurioSlots) {
            columns.add(slot.x);
            minX = Math.min(minX, slot.x);
            minY = Math.min(minY, slot.y);
        }
        int columnCount = Math.max(1, columns.size());
        int nextIndex = allCurioSlots.size();
        int checkboxX = left + minX + (nextIndex % columnCount) * 18 + 2;
        int checkboxY = top + minY + (nextIndex / columnCount) * 18 + 2;
        event.addListener(new CuriosRenderCheckbox(checkboxX, checkboxY, toggleableSlots));
    }

    /** A compact, unlabeled master switch for Curios item rendering. */
    private static final class CuriosRenderCheckbox extends AbstractButton {
        private final List<CurioSlot> slots;

        private CuriosRenderCheckbox(int x, int y, List<CurioSlot> slots) {
            super(x, y, 12, 12, Component.empty());
            this.slots = new ArrayList<>(slots);
        }

        @Override
        public void onPress() {
            boolean allRendered = !slots.isEmpty()
                    && slots.stream().allMatch(CurioSlot::getRenderStatus);
            boolean render = !allRendered;
            for (CurioSlot slot : slots) {
                if (slot.getRenderStatus() != render) {
                    PacketDistributor.sendToServer(
                            new CPacketToggleRender(slot.getIdentifier(), slot.getSlotIndex()));
                }
            }
        }

        @Override
        protected void updateWidgetNarration(NarrationElementOutput narration) {
            // This control is intentionally unlabeled; narration is omitted to preserve that design.
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            int x = getX();
            int y = getY();
            int border = isHoveredOrFocused() ? 0xFFFFFFFF : 0xFF171717;
            graphics.fill(x, y, x + 12, y + 12, border);
            graphics.fill(x + 1, y + 1, x + 11, y + 11, 0xFF777777);
            graphics.fill(x + 2, y + 2, x + 10, y + 10, 0xFF303030);

            boolean checked = !slots.isEmpty()
                    && slots.stream().allMatch(CurioSlot::getRenderStatus);
            if (checked) {
                // Pixel checkmark, matching the inventory's compact slot styling.
                graphics.fill(x + 2, y + 6, x + 4, y + 8, 0xFFFFFFFF);
                graphics.fill(x + 4, y + 8, x + 6, y + 10, 0xFFFFFFFF);
                graphics.fill(x + 6, y + 6, x + 8, y + 8, 0xFFFFFFFF);
                graphics.fill(x + 8, y + 4, x + 10, y + 6, 0xFFFFFFFF);
            }
        }
    }
}
