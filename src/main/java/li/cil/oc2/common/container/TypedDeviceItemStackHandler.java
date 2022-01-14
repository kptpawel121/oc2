package li.cil.oc2.common.container;

import li.cil.oc2.api.bus.device.DeviceType;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import net.minecraft.world.item.ItemStack;

import java.util.function.Function;

public class TypedDeviceItemStackHandler extends DeviceItemStackHandler {
    private final DeviceType deviceType;

    ///////////////////////////////////////////////////////////////////

    public TypedDeviceItemStackHandler(final int size, final Function<ItemStack, ItemDeviceQuery> queryFactory, final DeviceType deviceType) {
        super(size, queryFactory);
        this.deviceType = deviceType;
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    public boolean isItemValid(final int slot, final ItemStack stack) {
        return super.isItemValid(slot, stack) && stack.is(deviceType.getTag());
    }
}
