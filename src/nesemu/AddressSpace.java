package nesemu;

import java.util.ArrayList;
import java.util.List;

public class AddressSpace {
    private final List<MemoryMapped> devices;

    public AddressSpace() {
        devices = new ArrayList<>();
    }

    public void addDevice(MemoryMapped device) {
        device.linkAddressSpace(this);
        devices.add(device);
    }

    public byte readByte(short address) {
        for (MemoryMapped device : devices)
            if (device.addressIsMapped(address))
                return device.readByteFromDevice(address);
        return 0;
    }

    public void writeByte(short address, byte value) {
        for (MemoryMapped device : devices)
            if (device.addressIsMapped(address)) {
                device.writeByteToDevice(address, value);
                return;
            }
    }
}
