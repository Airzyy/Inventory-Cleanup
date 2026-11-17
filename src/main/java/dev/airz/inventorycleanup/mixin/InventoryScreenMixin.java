package dev.airz.inventorycleanup.mixin;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;\nimport net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import top.theillusivec4.curios.common.inventory.CurioSlot;

@Mixin(InventoryScreen.class)
public abstract class InventoryScreenMixin {
    @Inject(method = "init", at = @At("TAIL"), remap = false)
    private void inventorycleanup$makeRoomForCurios(CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        int count = 0;
        for (Slot slot : screen.getMenu().slots) {
            if (slot instanceof CurioSlot) count++;
        }

        int columns = count == 0 ? 0 : Math.max(1, (int) Math.ceil(count / 8.0));
        int panelWidth = columns == 0 ? 0 : 14 + 18 * columns;
        int centeredLeft = (screen.width - (176 + panelWidth)) / 2 + panelWidth;
        ((AbstractContainerScreenAccessor) (Object) this).inventorycleanup$setLeftPos(centeredLeft);

        var recipeBook = screen.getRecipeBookComponent();
        if (recipeBook.isVisible()) recipeBook.toggleVisibility();
    }


    @Inject(method = "hasClickedOutside", at = @At("HEAD"), cancellable = true, remap = false)
    private void inventorycleanup$treatCuriosPanelAsInside(double mouseX, double mouseY,
                                                            int guiLeft, int guiTop, int mouseButton,
                                                            CallbackInfoReturnable<Boolean> cir) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        boolean found = false;

        for (Slot slot : screen.getMenu().slots) {
            if (!(slot instanceof CurioSlot)) continue;
            found = true;
            minX = Math.min(minX, slot.x);
            maxX = Math.max(maxX, slot.x);
            minY = Math.min(minY, slot.y);
            maxY = Math.max(maxY, slot.y);
        }
        if (!found) return;

        int panelLeft = guiLeft + minX - 4;
        int panelTop = guiTop + minY - 4;
        int panelRight = guiLeft;
        int panelBottom = guiTop + maxY + 20;
        if (mouseX >= panelLeft && mouseX < panelRight
                && mouseY >= panelTop && mouseY < panelBottom) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "renderBg", at = @At("TAIL"), remap = false)
    private void inventorycleanup$renderCuriosPanel(GuiGraphics graphics, float partialTick,
                                                     int mouseX, int mouseY, CallbackInfo ci) {
        InventoryScreen screen = (InventoryScreen) (Object) this;
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int count = 0;

        for (Slot slot : screen.getMenu().slots) {
            if (!(slot instanceof CurioSlot)) continue;
            minX = Math.min(minX, slot.x);
            maxX = Math.max(maxX, slot.x);
            minY = Math.min(minY, slot.y);
            maxY = Math.max(maxY, slot.y);
            count++;
        }
        if (count == 0) return;

        int left = screen.getGuiLeft() + minX - 4;
        int top = screen.getGuiTop() + minY - 4;
        int right = screen.getGuiLeft() + maxX + 20;
        int bottom = screen.getGuiTop() + maxY + 20;

        graphics.fill(left, top, right, bottom, 0xFF171717);
        graphics.fill(left + 1, top + 1, right - 1, bottom - 1, 0xFF777777);
        graphics.fill(left + 2, top + 2, right - 2, bottom - 2, 0xFF303030);

        for (Slot slot : screen.getMenu().slots) {
            if (!(slot instanceof CurioSlot)) continue;
            int x = screen.getGuiLeft() + slot.x;
            int y = screen.getGuiTop() + slot.y;
            graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF171717);
            graphics.fill(x, y, x + 16, y + 16, 0xFF8B8B8B);
            graphics.fill(x + 1, y + 1, x + 15, y + 15, 0xFF373737);
        }
    }
}
