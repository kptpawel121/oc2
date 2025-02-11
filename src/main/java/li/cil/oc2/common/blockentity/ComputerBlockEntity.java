package li.cil.oc2.common.blockentity;

import li.cil.oc2.api.bus.DeviceBusElement;
import li.cil.oc2.api.bus.device.Device;
import li.cil.oc2.api.bus.device.DeviceTypes;
import li.cil.oc2.api.bus.device.provider.ItemDeviceQuery;
import li.cil.oc2.api.capabilities.TerminalUserProvider;
import li.cil.oc2.client.audio.LoopingSoundManager;
import li.cil.oc2.common.Config;
import li.cil.oc2.common.block.ComputerBlock;
import li.cil.oc2.common.bus.BlockEntityDeviceBusController;
import li.cil.oc2.common.bus.BlockEntityDeviceBusElement;
import li.cil.oc2.common.bus.CommonDeviceBusController;
import li.cil.oc2.common.bus.device.util.Devices;
import li.cil.oc2.common.capabilities.Capabilities;
import li.cil.oc2.common.container.ComputerInventoryContainer;
import li.cil.oc2.common.container.ComputerTerminalContainer;
import li.cil.oc2.common.container.DeviceItemStackHandler;
import li.cil.oc2.common.energy.FixedEnergyStorage;
import li.cil.oc2.common.network.Network;
import li.cil.oc2.common.network.message.ComputerBootErrorMessage;
import li.cil.oc2.common.network.message.ComputerBusStateMessage;
import li.cil.oc2.common.network.message.ComputerRunStateMessage;
import li.cil.oc2.common.network.message.ComputerTerminalOutputMessage;
import li.cil.oc2.common.serialization.NBTSerialization;
import li.cil.oc2.common.util.*;
import li.cil.oc2.common.vm.*;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;

import static li.cil.oc2.common.Constants.BLOCK_ENTITY_TAG_NAME_IN_ITEM;
import static li.cil.oc2.common.Constants.ITEMS_TAG_NAME;

public final class ComputerBlockEntity extends ModBlockEntity implements TerminalUserProvider {
    private static final String BUS_ELEMENT_TAG_NAME = "busElement";
    private static final String DEVICES_TAG_NAME = "devices";
    private static final String TERMINAL_TAG_NAME = "terminal";
    private static final String STATE_TAG_NAME = "state";
    private static final String ENERGY_TAG_NAME = "energy";

    private static final int MEMORY_SLOTS = 4;
    private static final int HARD_DRIVE_SLOTS = 4;
    private static final int FLASH_MEMORY_SLOTS = 1;
    private static final int CARD_SLOTS = 4;

    private static final int MAX_RUNNING_SOUND_DELAY = TickUtils.toTicks(Duration.ofSeconds(2));

    ///////////////////////////////////////////////////////////////////

    private boolean hasAddedOwnDevices;
    private boolean isNeighborUpdateScheduled;

    ///////////////////////////////////////////////////////////////////

    private final Terminal terminal = new Terminal();
    private final ComputerBusElement busElement = new ComputerBusElement();
    private final ComputerItemStackHandlers deviceItems = new ComputerItemStackHandlers();
    private final FixedEnergyStorage energy = new FixedEnergyStorage(Config.computerEnergyStorage);
    private final ComputerVirtualMachine virtualMachine = new ComputerVirtualMachine(new BlockEntityDeviceBusController(busElement, Config.computerEnergyPerTick, this), deviceItems::getDeviceAddressBase);
    private final Set<Player> terminalUsers = Collections.newSetFromMap(new WeakHashMap<>());

    ///////////////////////////////////////////////////////////////////

    public ComputerBlockEntity(final BlockPos pos, final BlockState state) {
        super(BlockEntities.COMPUTER.get(), pos, state);

        // We want to unload devices even on level unload to free global resources.
        setNeedsLevelUnloadEvent();
    }

    public Terminal getTerminal() {
        return terminal;
    }

    public VirtualMachine getVirtualMachine() {
        return virtualMachine;
    }

    public VMItemStackHandlers getItemStackHandlers() {
        return deviceItems;
    }

    public void start() {
        if (level == null || level.isClientSide()) {
            return;
        }

        virtualMachine.start();
    }

