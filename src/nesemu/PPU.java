package nesemu;

import java.awt.Color;
import java.awt.image.BufferedImage;

public class PPU extends MemoryMapped {
    private final static int PALETTE_MEM_SIZE = 32;
    private final static int NAMETABLE_SIZE = 1024;
    private final static int OAM_SIZE = 256;

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

    private final Cartridge cartridge;
    private final byte paletteMemory[];
    private final byte nametableMemory[][];
    private final byte oamMemory[];

    private final PPUCTRL regPPUCTRL;
    private final PPUMASK regPPUMASK;
    private final PPUSTATUS regPPUSTATUS;
    private byte regOAMADDR;
    private byte regPPUDATA;

    private int scanline;
    private int column;
    private int frameCount;
    public boolean isFrameReady;

    private short vramAddress;
    private short tempVramAddress;
    private int fineXScroll;
    private boolean firstByteWritten;

    private byte nextTilePatternLowByte;
    private byte nextTilePatternHighByte;
    private int nextTileAttribute;
    private int nextTileNumber;
    private short patternLowByteShiftRegister;
    private short patternHighByteShiftRegister;
    private short attributeLowByteShiftRegister;
    private short attributeHighByteShiftRegister;

    public PPU(Cartridge cartridge) {
        this.cartridge = cartridge;
        scanline = -1;
        paletteMemory = new byte[PALETTE_MEM_SIZE];
        nametableMemory = new byte[4][NAMETABLE_SIZE];
        oamMemory = new byte[OAM_SIZE];
        regPPUCTRL = new PPUCTRL(1, 0, 0, 8, true, false);
        regPPUMASK = new PPUMASK(false, false, false, false, false, false, false, false);
        regPPUSTATUS = new PPUSTATUS(false, false, false);
    }

    private class PPUCTRL {
        public int nametableAddressIncrement;       // 1 or 32
        public int spritePatternTableAddress;       // 0 or 1
        public int backgroundPatternTableAddress;   // 0 or 1
        public int spriteRowCount;                  // 8 or 16
        public boolean isSlave;
        public boolean generateNMIOnVBlank;

        public PPUCTRL(int nametableAddressIncrement, int spritePatternTableAddress,
                int backgroundPatternTableAddress, int spriteRowCount, boolean isSlave,
                boolean generateNMIOnVBlank) {
            this.nametableAddressIncrement = nametableAddressIncrement;
            this.spritePatternTableAddress = spritePatternTableAddress;
            this.backgroundPatternTableAddress = backgroundPatternTableAddress;
            this.spriteRowCount = spriteRowCount;
            this.isSlave = isSlave;
            this.generateNMIOnVBlank = generateNMIOnVBlank;
        }

