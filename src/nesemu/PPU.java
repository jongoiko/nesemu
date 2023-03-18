package nesemu;

public class PPU extends MemoryMapped {
    // TODO: OAM DMA

    private final static int PATTERN_TABLE_SIZE = 4096;
    private final static int PALETTE_MEM_SIZE = 32;

    private final byte patternMemory[][];
    private final byte paletteMemory[];

    private PPUCTRL regPPUCTRL;

    public PPU() {
        patternMemory = new byte[PATTERN_TABLE_SIZE][2];
        paletteMemory = new byte[PALETTE_MEM_SIZE];
        regPPUCTRL = new PPUCTRL(0, 1, 0, 0, 8, true, false);
    }

    private class PPUCTRL {
        public int baseNametableAddress;            // 0 to 3
        public int nametableAddressIncrement;       // 1 or 32
        public int spritePatternTableAddress;       // 0 or 1
        public int backgroundPatternTableAddress;   // 0 or 1
        public int spriteRowCount;                  // 8 or 16
        public boolean isSlave;
        public boolean generateNMIOnVBlank;

        public PPUCTRL(int nametableAddress, int nametableAddressIncrement,
                int spritePatternTableAddress, int backgroundPatternTableAddress,
                int spriteRowCount, boolean isSlave, boolean generateNMIOnVBlank) {
            this.baseNametableAddress = nametableAddress;
            this.nametableAddressIncrement = nametableAddressIncrement;
            this.spritePatternTableAddress = spritePatternTableAddress;
            this.backgroundPatternTableAddress = backgroundPatternTableAddress;
            this.spriteRowCount = spriteRowCount;
            this.isSlave = isSlave;
            this.generateNMIOnVBlank = generateNMIOnVBlank;
        }

        public void update(byte flags) {
            baseNametableAddress = flags & 3;
            nametableAddressIncrement = (flags & 4) != 0 ? 32 : 1;
            spritePatternTableAddress = (flags & 8) >> 3;
            backgroundPatternTableAddress = (flags & 16) >> 4;
            spriteRowCount = (flags & 32) != 0 ? 16 : 8;
            isSlave = (flags & 64) == 0;
            generateNMIOnVBlank = (flags & 128) != 0;
        }
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
        return 0;
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        switch (address & 7) {
            case 0:
                regPPUCTRL.update(value);
                break;
            default:
                throw new UnsupportedOperationException("Unsupported PPU register");
        }
    }
}
