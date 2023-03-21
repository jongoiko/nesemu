package nesemu;

import java.awt.Color;

public class PPU extends MemoryMapped {
    // TODO: OAM DMA

    private final static int PALETTE_MEM_SIZE = 32;
    private final static int NAMETABLE_SIZE = 1024;
    private final static int NAMETABLE_ATTRIBUTE_TABLE_INDEX = 960;
    private final static Color[] SYSTEM_PALETTE = new Color[] {
        new Color(84, 84, 84),    new Color(0, 30, 116),    new Color(8, 16, 144),    new Color(48, 0, 136),
        new Color(68, 0, 100),    new Color(92, 0, 48),     new Color(84, 4, 0),      new Color(60, 24, 0),
        new Color(32, 42, 0),     new Color(8, 58, 0),      new Color(0, 64, 0),      new Color(0, 60, 0),
        new Color(0, 50, 60),     new Color(0, 0, 0),       new Color(0, 0, 0),       new Color(0, 0, 0),
        new Color(152, 150, 152), new Color(8, 76, 196),    new Color(48, 50, 236),   new Color(92, 30, 228),
        new Color(136, 20, 176),  new Color(160, 20, 100),  new Color(152, 34, 32),   new Color(120, 60, 0),
        new Color(84, 90, 0),     new Color(40, 114, 0),    new Color(8, 124, 0),     new Color(0, 118, 40),
        new Color(0, 102, 120),   new Color(0, 0, 0),       new Color(0, 0, 0),       new Color(0, 0, 0),
        new Color(236, 238, 236), new Color(76, 154, 236),  new Color(120, 124, 236), new Color(176, 98, 236),
        new Color(228, 84, 236),  new Color(236, 88, 180),  new Color(236, 106, 100), new Color(212, 136, 32),
        new Color(160, 170, 0),   new Color(116, 196, 0),   new Color(76, 208, 32),   new Color(56, 204, 108),
        new Color(56, 180, 204),  new Color(60, 60, 60),    new Color(0, 0, 0),       new Color(0, 0, 0),
        new Color(236, 238, 236), new Color(168, 204, 236), new Color(188, 188, 236), new Color(212, 178, 236),
        new Color(236, 174, 236), new Color(236, 174, 212), new Color(236, 180, 176), new Color(228, 196, 144),
        new Color(204, 210, 120), new Color(180, 222, 120), new Color(168, 226, 144), new Color(152, 226, 180),
        new Color(160, 214, 228), new Color(160, 162, 160), new Color(0, 0, 0),       new Color(0, 0, 0)
    };

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
            renderTile(frameBuffer, cartridge);
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
                twoByteValue |= Byte.toUnsignedInt(value);
            else
                twoByteValue = (short)(value << 8);
            upperByteSet = !upperByteSet;
        }
    }

    private void renderTile(Color[][] frameBuffer, Cartridge cartridge) {
        int tileNumber = nametableMemory[regPPUCTRL.baseNametableAddress]
                [(scanline / 8) * 32 + column / 8];
        int pixelAddress = (tileNumber * 16) + scanline % 8;
        if (regPPUCTRL.backgroundPatternTableAddress == 1)
            pixelAddress |= 0x1000;
        int lsb = (cartridge.ppuReadByte((short)(pixelAddress)) >>> (7 - column % 8)) & 1;
        int msb = (cartridge.ppuReadByte((short)(pixelAddress + 8)) >>> (7 - column % 8)) & 1;
        int colorNum = lsb + 2 * msb;
        frameBuffer[scanline][column] =
                SYSTEM_PALETTE[paletteMemory[getPaletteNumber() * 4 + colorNum]];
    }

    private int getPaletteNumber() {
        int attributeAddress = NAMETABLE_ATTRIBUTE_TABLE_INDEX +
                (scanline / 32) * 8 + column / 32;
        byte attributeByte = nametableMemory[regPPUCTRL.baseNametableAddress]
                [attributeAddress];
        int regionNumberX = ((scanline / 16) * 16) % 2,
                regionNumberY = (column / 16) % 2;
        if (regionNumberX == 0 && regionNumberY == 0)
            return attributeByte & 3;
        else if (regionNumberX == 0 && regionNumberY == 1)
            return (attributeByte >>> 2) & 3;
        else if (regionNumberX == 1 && regionNumberY == 0)
            return (attributeByte >>> 4) & 3;
        return (attributeByte >>> 6) & 3;
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
