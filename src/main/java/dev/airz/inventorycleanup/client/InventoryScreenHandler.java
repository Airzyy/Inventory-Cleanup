package dev.airz.inventorycleanup.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.minecraft.client.gui.components.events.GuiEventListener;

@EventBusSubscriber(modid = "inventorycleanup", value = Dist.CLIENT)
public final class InventoryScreenHandler {
    private InventoryScreenHandler() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onScreenInit(ScreenEvent.Init.Post event) {
        Screen screen = event.getScreen();
        if (screen instanceof InventoryScreen) {
            // Remove the Curios launcher and Apothic Attributes panel/button from this screen only.
            for (GuiEventListener listener : List.copyOf(screen.children())) {
                String name = listener.getClass().getName();
                if (name.equals("top.theillusivec4.curios.client.gui.CuriosButton")
                        || name.equals("dev.shadowsoffire.apothic_attributes.client.AttributesGui")
                        || name.startsWith("dev.shadowsoffire.apothic_attributes.client.AttributesGui$")) {
                    event.removeListener(listener);
                }
            }

            // Reskillable's tab uses GUI-relative coordinates. Put it beside the new inventory tab.
            setReskillableTabPosition(31, -28);
            moveExistingReskillableTab(screen, 31, -28);
            addInventoryTab(event, screen, true);
        } else if (screen.getClass().getName().equals("net.bandit.reskillable.client.screen.SkillScreen")) {
            setReskillableTabPosition(31, -28);
            moveExistingReskillableTab(screen, 31, -28);
            addInventoryTab(event, screen, false);
        }
    }


    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post event) {
        Screen screen = event.getScreen();
        int left;
        int top;
        if (screen instanceof InventoryScreen inventory) {
            left = inventory.getGuiLeft();
            top = inventory.getGuiTop();
        } else if (screen.getClass().getName().equals("net.bandit.reskillable.client.screen.SkillScreen")) {
            left = (screen.width - 176) / 2;
            top = (screen.height - 166) / 2;
        } else {
            return;
        }
        event.getGuiGraphics().renderItem(net.minecraft.world.item.Items.CHEST.getDefaultInstance(), left + 8, top - 22);
    }

    private static void setReskillableTabPosition(int x, int y) {
        try {
            Class<?> tabs = Class.forName("net.bandit.reskillable.client.screen.InventoryTabs");
            tabs.getMethod("setPosition", int.class, int.class).invoke(null, x, y);
        } catch (ReflectiveOperationException ignored) {
            // Reskillable is an optional integration at runtime.
        }
    }

    private static void moveExistingReskillableTab(Screen screen, int x, int y) {
        for (GuiEventListener listener : screen.children()) {
            if (!listener.getClass().getName().equals("net.bandit.reskillable.client.screen.buttons.TabButton")) continue;
            try {
                var relX = listener.getClass().getDeclaredField("relX");
                var relY = listener.getClass().getDeclaredField("relY");
                relX.setAccessible(true);
                relY.setAccessible(true);
                relX.setInt(listener, x);
                relY.setInt(listener, y);
            } catch (ReflectiveOperationException ignored) {
                // The saved position above remains the fallback for future screen openings.
            }
        }
    }

    private static void addInventoryTab(ScreenEvent.Init.Post event, Screen screen, boolean alreadyInventory) {
        int left = screen instanceof InventoryScreen inventory ? inventory.getGuiLeft() : (screen.width - 176) / 2;
        int top = screen instanceof InventoryScreen inventory ? inventory.getGuiTop() : (screen.height - 166) / 2;
        Button tab = Button.builder(Component.empty(), button -> {
            if (!alreadyInventory && Minecraft.getInstance().player != null) {
                Minecraft.getInstance().setScreen(new InventoryScreen(Minecraft.getInstance().player));
            }
        }).bounds(left, top - 28, 31, 28).tooltip(
                net.minecraft.client.gui.components.Tooltip.create(Component.translatable("inventorycleanup.tab.inventory")))
                .build();
        event.addListener(tab);
    }
}
