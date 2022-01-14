package li.cil.oc2.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import li.cil.oc2.api.bus.device.DeviceTypes;
import li.cil.oc2.client.gui.util.GuiUtils;
import li.cil.oc2.client.gui.widget.ImageButton;
import li.cil.oc2.client.gui.widget.ToggleImageButton;
import li.cil.oc2.common.Constants;
import li.cil.oc2.common.container.AbstractMachineTerminalContainer;
import li.cil.oc2.common.util.TooltipUtils;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.player.Inventory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.List;

import static java.util.Arrays.asList;
import static li.cil.oc2.common.util.TextFormatUtils.withFormat;

@OnlyIn(Dist.CLIENT)
public abstract class AbstractMachineInventoryScreen<T extends AbstractMachineTerminalContainer> extends AbstractModContainerScreen<T> {
    private static final int CONTROLS_TOP = 8;
    private static final int ENERGY_TOP = CONTROLS_TOP + Sprites.SIDEBAR_2.height + 4;

    ///////////////////////////////////////////////////////////////////

    public AbstractMachineInventoryScreen(final T container, final Inventory playerInventory, final Component title) {
        super(container, playerInventory, title);
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void init() {
        super.init();

        addRenderableWidget(new ToggleImageButton(
            leftPos - Sprites.SIDEBAR_3.width + 4, topPos + CONTROLS_TOP + 4,
            12, 12,
            Sprites.POWER_BUTTON_BASE,
            Sprites.POWER_BUTTON_PRESSED,
            Sprites.POWER_BUTTON_ACTIVE
        ) {
            @Override
            public void onPress() {
                super.onPress();
                menu.sendPowerStateToServer(!menu.getVirtualMachine().isRunning());
            }

            @Override
            public boolean isToggled() {
                return menu.getVirtualMachine().isRunning();
            }
        }).withTooltip(
            new TranslatableComponent(Constants.COMPUTER_SCREEN_POWER_CAPTION),
            new TranslatableComponent(Constants.COMPUTER_SCREEN_POWER_DESCRIPTION)
        );

        addRenderableWidget(new ImageButton(
            leftPos - Sprites.SIDEBAR_3.width + 4, topPos + CONTROLS_TOP + 4 + 14,
            12, 12,
            Sprites.INVENTORY_BUTTON_ACTIVE,
            Sprites.INVENTORY_BUTTON_INACTIVE
        ) {
            @Override
            public void onPress() {
                menu.switchToTerminal();
            }
        }.withTooltip(new TranslatableComponent(Constants.MACHINE_OPEN_TERMINAL_CAPTION)));
    }

    @Override
    public void render(final PoseStack stack, final int mouseX, final int mouseY, final float partialTicks) {
        renderBackground(stack);
        super.render(stack, mouseX, mouseY, partialTicks);

        final int energyCapacity = menu.getEnergyCapacity();
        if (energyCapacity > 0) {
            final int energyStored = menu.getEnergy();
            final int energyConsumption = menu.getEnergyConsumption();

            Sprites.ENERGY_BAR.drawFillY(stack, leftPos - Sprites.SIDEBAR_2.width + 4, topPos + ENERGY_TOP + 4, energyStored / (float) energyCapacity);

            if (isMouseOver(mouseX, mouseY, -Sprites.SIDEBAR_2.width + 4, ENERGY_TOP + 4, Sprites.ENERGY_BAR.width, Sprites.ENERGY_BAR.height)) {
                final List<? extends FormattedText> tooltip = asList(
                    new TranslatableComponent(Constants.TOOLTIP_ENERGY, withFormat(energyStored + "/" + energyCapacity, ChatFormatting.GREEN)),
                    new TranslatableComponent(Constants.TOOLTIP_ENERGY_CONSUMPTION, withFormat(String.valueOf(energyConsumption), ChatFormatting.GREEN))
                );
                TooltipUtils.drawTooltip(stack, tooltip, mouseX, mouseY, 200);
            }
        }
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void renderBg(final PoseStack stack, final float partialTicks, final int mouseX, final int mouseY) {
        Sprites.SIDEBAR_2.draw(stack, leftPos - Sprites.SIDEBAR_2.width, topPos + CONTROLS_TOP);

        if (menu.getEnergyCapacity() > 0) {
            final int x = leftPos - Sprites.SIDEBAR_2.width;
            final int y = topPos + ENERGY_TOP;
            Sprites.SIDEBAR_2.draw(stack, x, y);
            Sprites.ENERGY_BASE.draw(stack, x + 4, y + 4);
        }
    }

    protected void renderMissingDeviceInfo(final PoseStack stack, final int mouseX, final int mouseY) {
        GuiUtils.renderMissingDeviceInfoIcon(stack, this, DeviceTypes.FLASH_MEMORY, Sprites.WARN_ICON);
        GuiUtils.renderMissingDeviceInfoIcon(stack, this, DeviceTypes.MEMORY, Sprites.WARN_ICON);
        GuiUtils.renderMissingDeviceInfoIcon(stack, this, DeviceTypes.HARD_DRIVE, Sprites.INFO_ICON);

        GuiUtils.renderMissingDeviceInfoTooltip(stack, this, mouseX, mouseY, DeviceTypes.FLASH_MEMORY);
        GuiUtils.renderMissingDeviceInfoTooltip(stack, this, mouseX, mouseY, DeviceTypes.MEMORY);
        GuiUtils.renderMissingDeviceInfoTooltip(stack, this, mouseX, mouseY, DeviceTypes.HARD_DRIVE);
    }
}
