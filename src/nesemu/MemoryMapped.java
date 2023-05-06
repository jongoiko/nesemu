package nesemu;

import java.io.Serializable;

public abstract class MemoryMapped implements Serializable {
    AddressSpace addressSpace;

    abstract boolean addressIsMapped(short address);
    abstract byte readByteFromDevice(short address);
    abstract void writeByteToDevice(short address, byte value);

    void linkAddressSpace(AddressSpace addressSpace) {
        this.addressSpace = addressSpace;
    }
}
