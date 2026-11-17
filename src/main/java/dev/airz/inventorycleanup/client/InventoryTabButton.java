package dev.airz.inventorycleanup.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.airz.inventorycleanup.client.BackpackCompat.BackpackRef;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.network.PacketDistributor;
import top.theillusivec4.curios.common.network.client.CPacketOpenVanilla;

/**
 * One tab in the row above the inventory-style screens: Inventory, Skills, then one tab per
 * Sophisticated Backpacks backpack the player is carrying.
 */
public final class InventoryTabButton extends Button {
    public enum Kind { INVENTORY, SKILLS, BACKPACK }

    /** Tab artwork size once rotated into place. */
    public static final int TAB_WIDTH = 28;
    public static final int TAB_HEIGHT = 31;
    /** The tab row sits this far above the top edge of the screen's frame. */
    public static final int ROW_OFFSET_Y = -28;
    /** Inventory tab starts 1px right of the frame edge; each tab after it is 1px closer than before. */
    private static final int FIRST_TAB_OFFSET_X = 1;
    private static final int TAB_STEP = 29;

    private static final ResourceLocation SKILL_TEXTURE =
            ResourceLocation.fromNamespaceAndPath("reskillable", "textures/gui/skills.png");
    private static final String CURIOS_SCREEN =
            "top.theillusivec4.curios.client.gui.CuriosScreen";
    private static final String SKILL_SCREEN =
            "net.bandit.reskillable.client.screen.SkillScreen";

    private final Screen owner;
    private final Kind kind;
    private final BackpackRef backpack;
    private final int index;
    private final boolean backpackOpen;
    private String shownTooltip = "";

    public InventoryTabButton(Screen owner, Kind kind, BackpackRef backpack, int index) {
        super(slotX(owner, index), slotY(owner), TAB_WIDTH, TAB_HEIGHT, Component.empty(),
                button -> activate(owner, kind, backpack), DEFAULT_NARRATION);
        this.owner = owner;
        this.kind = kind;
        this.backpack = backpack;
        this.index = index;
        this.backpackOpen = kind == Kind.BACKPACK
                && owner instanceof AbstractContainerScreen<?> container
                && BackpackCompat.isOpen(container.getMenu(), backpack);
        if (kind == Kind.INVENTORY) {
            setTooltip(Tooltip.create(Component.literal("Inventory")));
        } else if (kind == Kind.SKILLS) {
            setTooltip(Tooltip.create(Component.literal("Skills")));
        }
    }

    /** X of the tab with this index (0 = Inventory, 1 = Skills, 2+ = backpacks). */
    public static int slotX(Screen owner, int index) {
        return InventoryScreenHandler.getGuiLeft(owner) + FIRST_TAB_OFFSET_X + TAB_STEP * index;
    }

    public static int slotY(Screen owner) {
        return InventoryScreenHandler.getGuiTop(owner) + ROW_OFFSET_Y;
    }

    /** Whether another tab with this index still fits inside the window. */
    public static boolean fitsOnScreen(Screen owner, int index) {
        return slotX(owner, index) + TAB_WIDTH <= owner.width;
    }

    private static boolean isSkillScreen(Screen screen) {
        return screen.getClass().getName().equals(SKILL_SCREEN);
    }

    private static boolean isCuriosScreen(Screen screen) {
        return screen.getClass().getName().equals(CURIOS_SCREEN);
    }

