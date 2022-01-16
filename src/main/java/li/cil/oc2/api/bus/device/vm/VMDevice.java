package li.cil.oc2.api.bus.device.vm;

import li.cil.oc2.api.bus.device.Device;
import li.cil.oc2.api.bus.device.rpc.RPCDevice;
import li.cil.oc2.api.bus.device.vm.context.InterruptAllocator;
import li.cil.oc2.api.bus.device.vm.context.VMContext;
import li.cil.sedna.api.device.MemoryMappedDevice;

/**
 * Allows adding {@link MemoryMappedDevice}s directly to the underlying
 * virtual machine.
 * <p>
 * This is a more low-level approach than the {@link RPCDevice}. Devices
 * implemented through this interface will require explicit driver support
 * in the guest system.
 * <p>
 * To listen to lifecycle events of the VM and the device, register to the event
 * bus provided via {@link VMContext#getEventBus()} in {@link #mount(VMContext)}.
 * <p>
 * The lifecycle for VMDevices can be depicted as such:
 * <pre>
 *    ┌──────────────────────────────────┐
 *    │VirtualMachine.isRunning() = false◄────────────────────────┐
 *    └────────────────┬─────────────────┘                        │
 *                     │                                          │
 *          ┌──────────▼───────────┐                              │
 *          │VirtualMachine.start()│                              │
 *          └──────────┬───────────┘                              │
 *                     │                                          │
 *                     │   ┌──────────┐                           │
 *                     │   │Chunk Load│  ┌──────────────────┐     │
 *                     ├───┼──────────◄──┤VMDevice.suspend()◄───┐ │
 *                     │   │World Load│  └──────────────────┘   │ │
 *                     │   └──────────┘                         │ │
 *                     │                                        │ │
 *            ┌────────▼───────┐       ┌────┐                   │ │
 * ┌──────────►VMDevice.mount()◄───────┤Wait◄─────────┐         │ │
 * │          └────────┬───────┘       └──▲─┘         │         │ │
 * │                   │                  │           │         │ │
 * │                   │  ┌───────────────┴─────────┐ │         │ │
 * │                   ├──►VMDeviceLoadResult.fail()│ │         │ │
 * │                   │  └─────────────────────────┘ │         │ │
 * │                   │                              │         │ │
 * │    ┌──────────────▼─────────────┐ ┌──────────────┴───┐     │ │
 * │    │VMDeviceLoadResult.success()│ │VMDevice.unmount()│     │ │
 * │    └──────────────┬─────────────┘ └──────────────▲───┘     │ │
 * │                   │                              │         │ │
 * │                   │        ┌─────────────────────┴───┐     │ │
 * │                   ├────────►     Other VMDevice:     │     │ │
 * │                   │        │VMDeviceLoadResult.fail()│     │ │
 * │                   │        └─────────────────────────┘     │ │
 * │                   │                                        │ │
 * │                   │                     ┌────────────┐     │ │
 * │                   │                     │Chunk Unload│     │ │
 * │                   │                   ┌─►────────────┼─────┘ │
 * │                   │                   │ │World Unload│       │
 * │ ┌─────────────────▼───────────────┐   │ └────────────┘       │
 * │ │VirtualMachine.isRunning() = true├───┤                      │
 * │ └─────┬───────────────────┬───────┘   │ ┌──────────────────┐ │
 * │       │                   │           │ │Computer Shutdown │ │
 * │ ┌─────▼──────┐     ┌──────▼───────┐   └─►──────────────────┤ │
 * └─┤Device Added│     │Device Removed│     │Computer Destroyed│ │
 *   └────────────┘     └──────┬───────┘     └─────────┬────────┘ │
 *                             │                       │          │
 *                    ┌────────▼─────────┐   ┌─────────▼────────┐ │
 *                    │VMDevice.unmount()│   │VMDevice.unmount()├─┘
 *                    └──────────────────┘   └──────────────────┘
 * </pre>
 *
 * @see li.cil.oc2.api.bus.device.provider.BlockDeviceProvider
 * @see li.cil.oc2.api.bus.device.provider.ItemDeviceProvider
 */
public interface VMDevice extends Device {
    /**
     * Called to initialize this device.
     * <p>
     * Register {@link MemoryMappedDevice}s and claim interrupts via the
     * {@link InterruptAllocator} made available through the {@code context}.
     * <p>
     * If loading cannot complete, e.g. because resources cannot be allocated,
     * this should return {@code false}. The virtual machine will periodically
     * try again to load failed devices. The virtual machine will only start
     * running after all devices have successfully loaded.
     *
     * @param context the virtual machine context.
     * @return {@code true} if the device was loaded successfully; {@code false} otherwise.
     */
    VMDeviceLoadResult mount(VMContext context);

    /**
     * Called when the device is removed from the context it was loaded with.
     * <p>
     * This can happen because the VM was stopped or the device was removed from
     * the device bus that connected it to the VM, for example.
     * <p>
     * Intended for releasing resources acquired in {@link #mount(VMContext)}.
     */
    void unmount();

    /**
     * Called when the device is suspended.
     * <p>
     * This can happen when the level area containing the context the device was loaded in is unloaded,
     * e.g. due to player moving too far away from the area.
     * <p>
     * Intended for soft-releasing resources acquired in {@link #mount(VMContext)}, i.e. non-persisted
     * unmanaged resources.
     */
    void suspend();
}
