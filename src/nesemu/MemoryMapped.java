package nesemu;

public abstract class MemoryMapped {
    AddressSpace addressSpace;

    abstract boolean addressIsMapped(short address);
    abstract byte readByte(short address);
    abstract void writeByte(short address, byte value);

    void linkAddressSpace(AddressSpace addressSpace) {
        this.addressSpace = addressSpace;
    }
}
