package nesemu;

public class RAM extends MemoryMapped {
    final byte ram[];

    public RAM() {
        ram = new byte[0x800];
    }

    @Override
    public boolean addressIsMapped(short address) {
        return address >= 0 && address < 0x2000;
    }

    @Override
    public byte readByte(short address) {
        return ram[address & 0x7FF];
    }

    @Override
    public void writeByte(short address, byte value) {
        ram[address & 0x7FF] = value;
    }
}
