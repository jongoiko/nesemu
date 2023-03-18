package nesemu;

public class PPU extends MemoryMapped {
    // TODO: OAM DMA
    final private byte registers[];

    public PPU() {
        this.registers = new byte[8];
    }

    private enum PPURegister {
        PPUCTRL(0),
        PPUMASK(1),
        PPUSTATUS(2),
        OAMADDR(3),
        OAMDATA(4),
        PPUSCROLL(5),
        PPUADDR(6),
        PPUDATA(7);

        public final int index;

        PPURegister(int index) {
            this.index = index;
        }
    }

    @Override
    boolean addressIsMapped(short address) {
        return address >= 0x2000 && address <= 0x3FFF;
    }

    @Override
    byte readByteFromDevice(short address) {
        return registers[address & 7];
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        registers[address & 7] = value;
    }
}