    private static void activate(Screen owner, Kind kind, BackpackRef backpack) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null) return;

        boolean inCurios = isCuriosScreen(owner);
        boolean inSkills = isSkillScreen(owner);
        boolean inInventory = owner instanceof InventoryScreen;
        boolean inBackpack = BackpackCompat.isBackpackScreen(owner);

        switch (kind) {
            case INVENTORY -> {
                if (inInventory) return;
                if (inBackpack) {
                    // Same hand-off Sophisticated Backpacks uses itself when closing back to the inventory.
                    player.closeContainer();
                } else if (inCurios || inSkills) {
                    switchBackToVanillaMenu(player);
                }
                minecraft.setScreen(new InventoryScreen(player));
            }
            case SKILLS -> {
                if (inSkills) return;
                if (inBackpack) {
                    player.closeContainer();
                } else if (inCurios) {
                    switchBackToVanillaMenu(player);
                }
                try {
                    Screen skills = (Screen) Class.forName(SKILL_SCREEN).getConstructor().newInstance();
                    minecraft.setScreen(skills);
                } catch (ReflectiveOperationException ignored) {
                    // Reskillable is optional.
                }
            }
            case BACKPACK -> {
                if (backpack == null) return;
                // Already looking at this backpack.
                if (owner instanceof AbstractContainerScreen<?> container
                        && BackpackCompat.isOpen(container.getMenu(), backpack)) {
                    return;
                }
                // Moved or removed since the screen opened.
                if (BackpackCompat.getStack(player, backpack).isEmpty()) return;
                // The server opens the backpack from the plain inventory menu, so leave Curios' menu first.
                if (inCurios) switchBackToVanillaMenu(player);
                BackpackCompat.open(backpack);
            }
        }
    }

    private static void switchBackToVanillaMenu(Player player) {
        ItemStack carried = player.containerMenu.getCarried().copy();
        if (player.containerMenu != player.inventoryMenu) {
            player.containerMenu.setCarried(ItemStack.EMPTY);
            player.inventoryMenu.setCarried(carried);
            PacketDistributor.sendToServer(new CPacketOpenVanilla(carried));
        }
    }

    /** The backpack this tab points at right now, or empty if it moved away. */
    private ItemStack backpackStack() {
        Player player = Minecraft.getInstance().player;
        return player == null ? ItemStack.EMPTY : BackpackCompat.getStack(player, backpack);
    }

    /** False for a backpack tab whose backpack is no longer in its slot. */
    private boolean isShown() {
        return kind != Kind.BACKPACK || !backpackStack().isEmpty();
    }

    private boolean isSelected() {
        return switch (kind) {
            // Curios' screen is still "the inventory"; only Skills and backpack screens are different places.
            case INVENTORY -> owner instanceof InventoryScreen
                    || (!isSkillScreen(owner) && !BackpackCompat.isBackpackScreen(owner));
            case SKILLS -> isSkillScreen(owner);
            case BACKPACK -> backpackOpen;
        };
    }

    /** Re-reads the screen's position so the tab follows it if the layout changes. */
    private void syncPosition() {
        setX(slotX(owner, index));
        setY(slotY(owner));
    }

    /**
     * Draws the artwork of every tab on the screen. The vanilla inventory calls this before its
     * frame is drawn so the frame overlaps the tabs; other screens draw their tabs themselves.
     */
    public static void renderArtworkBehindFrame(Screen screen, GuiGraphics graphics) {
        for (GuiEventListener child : screen.children()) {
            if (child instanceof InventoryTabButton tab && tab.isShown()) {
                tab.syncPosition();
                renderArtwork(graphics, tab.getX(), tab.getY(), tab.isSelected());
            }
        }
    }

    public static void renderArtwork(GuiGraphics graphics, int x, int y, boolean selected) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        // Draw the tab before the inventory texture so the inventory frame is layered over it.
        pose.translate(x, y, 0);
        pose.scale(-1.0F, 1.0F, 1.0F);
        pose.mulPose(Axis.ZP.rotationDegrees(90.0F));
        RenderSystem.disableCull();
        graphics.blit(SKILL_TEXTURE, 0, 0, selected ? 31 : 0, 166, 31, 28);
        RenderSystem.enableCull();
        pose.popPose();
    }

    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        syncPosition();

        ItemStack backpackStack = ItemStack.EMPTY;
        if (kind == Kind.BACKPACK) {
            backpackStack = backpackStack();
            if (backpackStack.isEmpty()) {
                setTooltip(null);
                shownTooltip = "";
                return;
            }
            // Follow renames / swaps without rebuilding the tooltip every frame.
            String name = backpackStack.getHoverName().getString();
            if (!name.equals(shownTooltip)) {
                setTooltip(Tooltip.create(backpackStack.getHoverName()));
                shownTooltip = name;
            }
        }

        if (!(owner instanceof InventoryScreen)) {
            renderArtwork(graphics, getX(), getY(), isSelected());
        }

        switch (kind) {
            case INVENTORY -> graphics.renderItem(Items.CHEST.getDefaultInstance(), getX() + 6, getY() + 7);
            case SKILLS -> graphics.blit(SKILL_TEXTURE, getX() + 6, getY() + 7, 240, 144, 16, 16);
            case BACKPACK -> graphics.renderItem(backpackStack, getX() + 6, getY() + 7);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // A stale backpack tab (backpack moved away) draws nothing, so it must not take clicks either.
        return isShown() && super.mouseClicked(mouseX, mouseY, button);
    }
}
