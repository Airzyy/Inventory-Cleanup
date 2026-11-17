package dev.airz.inventorycleanup.client;

import dev.airz.inventorycleanup.CuriosMenuSlots;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Predicate;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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

    private InventoryScreenHandler() {}


    @SubscribeEvent
    public static void onClientLogin(ClientPlayerNetworkEvent.LoggingIn event) {
        var player = event.getPlayer();
        CuriosMenuSlots.addToInventoryMenu(player, player.inventoryMenu);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        boolean inventory = screen instanceof InventoryScreen;
        boolean skills = screen.getClass().getName().equals(SKILL_SCREEN);
        boolean curios = screen.getClass().getName().equals(CURIOS_SCREEN);
        if (!inventory && !skills && !curios) return;

        for (GuiEventListener listener : List.copyOf(screen.children())) {
            String name = listener.getClass().getName();
            if ((inventory && listener instanceof ImageButton)
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