    public void stop() {
        if (level == null || level.isClientSide()) {
            return;
        }

        virtualMachine.stop();
    }

    public void openTerminalScreen(final ServerPlayer player) {
        ComputerTerminalContainer.createServer(this, energy, virtualMachine.busController, player);
    }

    public void openInventoryScreen(final ServerPlayer player) {
        ComputerInventoryContainer.createServer(this, energy, virtualMachine.busController, player);
    }

    public void addTerminalUser(final Player player) {
        terminalUsers.add(player);
    }

    public void removeTerminalUser(final Player player) {
        terminalUsers.remove(player);
    }

    @Override
    public Iterable<Player> getTerminalUsers() {
        return terminalUsers;
    }

    public void handleNeighborChanged() {
        virtualMachine.busController.scheduleBusScan();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(final Capability<T> capability, @Nullable final Direction side) {
        if (isRemoved()) {
            return LazyOptional.empty();
        }

        final LazyOptional<T> optional = super.getCapability(capability, side);
        if (optional.isPresent()) {
            return optional;
        }

        final Direction localSide = HorizontalBlockUtils.toLocal(getBlockState(), side);
        for (final Device device : virtualMachine.busController.getDevices()) {
            if (device instanceof final ICapabilityProvider capabilityProvider) {
                final LazyOptional<T> value = capabilityProvider.getCapability(capability, localSide);
                if (value.isPresent()) {
                    return value;
                }
            }
        }

        return LazyOptional.empty();
    }

    public static void tick(final Level level, final BlockPos ignoredPos, final BlockState ignoredState, final ComputerBlockEntity computer) {
        if (level.isClientSide()) {
            computer.clientTick();
        } else {
            computer.serverTick();
        }
    }

    private void clientTick() {
        terminal.clientTick();
    }

    private void serverTick() {
        if (level == null) {
            return;
        }

        // Always add devices provided for the computer itself, even if there's no
        // adjacent cable. Because that would just be weird.
        if (!hasAddedOwnDevices) {
            hasAddedOwnDevices = true;
            busElement.addOwnDevices();
        }

        if (isNeighborUpdateScheduled) {
            isNeighborUpdateScheduled = false;
            level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
        }

        virtualMachine.tick();
    }

    @Override
    public CompoundTag getUpdateTag() {
        final CompoundTag tag = super.getUpdateTag();

        tag.put(TERMINAL_TAG_NAME, NBTSerialization.serialize(terminal));
        tag.putInt(AbstractVirtualMachine.BUS_STATE_TAG_NAME, virtualMachine.getBusState().ordinal());
        tag.putInt(AbstractVirtualMachine.RUN_STATE_TAG_NAME, virtualMachine.getRunState().ordinal());
        tag.putString(AbstractVirtualMachine.BOOT_ERROR_TAG_NAME, Component.Serializer.toJson(virtualMachine.getBootError()));

        return tag;
    }

    @Override
    public void handleUpdateTag(final CompoundTag tag) {
        super.handleUpdateTag(tag);

        NBTSerialization.deserialize(tag.getCompound(TERMINAL_TAG_NAME), terminal);
        virtualMachine.setBusStateClient(CommonDeviceBusController.BusState.values()[tag.getInt(AbstractVirtualMachine.BUS_STATE_TAG_NAME)]);
        virtualMachine.setRunStateClient(VMRunState.values()[tag.getInt(AbstractVirtualMachine.RUN_STATE_TAG_NAME)]);
        virtualMachine.setBootErrorClient(Component.Serializer.fromJson(tag.getString(AbstractVirtualMachine.BOOT_ERROR_TAG_NAME)));
    }

    @Override
    protected void saveAdditional(final CompoundTag tag) {
        super.saveAdditional(tag);

        tag.put(ENERGY_TAG_NAME, energy.serializeNBT());
        tag.put(STATE_TAG_NAME, virtualMachine.serialize());
        tag.put(TERMINAL_TAG_NAME, NBTSerialization.serialize(terminal));
        tag.put(BUS_ELEMENT_TAG_NAME, busElement.save());
        tag.put(ITEMS_TAG_NAME, deviceItems.saveItems());
        tag.put(DEVICES_TAG_NAME, deviceItems.saveDevices());
    }

    @Override
    public void load(final CompoundTag tag) {
        super.load(tag);

        energy.deserializeNBT(tag.getCompound(ENERGY_TAG_NAME));
        virtualMachine.deserialize(tag.getCompound(STATE_TAG_NAME));
        NBTSerialization.deserialize(tag.getCompound(TERMINAL_TAG_NAME), terminal);
        busElement.load(tag.getCompound(BUS_ELEMENT_TAG_NAME));

        deviceItems.loadItems(tag.getCompound(ITEMS_TAG_NAME));
        deviceItems.loadDevices(tag.getCompound(DEVICES_TAG_NAME));
    }

    public void exportToItemStack(final ItemStack stack) {
        deviceItems.saveItems(NBTUtils.getOrCreateChildTag(stack.getOrCreateTag(), BLOCK_ENTITY_TAG_NAME_IN_ITEM, ITEMS_TAG_NAME));
    }

    ///////////////////////////////////////////////////////////////////

    @Override
    protected void collectCapabilities(final CapabilityCollector collector, @Nullable final Direction direction) {
        collector.offer(Capabilities.ITEM_HANDLER, deviceItems.combinedItemHandlers);
        collector.offer(Capabilities.DEVICE_BUS_ELEMENT, busElement);
        collector.offer(Capabilities.TERMINAL_USER_PROVIDER, this);

        if (Config.computersUseEnergy()) {
            collector.offer(Capabilities.ENERGY_STORAGE, energy);
        }
    }

    @Override
    protected void loadClient() {
        super.loadClient();

        terminal.setDisplayOnly(true);
    }

    @Override
    protected void loadServer() {
        super.loadServer();

        busElement.initialize();
        virtualMachine.state.builtinDevices.rtcMinecraft.setLevel(level);
    }

    @Override
    protected void unloadServer(final boolean isRemove) {
        super.unloadServer(isRemove);

        if (isRemove) {
            virtualMachine.stop();
        } else {
            virtualMachine.suspend();
        }

        virtualMachine.dispose();

        // This is necessary in case some other controller found us before our controller
        // did its scan, which can happen because the scan can happen with a delay. In
        // that case we don't know that controller and disposing our controller won't
        // notify it, so we also send out a notification through our bus element, which
        // would be registered with other controllers in that case.
        busElement.scheduleScan();
    }

    ///////////////////////////////////////////////////////////////////

    private final class ComputerItemStackHandlers extends AbstractVMItemStackHandlers {
        public ComputerItemStackHandlers() {
            super(
                new GroupDefinition(DeviceTypes.MEMORY, MEMORY_SLOTS),
                new GroupDefinition(DeviceTypes.HARD_DRIVE, HARD_DRIVE_SLOTS),
                new GroupDefinition(DeviceTypes.FLASH_MEMORY, FLASH_MEMORY_SLOTS),
                new GroupDefinition(DeviceTypes.CARD, CARD_SLOTS)
            );
        }

        @Override
        protected ItemDeviceQuery getDeviceQuery(final ItemStack stack) {
            return Devices.makeQuery(ComputerBlockEntity.this, stack);
        }

        @Override
        protected void onContentsChanged(final DeviceItemStackHandler itemStackHandler, final int slot) {
            super.onContentsChanged(itemStackHandler, slot);
            setChanged();
            isNeighborUpdateScheduled = true;
        }
    }

    private final class ComputerBusElement extends BlockEntityDeviceBusElement {
        private static final String DEVICE_ID_TAG_NAME = "device_id";

        private final HashSet<Device> devices = new HashSet<>();
        private UUID deviceId = UUID.randomUUID();

        public ComputerBusElement() {
            super(ComputerBlockEntity.this);
        }

        public void addOwnDevices() {
            assert level != null;

            for (final BlockEntry info : collectDevices(level, getPosition(), null)) {
                devices.add(info.getDevice());
                super.addDevice(info.getDevice());
            }
        }

        @Override
        public Optional<Collection<LazyOptional<DeviceBusElement>>> getNeighbors() {
            return super.getNeighbors().map(neighbors -> {
                // If we have valid neighbors (complete bus) also add a connection to the bus
                // element hosting our item devices.
                final ArrayList<LazyOptional<DeviceBusElement>> list = new ArrayList<>(neighbors);
                list.add(LazyOptional.of(() -> deviceItems.busElement));
                return list;
            });
        }

        @Override
        public boolean canScanContinueTowards(@Nullable final Direction direction) {
            return getBlockState().getValue(ComputerBlock.FACING) != direction;
        }

        @Override
        public Optional<UUID> getDeviceIdentifier(final Device device) {
            if (devices.contains(device)) {
                return Optional.of(deviceId);
            }
            return super.getDeviceIdentifier(device);
        }

        @Override
        public CompoundTag save() {
            final CompoundTag tag = super.save();
            tag.putUUID(DEVICE_ID_TAG_NAME, deviceId);
            return tag;
        }

        public void load(final CompoundTag tag) {
            super.load(tag);
            if (tag.hasUUID(DEVICE_ID_TAG_NAME)) {
                deviceId = tag.getUUID(DEVICE_ID_TAG_NAME);
            }
        }
    }

    private final class ComputerVMRunner extends AbstractTerminalVMRunner {
        public ComputerVMRunner(final AbstractVirtualMachine virtualMachine, final Terminal terminal) {
            super(virtualMachine, terminal);
        }

        @Override
        protected void sendTerminalUpdateToClient(final ByteBuffer output) {
            Network.sendToClientsTrackingChunk(new ComputerTerminalOutputMessage(ComputerBlockEntity.this, output), virtualMachine.chunk);
        }
    }

    private final class ComputerVirtualMachine extends AbstractVirtualMachine {
        private LevelChunk chunk;

        private ComputerVirtualMachine(final CommonDeviceBusController busController, final BaseAddressProvider baseAddressProvider) {
            super(busController);
            state.vmAdapter.setBaseAddressProvider(baseAddressProvider);
        }

        @Override
        public void setRunStateClient(final VMRunState value) {
            super.setRunStateClient(value);

            if (value == VMRunState.RUNNING) {
                if (!LoopingSoundManager.isPlaying(ComputerBlockEntity.this) && level != null) {
                    LoopingSoundManager.play(ComputerBlockEntity.this, SoundEvents.COMPUTER_RUNNING.get(), level.getRandom().nextInt(MAX_RUNNING_SOUND_DELAY));
                }
            } else {
                LoopingSoundManager.stop(ComputerBlockEntity.this);
            }
        }

        @Override
        public void tick() {
            assert level != null;

            if (chunk == null) {
                chunk = level.getChunkAt(getBlockPos());
            }

            if (isRunning()) {
                chunk.setUnsaved(true);
            }

            super.tick();
        }

        @Override
        protected boolean consumeEnergy(final int amount, final boolean simulate) {
            if (!Config.computersUseEnergy()) {
                return true;
            }

            if (amount > energy.getEnergyStored()) {
                return false;
            }

            energy.extractEnergy(amount, simulate);
            return true;
        }

        @Override
        protected void stopRunnerAndReset() {
            super.stopRunnerAndReset();

            TerminalUtils.resetTerminal(terminal, output -> Network.sendToClientsTrackingChunk(
                new ComputerTerminalOutputMessage(ComputerBlockEntity.this, output), chunk));
        }

        @Override
        protected AbstractTerminalVMRunner createRunner() {
            return new ComputerVMRunner(this, terminal);
        }

        @Override
        protected void handleBusStateChanged(final CommonDeviceBusController.BusState value) {
            Network.sendToClientsTrackingChunk(new ComputerBusStateMessage(ComputerBlockEntity.this, value), chunk);

            if (value == CommonDeviceBusController.BusState.READY && level != null) {
                // Bus just became ready, meaning new devices may be available, meaning new
                // capabilities may be available, so we need to tell our neighbors.
                level.updateNeighborsAt(getBlockPos(), getBlockState().getBlock());
            }
        }

        @Override
        protected void handleRunStateChanged(final VMRunState value) {
            // This method can be called from disposal logic, so if we are disposed quickly enough
            // chunk may not be initialized yet. Avoid resulting NRE in network logic.
            if (chunk != null) {
                Network.sendToClientsTrackingChunk(new ComputerRunStateMessage(ComputerBlockEntity.this, value), chunk);
            }
        }

        @Override
        protected void handleBootErrorChanged(@Nullable final Component value) {
            Network.sendToClientsTrackingChunk(new ComputerBootErrorMessage(ComputerBlockEntity.this, value), chunk);
        }
    }
}
