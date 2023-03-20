package nesemu;

import java.awt.Color;

public class PPU extends MemoryMapped {
    // TODO: OAM DMA

    private final static int PALETTE_MEM_SIZE = 32;
    private final static int NAMETABLE_SIZE = 1024;

    private final byte paletteMemory[];
    private final byte nametableMemory[][];

    private final PPUCTRL regPPUCTRL;
    private final PPUMASK regPPUMASK;
    private final PPUSTATUS regPPUSTATUS;
    private byte regOAMADDR;
    private byte regOAMDATA;
    private final TwoByteRegister regPPUSCROLL;
    private final TwoByteRegister regPPUADDR;
    private byte regPPUDATA;

    private int scanline;
    private int column;
    public boolean isFrameReady;

    public PPU() {
        paletteMemory = new byte[PALETTE_MEM_SIZE];
        nametableMemory = new byte[4][NAMETABLE_SIZE];
        regPPUCTRL = new PPUCTRL(0, 1, 0, 0, 8, true, false);
        regPPUMASK = new PPUMASK(false, false, false, false, false, false, false, false);
        regPPUSTATUS = new PPUSTATUS(false, false, false);
        regPPUADDR = new TwoByteRegister((short)0);
        regPPUSCROLL = new TwoByteRegister((short)0);
    }

    public void clockTick(Color[][] frameBuffer, CPU cpu, Cartridge cartridge) {
        if (scanline < 240 && column < 256) {
            int tileNumber = nametableMemory[regPPUCTRL.baseNametableAddress][(scanline / 8) * 32 + column / 8];
            int pixelAddress = (tileNumber * 16) + scanline % 8;
            if (regPPUCTRL.backgroundPatternTableAddress == 1)
                pixelAddress |= 0x1000;
            int colorNum = ((cartridge.ppuReadByte((short)(pixelAddress)) >>> (7 - column % 8)) & 1)
                        + 2 * ((cartridge.ppuReadByte((short)(pixelAddress + 8)) >>> (7 - column % 8)) & 1);
            final Color[] colors = new Color[] {
                Color.BLACK, Color.DARK_GRAY, Color.LIGHT_GRAY, Color.WHITE
            };
            frameBuffer[scanline][column] = colors[colorNum];
        }
        column++;
        if (column >= 341) {
            column = 0;
            scanline++;
        }
        if (scanline >= 240) {
            if (scanline == 240 && column == 0) {
                isFrameReady = true;
                if (regPPUCTRL.generateNMIOnVBlank)
                    cpu.requestNMI = true;
            }
            regPPUSTATUS.verticalBlank = true;
        } else
            regPPUSTATUS.verticalBlank = false;
        if (scanline >= 261)
            scanline = 0;
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

    private class PPUMASK {
        public boolean grayscale;
        public boolean showBackgroundLeft;
        public boolean showSpritesLeft;
        public boolean showBackground;
        public boolean showSprites;
        public boolean emphasizeRed;
        public boolean emphasizeGreen;
        public boolean emphasizeBlue;

        public PPUMASK(boolean grayscale, boolean showBackgroundLeft,
                boolean showSpritesLeft, boolean showBackground, boolean showSprites,
                boolean emphasizeRed, boolean emphasizeGreen, boolean emphasizeBlue) {
            this.grayscale = grayscale;
            this.showBackgroundLeft = showBackgroundLeft;
            this.showSpritesLeft = showSpritesLeft;
            this.showBackground = showBackground;
            this.showSprites = showSprites;
            this.emphasizeRed = emphasizeRed;
            this.emphasizeGreen = emphasizeGreen;
            this.emphasizeBlue = emphasizeBlue;
        }

        public void update(byte flags) {
            grayscale = (flags & 1) != 0;
            showBackgroundLeft = (flags & 2) != 0;
            showSpritesLeft = (flags & 4) != 0;
            showBackground = (flags & 8) != 0;
            showSprites = (flags & 16) != 0;
            emphasizeRed = (flags & 32) != 0;
            emphasizeGreen = (flags & 64) != 0;
            emphasizeBlue = (flags & 128) != 0;
        }
    }

    private class PPUSTATUS {
        public boolean spriteOverflow;
        public boolean spriteZeroHit;
        public boolean verticalBlank;

        public PPUSTATUS(boolean spriteOverflow, boolean spriteZeroHit,
                boolean verticalBlank) {
            this.spriteOverflow = spriteOverflow;
            this.spriteZeroHit = spriteZeroHit;
            this.verticalBlank = verticalBlank;
        }

        public byte toByte() {
            return (byte)((spriteOverflow ? 32 : 0) | (spriteZeroHit ? 64 : 0) |
                    (verticalBlank ? 128 : 0));
        }
    }

    private class TwoByteRegister {
        public short twoByteValue;
        private boolean upperByteSet;

        public TwoByteRegister(short twoByteValue) {
            this.twoByteValue = twoByteValue;
            this.upperByteSet = false;
        }

        public void update(byte value) {
            if (upperByteSet)
                twoByteValue |= value;
            else
                twoByteValue = (short)(value << 8);
            upperByteSet = !upperByteSet;
        }
    }

    private void writeByteAtPPUADDR(byte value) {
        short address = (short)(regPPUADDR.twoByteValue & 0x3FFF);
        //if (address < 0x2000)
        //    patternMemory[(address & 0x1000) >>> 12][address & 0xFFF] = value;
        //else
        if (address >= 0x2000 && address < 0x3F00)
            nametableMemory[(address & 0xC00) >>> 10][address & 0x3FF] = value;
        else if (address >= 0x3F00)
            paletteMemory[address & 0x1F] = value;
        else
            throw new UnsupportedOperationException("Unsupported PPUADDR address");
    }

    @Override
    boolean addressIsMapped(short address) {
        return address >= 0x2000 && address <= 0x3FFF;
    }

    @Override
    byte readByteFromDevice(short address) {
        switch (address & 7) {
            case 2 -> {
                return regPPUSTATUS.toByte();
            }
            case 4 -> {
                return regOAMDATA;
            }
            case 7 -> {
                return regPPUDATA;
            }
            default -> throw new UnsupportedOperationException("Unsupported PPU register 0x" +
                    String.format("%04X", address));
        }
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        switch (address & 7) {
            case 0 -> regPPUCTRL.update(value);
            case 1 -> regPPUMASK.update(value);
            case 2 -> regOAMADDR = value;
            case 4 -> regOAMDATA = value;
            case 5 -> regPPUSCROLL.update(value);
            case 6 -> regPPUADDR.update(value);
            case 7 -> {
                regPPUDATA = value;
                writeByteAtPPUADDR(value);
                regPPUADDR.twoByteValue += regPPUCTRL.nametableAddressIncrement;
            }
            default -> throw new UnsupportedOperationException("Unsupported PPU register 0x" +
                    String.format("%04X", address));
        }
    }
}
