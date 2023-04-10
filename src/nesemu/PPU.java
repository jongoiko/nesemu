package nesemu;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.util.Arrays;

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

    public Cartridge cartridge;
    private final byte paletteMemory[];
    private final byte nametableMemory[][];
    private final byte oamMemory[];
    private final byte secondaryOamMemory[];

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

    private boolean isSpriteZeroLoadedToSecondaryOam;
    private boolean isSpriteZeroInScanline;
    private boolean renderingSpriteZero;

    private byte nextTilePatternLowByte;
    private byte nextTilePatternHighByte;
    private int nextTileAttribute;
    private int nextTileNumber;
    private short backgroundPatternLowByteShiftRegister;
    private short backgroundPatternHighByteShiftRegister;
    private short backgroundAttributeLowByteShiftRegister;
    private short backgroundAttributeHighByteShiftRegister;

    private final byte spritePatternLowByteShiftRegisters[];
    private final byte spritePatternHighByteShiftRegisters[];
    private final byte spriteAttributes[];
    private final byte spriteXPositions[];
    private boolean spriteHasPriorityOverBackground;

    private int backgroundColorNumber;
    private int spriteColorNumber;

    public PPU(Cartridge cartridge) {
        this.cartridge = cartridge;
        scanline = -1;
        paletteMemory = new byte[PALETTE_MEM_SIZE];
        nametableMemory = new byte[4][NAMETABLE_SIZE];
        oamMemory = new byte[OAM_SIZE];
        secondaryOamMemory = new byte[OAM_SIZE / 8];
        spritePatternLowByteShiftRegisters = new byte[8];
        spritePatternHighByteShiftRegisters = new byte[8];
        spriteAttributes = new byte[8];
        spriteXPositions = new byte[8];
        regPPUCTRL = new PPUCTRL(1, 0, 0, false, true, false);
        regPPUMASK = new PPUMASK(false, false, false, false, false, false, false, false);
        regPPUSTATUS = new PPUSTATUS(false, false, false);
    }

    private class PPUCTRL {
        public int nametableAddressIncrement;       // 1 or 32
        public int spritePatternTableAddress;       // 0 or 1
        public int backgroundPatternTableAddress;   // 0 or 1
        public boolean eightBySixteenMode;
        public boolean isSlave;
        public boolean generateNMIOnVBlank;

        public PPUCTRL(int nametableAddressIncrement, int spritePatternTableAddress,
                int backgroundPatternTableAddress, boolean eightBySixteenMode, boolean isSlave,
                boolean generateNMIOnVBlank) {
            this.nametableAddressIncrement = nametableAddressIncrement;
            this.spritePatternTableAddress = spritePatternTableAddress;
            this.backgroundPatternTableAddress = backgroundPatternTableAddress;
            this.eightBySixteenMode = eightBySixteenMode;
            this.isSlave = isSlave;
            this.generateNMIOnVBlank = generateNMIOnVBlank;
        }

        public void update(byte flags) {
            nametableAddressIncrement = (flags & 4) != 0 ? 32 : 1;
            spritePatternTableAddress = (flags & 8) >> 3;
            backgroundPatternTableAddress = (flags & 16) >> 4;
            eightBySixteenMode = (flags & 32) != 0;
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

        public void reset() {
            spriteOverflow = false;
            spriteZeroHit = false;
            verticalBlank = false;
        }
    }

    public void clockTick(BufferedImage img, CPU cpu) {
        if (scanline >= -1 && scanline < 240) {
            if (scanline == -1 && column == 1) {
                regPPUSTATUS.verticalBlank = false;
                regPPUSTATUS.spriteOverflow = false;
                regPPUSTATUS.spriteZeroHit = false;
            }
            if ((column >= 1 && column <= 257) || (column >= 321 && column <= 336)) {
                shiftBackgroundShiftRegisters();
                switch ((column - 1) % 8) {
                    case 0 -> {
                        loadLatchesIntoBackgroundShiftRegisters();
                        readBackgroundNametableByte();
                    }
                    case 2 -> readBackgroundAttribute();
                    case 4 -> readBackgroundPatternLowByte();
                    case 6 -> readBackgroundPatternHighByte();
                    case 7 -> increaseHorizontalVramAddress();
                }
            }
            if (column == 256)
                increaseVerticalVramAddress();
            if (column == 257) {
                copyVramAddressHorizontalPosition();
                readSpriteData();
            }
            if (scanline >= 0 && column >= 1 && column < 256) {
                if (column == 1)
                    clearSecondaryOam();
                else if (column == 65)
                    evaluateSprites();
                Color backgroundColor = null, spriteColor = null;
                if (column > 8 && regPPUMASK.showBackground ||
                        column <= 8 && regPPUMASK.showBackgroundLeft)
                    backgroundColor = getBackgroundPixelColor();
                if (column > 8 && regPPUMASK.showSprites ||
                        column <= 8 && regPPUMASK.showSpritesLeft)
                    spriteColor = getSpritePixelColor();
                renderPixel(backgroundColor, spriteColor, img);
                if (column < 255) {
                    shiftSpriteShiftRegisters();
                    decrementSpritesXPositions();
                }
            }
        } else if (scanline == 260 && column == 340 && frameCount % 2 != 0) {
            scanline = -1;
            column = 0;
        }
        if (column >= 257 && column <= 320 && scanline < 240)
            regOAMADDR = 0;
        if (scanline == -1 && column >= 280 && column <= 304)
            copyVramAddressVerticalPosition();
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

    public void reset() {
        scanline = -1;
        column = 0;
        frameCount = 0;
        vramAddress = 0;
        tempVramAddress = 0;
        fineXScroll = 0;
        firstByteWritten = false;
        isFrameReady = false;
        regPPUDATA = (byte)0;
        regPPUMASK.update((byte)0);
        regPPUCTRL.update((byte)0);
        regPPUSTATUS.reset();
    }

    private void renderPixel(Color backgroundColor, Color spriteColor, BufferedImage img) {
        Color finalColor = new Color(0);
        if (backgroundColor != null)
            finalColor = backgroundColor;
        if (spriteColor != null) {
            if (spriteColorNumber != 0 && spriteHasPriorityOverBackground ||
                    backgroundColorNumber == 0)
                finalColor = spriteColor;
            if (backgroundColor != null && renderingSpriteZero &&
                    spriteColorNumber != 0 && backgroundColorNumber != 0)
                regPPUSTATUS.spriteZeroHit = true;
        }
        img.setRGB(column - 1, scanline, finalColor.getRGB());
    }

    private void shiftBackgroundShiftRegisters() {
        backgroundPatternLowByteShiftRegister <<= 1;
        backgroundPatternHighByteShiftRegister <<= 1;
        backgroundAttributeLowByteShiftRegister <<= 1;
        backgroundAttributeHighByteShiftRegister <<= 1;
    }

    private void shiftSpriteShiftRegisters() {
        for (int i = 0; i < 8; i++) {
            if (spriteXPositions[i] == 0) {
                spritePatternLowByteShiftRegisters[i] <<= 1;
                spritePatternHighByteShiftRegisters[i] <<= 1;
            }
        }
    }

    private void loadLatchesIntoBackgroundShiftRegisters() {
        backgroundPatternLowByteShiftRegister &= 0xFF00;
        backgroundPatternLowByteShiftRegister |= nextTilePatternLowByte & 0xFF;
        backgroundPatternHighByteShiftRegister &= 0xFF00;
        backgroundPatternHighByteShiftRegister |= nextTilePatternHighByte & 0xFF;
        backgroundAttributeLowByteShiftRegister &= 0xFF00;
        backgroundAttributeLowByteShiftRegister |= (nextTileAttribute & 1) != 0 ? 0xFF : 0;
        backgroundAttributeHighByteShiftRegister &= 0xFF00;
        backgroundAttributeHighByteShiftRegister |= (nextTileAttribute & 2) != 0 ? 0xFF : 0;
    }

    private boolean isRenderingEnabled() {
        return regPPUMASK.showBackground || regPPUMASK.showSprites;
    }

    private void readBackgroundNametableByte() {
        int tileNumberAddress = vramAddress & 0xFFF;
        nextTileNumber = Byte.toUnsignedInt(readByteFromNametableMemory(tileNumberAddress));
    }

    private void readBackgroundAttribute() {
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

    private void readBackgroundPatternLowByte() {
        int patternByteAddress = nextTileNumber * 16 + (vramAddress >>> 12);
        if (regPPUCTRL.backgroundPatternTableAddress != 0)
            patternByteAddress |= 0x1000;
        nextTilePatternLowByte = cartridge.ppuReadByte((short)patternByteAddress);
    }

    private void readBackgroundPatternHighByte() {
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

    private void copyVramAddressHorizontalPosition() {
        if (isRenderingEnabled()) {
            vramAddress &= ~0x41F;
            vramAddress |= tempVramAddress & 0x41F;
        }
    }

    private void copyVramAddressVerticalPosition() {
        if (isRenderingEnabled()) {
            vramAddress &= ~0x7BE0;
            vramAddress |= tempVramAddress & 0x7BE0;
        }
    }

    private Color getBackgroundPixelColor() {
        int pixel = 0x8000 >>> fineXScroll;
        backgroundColorNumber = ((backgroundPatternLowByteShiftRegister & pixel) != 0 ? 1 : 0) +
                2 * ((backgroundPatternHighByteShiftRegister & pixel) != 0 ? 1 : 0);
        int attribute = ((backgroundAttributeLowByteShiftRegister & pixel) != 0 ? 1 : 0) +
                2 * ((backgroundAttributeHighByteShiftRegister & pixel) != 0 ? 1 : 0);
        int colorCode = readByteFromPaletteMemory(attribute * 4 + backgroundColorNumber, true);
        // TODO: color tinting/emphasis using PPUMASK
        if (regPPUMASK.grayscale && (colorCode & 0xF) < 0xD)
            colorCode &= 0xF0;
        return SYSTEM_PALETTE[colorCode];
    }

    private Color getSpritePixelColor() {
        renderingSpriteZero = false;
        for (int i = 0; i < 8; i++) {
            int xPosition = spriteXPositions[i];
            if (xPosition == 0) {
                byte lsb = spritePatternLowByteShiftRegisters[i];
                byte msb = spritePatternHighByteShiftRegisters[i];
                spriteColorNumber = ((lsb & 0x80) != 0 ? 1 : 0) +
                        2 * ((msb & 0x80) != 0 ? 1 : 0);
                int palette = spriteAttributes[i] & 3;
                spriteHasPriorityOverBackground = (spriteAttributes[i] & 0x20) == 0;
                int colorCode = readByteFromPaletteMemory(16 + palette * 4 +
                        spriteColorNumber, true);
                if (spriteColorNumber != 0) {
                    renderingSpriteZero = isSpriteZeroInScanline && i == 0;
                    return SYSTEM_PALETTE[colorCode];
                }
            }
        }
        return null;
    }

    private void clearSecondaryOam() {
        Arrays.fill(secondaryOamMemory, (byte)0xFF);
    }

    private void evaluateSprites() {
        isSpriteZeroLoadedToSecondaryOam = false;
        int spriteNumber = 0, visibleSpriteCount = 0;
        while (spriteNumber < 64) {
            int spriteAddress = spriteNumber * 4;
            int secondarySpriteAddress = visibleSpriteCount * 4;
            int yPosition = Byte.toUnsignedInt(oamMemory[spriteAddress]);
            int spriteHeight = regPPUCTRL.eightBySixteenMode ? 16 : 8;
            if (scanline >= yPosition && scanline < yPosition + spriteHeight) {
                if (visibleSpriteCount < 8) {
                    if (spriteNumber == 0)
                        isSpriteZeroLoadedToSecondaryOam = true;
                    for (int i = 0; i < 4; i++)
                        secondaryOamMemory[secondarySpriteAddress + i] =
                                oamMemory[spriteAddress + i];
                }
                visibleSpriteCount++;
            }
            if (visibleSpriteCount >= 9)
                break;
            spriteNumber++;
        }
        regPPUSTATUS.spriteOverflow = visibleSpriteCount == 9;
    }

    void readSpriteData() {
        isSpriteZeroInScanline = isSpriteZeroLoadedToSecondaryOam;
        for (int i = 0; i < 8; i++) {
            spriteXPositions[i] = secondaryOamMemory[i * 4 + 3];
            if (spriteXPositions[i] == -1) {
                spritePatternLowByteShiftRegisters[i] = 0;
                spritePatternHighByteShiftRegisters[i] = 0;
            } else {
                spriteAttributes[i] = secondaryOamMemory[i * 4 + 2];
                boolean horizontalFlip = (spriteAttributes[i] & 0x40) != 0;
                boolean verticalFlip = (spriteAttributes[i] & 0x80) != 0;
                int yPosition = Byte.toUnsignedInt(secondaryOamMemory[i * 4]);
                int patternByteAddress;
                if (regPPUCTRL.eightBySixteenMode) {
                    byte tileNumberByte = secondaryOamMemory[i * 4 + 1];
                    int tileNumber = Byte.toUnsignedInt(tileNumberByte) >>> 1;
                    patternByteAddress = tileNumber * 32;
                    if (verticalFlip && scanline - yPosition <= 7 ||
                            !verticalFlip && scanline - yPosition > 7)
                        patternByteAddress += 16;
                    patternByteAddress += verticalFlip ?
                            7 - (scanline - yPosition) % 8: (scanline - yPosition) % 8;
                    if ((tileNumberByte & 1) != 0)
                        patternByteAddress |= 0x1000;
                } else {
                    int tileNumber = Byte.toUnsignedInt(secondaryOamMemory[i * 4 + 1]);
                    patternByteAddress = tileNumber * 16;
                    patternByteAddress += verticalFlip ?
                            7 - scanline + yPosition : scanline - yPosition;
                    if (regPPUCTRL.spritePatternTableAddress != 0)
                        patternByteAddress |= 0x1000;
                }
                byte lsb = cartridge.ppuReadByte((short)patternByteAddress);
                byte msb = cartridge.ppuReadByte((short)(patternByteAddress + 8));
                if (horizontalFlip) {
                    lsb = reverseBits(lsb);
                    msb = reverseBits(msb);
                }
                spritePatternLowByteShiftRegisters[i] = lsb;
                spritePatternHighByteShiftRegisters[i] = msb;
            }
        }
    }

    private byte reverseBits(byte b) {
        byte out = 0;
        int in = Byte.toUnsignedInt(b);
        for (int i = 0; i < 8; i++) {
            out <<= 1;
            out |= in & 1;
            in >>>= 1;
        }
        return out;
    }

    private void decrementSpritesXPositions() {
        for (int i = 0; i < spriteXPositions.length; i++)
            if (spriteXPositions[i] != 0)
                spriteXPositions[i]--;
    }

    private byte readByteFromPaletteMemory(int address, boolean rendering) {
        if (address % 4 != 0)
            return paletteMemory[address];
        if (rendering)
            return paletteMemory[0];
        return paletteMemory[address & ~0x10];
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
            return readByteFromPaletteMemory(vramAddress & 0x1F, false);
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
                byte value = regPPUSTATUS.toByte();
                firstByteWritten = false;
                regPPUSTATUS.verticalBlank = false;
                return value;
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