        public void update(byte flags) {
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

    public void clockTick(BufferedImage img, CPU cpu) {
        if (scanline >= -1 && scanline < 240) {
            if (scanline == -1 && column == 1)
                regPPUSTATUS.verticalBlank = false;
            if ((column >= 1 && column <= 257) || (column >= 321 && column <= 336)) {
                shiftRegisters();
                switch ((column - 1) % 8) {
                    case 0 -> {
                        loadLatchesIntoShiftRegisters();
                        readNametableByte();
                    }
                    case 2 -> readAttribute();
                    case 4 -> readPatternLowByte();
                    case 6 -> readPatternHighByte();
                    case 7 -> increaseHorizontalVramAddress();
                }
            }
            if (column == 256)
                increaseVerticalVramAddress();
            if (column == 257)
                copyHorizontalPosition();
            if (scanline >= 0 && column >= 1) {
                if (regPPUMASK.showBackground)
                    renderBackgroundPixel(img);
            }
        } else if (scanline == 260 && column == 340 && frameCount % 2 != 0) {
            scanline = -1;
            column = 0;
        }
        if (column >= 257 && column <= 320 && scanline < 240)
            regOAMADDR = 0;
        if (scanline == -1 && column >= 280 && column <= 304)
            copyVerticalPosition();
        else if (scanline == 241 && column == 1) {
            regPPUSTATUS.verticalBlank = true;
            isFrameReady = true;
            frameCount++;
            if (regPPUCTRL.generateNMIOnVBlank)
                cpu.requestNMI = true;
        }
        column++;
        if (column > 340) {
            column = 0;
            scanline++;
        }
        if (scanline > 260)
            scanline = -1;
    }

    private void shiftRegisters() {
        patternLowByteShiftRegister <<= 1;
        patternHighByteShiftRegister <<= 1;
        attributeLowByteShiftRegister <<= 1;
        attributeHighByteShiftRegister <<= 1;
    }

    private void loadLatchesIntoShiftRegisters() {
        patternLowByteShiftRegister &= 0xFF00;
        patternLowByteShiftRegister |= nextTilePatternLowByte & 0xFF;
        patternHighByteShiftRegister &= 0xFF00;
        patternHighByteShiftRegister |= nextTilePatternHighByte & 0xFF;
        attributeLowByteShiftRegister &= 0xFF00;
        attributeLowByteShiftRegister |= (nextTileAttribute & 1) != 0 ? 0xFF : 0;
        attributeHighByteShiftRegister &= 0xFF00;
        attributeHighByteShiftRegister |= (nextTileAttribute & 2) != 0 ? 0xFF : 0;
    }

    private boolean isRenderingEnabled() {
        return regPPUMASK.showBackground || regPPUMASK.showSprites;
    }

    private void readNametableByte() {
        int tileNumberAddress = vramAddress & 0xFFF;
        nextTileNumber = readByteFromNametableMemory(tileNumberAddress);
    }

    private void readAttribute() {
        int attributeByteAddress = 0x3C0 | (vramAddress & 0x0C00) |
                ((vramAddress >>> 4) & 0x38) | ((vramAddress >>> 2) & 7);
        byte attributeByte = readByteFromNametableMemory(attributeByteAddress);
        int regionNumberY = (vramAddress & 2) != 0 ? 1 : 0;
        int regionNumberX = (vramAddress & 64) != 0 ? 1 : 0;
        if (regionNumberX == 0 && regionNumberY == 0)
            nextTileAttribute = attributeByte & 3;
        else if (regionNumberX == 0 && regionNumberY == 1)
            nextTileAttribute = (attributeByte >>> 2) & 3;
        else if (regionNumberX == 1 && regionNumberY == 0)
            nextTileAttribute = (attributeByte >>> 4) & 3;
        else
            nextTileAttribute = (attributeByte >>> 6) & 3;
    }

    private void readPatternLowByte() {
        int patternByteAddress = nextTileNumber * 16 + (vramAddress >>> 12);
        if (regPPUCTRL.backgroundPatternTableAddress != 0)
            patternByteAddress |= 0x1000;
        nextTilePatternLowByte = cartridge.ppuReadByte((short)patternByteAddress);
    }

    private void readPatternHighByte() {
        int patternByteAddress = nextTileNumber * 16 + 8 + (vramAddress >>> 12);
        if (regPPUCTRL.backgroundPatternTableAddress != 0)
            patternByteAddress |= 0x1000;
        nextTilePatternHighByte = cartridge.ppuReadByte((short)patternByteAddress);
    }

    private void increaseHorizontalVramAddress() {
        if (isRenderingEnabled()) {
            if ((vramAddress & 0x1F) == 31) {
                vramAddress &= ~0x1F;
                vramAddress ^= 0x400;
            } else
                vramAddress++;
        }
    }

    private void increaseVerticalVramAddress() {
        if (isRenderingEnabled()) {
            if ((vramAddress & 0x7000) != 0x7000)
                vramAddress += 0x1000;
            else {
                vramAddress &= ~0x7000;
                int coarseY = (vramAddress & 0x3E0) >>> 5;
                switch (coarseY) {
                    case 29 -> {
                        coarseY = 0;
                        vramAddress ^= 0x800;
                    }
                    case 31 -> coarseY = 0;
                    default -> coarseY++;
                }
                vramAddress &= ~0x3E0;
                vramAddress |= coarseY << 5;
            }
        }
    }

    private void copyHorizontalPosition() {
        if (isRenderingEnabled()) {
            vramAddress &= ~0x41F;
            vramAddress |= tempVramAddress & 0x41F;
        }
    }

    private void copyVerticalPosition() {
        if (isRenderingEnabled()) {
            vramAddress &= ~0x7BE0;
            vramAddress |= tempVramAddress & 0x7BE0;
        }
    }
    private void renderBackgroundPixel(BufferedImage img) {
        int pixel = 0x8000 >>> fineXScroll;
        int colorIndex = ((patternLowByteShiftRegister & pixel) != 0 ? 1 : 0) +
                2 * ((patternHighByteShiftRegister & pixel) != 0 ? 1 : 0);
        int attribute = ((attributeLowByteShiftRegister & pixel) != 0 ? 1 : 0) +
                2 * ((attributeHighByteShiftRegister & pixel) != 0 ? 1 : 0);
        int colorCode = readByteFromPaletteMemory(attribute * 4 + colorIndex);
        // TODO: color tinting/emphasis using PPUMASK
        if (regPPUMASK.grayscale && (colorCode & 0xF) < 0xD)
            colorCode &= 0xF0;
        img.setRGB(column - 1, scanline, SYSTEM_PALETTE[colorCode].getRGB());
    }

    private byte readByteFromPaletteMemory(int address) {
        if ((address & 0x10) != 0 && address % 4 == 0)
            return paletteMemory[address & ~0x10];
        return paletteMemory[address];
    }

    private void writeByteToPaletteMemory(int address, byte value) {
        if ((address & 0x10) != 0 && address % 4 == 0)
            paletteMemory[address & ~0x10] = value;
        else
            paletteMemory[address] = value;
    }

    private byte readByteFromNametableMemory(int address) {
        int nametableNumber;
        switch (cartridge.mirroring) {
            case HORIZONTAL -> nametableNumber = (address & 0x800) >>> 11;
            case VERTICAL ->   nametableNumber = (address & 0x400) >>> 10;
            default ->         nametableNumber = (address & 0xC00) >>> 10;
        }
        return nametableMemory[nametableNumber][address & 0x3FF];
    }

    private void writeByteToNametableMemory(int address, byte value) {
        int nametableNumber;
        switch (cartridge.mirroring) {
            case HORIZONTAL -> nametableNumber = (address & 0x800) >>> 11;
            case VERTICAL ->   nametableNumber = (address & 0x400) >>> 10;
            default ->         nametableNumber = (address & 0xC00) >>> 10;
        }
        nametableMemory[nametableNumber][address & 0x3FF] = value;
    }

    private byte readByteFromVramAddress() {
        if (vramAddress >= 0x3F00)
            return readByteFromPaletteMemory(vramAddress & 0x1F);
        byte buffer = regPPUDATA;
        if (vramAddress < 0x2000)
            regPPUDATA = cartridge.ppuReadByte(vramAddress);
        else if (vramAddress >= 0x2000 && vramAddress < 0x3F00)
            regPPUDATA = readByteFromNametableMemory(vramAddress & 0xFFF);
        return buffer;
    }

    private void writeByteAtVramAddress(byte value) {
        short address = (short)(vramAddress & 0x3FFF);
        //if (address < 0x2000)
        //    patternMemory[(address & 0x1000) >>> 12][address & 0xFFF] = value;
        //else
        if (address >= 0x2000 && address < 0x3F00)
            writeByteToNametableMemory(address & 0xFFF, value);
        else if (address >= 0x3F00)
            writeByteToPaletteMemory(address & 0x1F, value);
        else
            throw new UnsupportedOperationException("Unsupported PPUADDR address " +
                    String.format("%04X", address));
    }

    @Override
    boolean addressIsMapped(short address) {
        return address >= 0x2000 && address <= 0x3FFF;
    }

    @Override
    byte readByteFromDevice(short address) {
        switch (address & 7) {
            case 2 -> {
                firstByteWritten = false;
                return regPPUSTATUS.toByte();
            }
            case 4 -> {
                return oamMemory[Byte.toUnsignedInt(regOAMADDR)];
            }
            case 7 -> {
                byte value = readByteFromVramAddress();
                vramAddress += regPPUCTRL.nametableAddressIncrement;
                return value;
            }
            default -> throw new UnsupportedOperationException("Unsupported PPU register 0x" +
                    String.format("%04X", address));
        }
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        switch (address & 7) {
            case 0 -> {
                tempVramAddress &= ~0xC00;
                tempVramAddress |= (value & 3) << 10;
                regPPUCTRL.update(value);
            }
            case 1 -> regPPUMASK.update(value);
            case 3 -> regOAMADDR = value;
            case 4 -> {
                oamMemory[Byte.toUnsignedInt(regOAMADDR)] = value;
                regOAMADDR++;
            }
            case 5 -> {
                if (firstByteWritten) {
                    tempVramAddress &= ~0x73E0;
                    tempVramAddress |= (value & 7) << 12;
                    tempVramAddress |= (value & 0xF8) << 2;
                } else {
                    tempVramAddress &= ~0x1F;
                    tempVramAddress |= (value & 0xF8) >>> 3;
                    fineXScroll = value & 7;
                }
                firstByteWritten = !firstByteWritten;
            }
            case 6 -> {
                if (firstByteWritten) {
                    tempVramAddress &= ~0xFF;
                    tempVramAddress |= Byte.toUnsignedInt(value);
                    vramAddress = tempVramAddress;
                } else {
                    tempVramAddress &= 0xFF;
                    tempVramAddress |= (value & 0x3F) << 8;
                }
                firstByteWritten = !firstByteWritten;
            }
            case 7 -> {
                writeByteAtVramAddress(value);
                vramAddress += regPPUCTRL.nametableAddressIncrement;
            }
            default -> throw new UnsupportedOperationException("Unsupported PPU register 0x" +
                    String.format("%04X", address));
        }
    }
}
