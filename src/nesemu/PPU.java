package nesemu;

import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.logging.Level;
import java.util.logging.Logger;

/* The PPU (Picture Processing Unit) is the NES's graphics processor. In each
 * clock cycle, it outputs a single pixel to the screen. It has its own address
 * space (https://www.nesdev.org/wiki/PPU_memory_map) and it operates in parallel
 * with the CPU. See https://www.nesdev.org/wiki/PPU
 */

public class PPU extends MemoryMapped {
    private final static int PALETTE_MEM_SIZE = 32;
    private final static int NAMETABLE_SIZE = 1024;
    private final static int OAM_SIZE = 256;
    private final static int NUM_COLORS = 64;
    private final static int MAX_SPRITES_PER_SCANLINE = 8;

    // The palette file was produced using Bisqwit's tool at
    // https://bisqwit.iki.fi/utils/nespalette.php
    private final static int[] SYSTEM_PALETTE =
            readPaletteFromPalFile("/resources/ntscpalette.pal");

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

    // The following four registers enable scrolling, which is explained in
    // detail at https://www.nesdev.org/wiki/PPU_scrolling
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
        spritePatternLowByteShiftRegisters = new byte[MAX_SPRITES_PER_SCANLINE];
        spritePatternHighByteShiftRegisters = new byte[MAX_SPRITES_PER_SCANLINE];
        spriteAttributes = new byte[MAX_SPRITES_PER_SCANLINE];
        spriteXPositions = new byte[MAX_SPRITES_PER_SCANLINE];
        regPPUCTRL = new PPUCTRL();
        regPPUMASK = new PPUMASK();
        regPPUSTATUS = new PPUSTATUS();
    }

    // The PPU has a set of internal registers that may be read from and/or written
    // to by the CPU: https://www.nesdev.org/wiki/PPU_registers

    private class PPUCTRL implements Serializable {
        public boolean incrementVramAddressByWholeRow;
        public boolean usingHighSpritePatternTable;
        public boolean usingHighBackgroundPatternTable;
        public boolean eightBySixteenMode;
        public boolean generateNMIOnVBlank;

        public void update(byte flags) {
            incrementVramAddressByWholeRow = (flags & 4) != 0;
            usingHighSpritePatternTable = (flags & 8) != 0;
            usingHighBackgroundPatternTable = (flags & 16) != 0;
            eightBySixteenMode = (flags & 32) != 0;
            generateNMIOnVBlank = (flags & 128) != 0;
        }
    }

    private class PPUMASK implements Serializable {
        public boolean grayscale;
        public boolean showBackgroundLeft;
        public boolean showSpritesLeft;
        public boolean showBackground;
        public boolean showSprites;
        public int emphasisBits;

        public void update(byte flags) {
            grayscale = (flags & 1) != 0;
            showBackgroundLeft = (flags & 2) != 0;
            showSpritesLeft = (flags & 4) != 0;
            showBackground = (flags & 8) != 0;
            showSprites = (flags & 16) != 0;
            emphasisBits = (flags & 0xE0) >>> 5;
        }
    }

    private class PPUSTATUS implements Serializable {
        public boolean spriteOverflow;
        public boolean spriteZeroHit;
        public boolean verticalBlank;

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

    private static int[] readPaletteFromPalFile(String fileName) {
        DataInputStream stream = new DataInputStream(
                PPU.class.getResourceAsStream(fileName));
        final int[] colors = new int[NUM_COLORS * 8];
        try {
            for (int i = 0; i < colors.length; i++) {
                byte rgb[] = stream.readNBytes(3);
                colors[i] = 0xFF000000 | (Byte.toUnsignedInt(rgb[0]) << 16) |
                        (Byte.toUnsignedInt(rgb[1]) << 8) |
                        Byte.toUnsignedInt(rgb[2]);
            }
        } catch (IOException ex) {
            Logger.getLogger(PPU.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
        return colors;
    }

    /* As mentioned, each clock tick of the PPU outputs a single pixel. This
     * implementation only supports the NTSC version, and as such it follows the
     * timings of the NTSC NES. For an overview of these timings and the workings
     * of the rendering pipeline, see https://www.nesdev.org/wiki/PPU_rendering
     */
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
            else if (column == 257) {
                copyVramAddressHorizontalPosition();
                readSpriteData();
            }
            else if (scanline >= 0 && column >= 1 && column < 256) {
                if (column == 1)
                    clearSecondaryOam();
                else if (column == 65)
                    evaluateSprites();
                Integer backgroundColorCode = null, spriteColorCode = null;
                if (regPPUMASK.showBackground &&
                        (column > 8 || regPPUMASK.showBackgroundLeft))
                    backgroundColorCode = getBackgroundPixelColorCode();
                if (scanline > 0 && regPPUMASK.showSprites &&
                        (column > 8 || regPPUMASK.showSpritesLeft))
                    spriteColorCode = getSpritePixelColorCode();
                renderPixel(backgroundColorCode, spriteColorCode, img);
                if (column < 255) {
                    shiftSpriteShiftRegisters();
                    decrementSpritesXPositions();
                }
            }
            if (column >= 257 && column <= 320)
                regOAMADDR = 0;
        } else if (scanline == 260 && column == 340 && frameCount % 2 != 0) {
            scanline = -1;
            column = 0;
        }
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

    /* After the color values for the background and/or the foreground have been
     * obtained, their priority is resolved according to the rules explained here:
     * https://www.nesdev.org/wiki/PPU_sprite_priority
     *
     * Once the priority is resolved, a grayscale mask and/or tinting may be
     * applied, depending on the corresponding control bits of PPUMASK.
     */
    private void renderPixel(Integer backgroundColorCode, Integer spriteColorCode,
            BufferedImage img) {
        int finalColorCode = Byte.toUnsignedInt(readByteFromPaletteMemory(0, true));
        if (backgroundColorCode != null)
            finalColorCode = backgroundColorCode;
        if (spriteColorCode != null) {
            if (spriteColorNumber != 0 && spriteHasPriorityOverBackground ||
                    backgroundColorNumber == 0)
                finalColorCode = spriteColorCode;
            if (backgroundColorCode != null && renderingSpriteZero &&
                    spriteColorNumber != 0 && backgroundColorNumber != 0)
                regPPUSTATUS.spriteZeroHit = true;
        }
        int colorEmphasisOffset = regPPUMASK.emphasisBits * NUM_COLORS;
        img.setRGB(column - 1, scanline, SYSTEM_PALETTE[colorEmphasisOffset +
                finalColorCode % NUM_COLORS]);
    }

    private void shiftBackgroundShiftRegisters() {
        backgroundPatternLowByteShiftRegister <<= 1;
        backgroundPatternHighByteShiftRegister <<= 1;
        backgroundAttributeLowByteShiftRegister <<= 1;
        backgroundAttributeHighByteShiftRegister <<= 1;
    }

    private void shiftSpriteShiftRegisters() {
        for (int i = 0; i < MAX_SPRITES_PER_SCANLINE; i++) {
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
        nextTileNumber =
                Byte.toUnsignedInt(readByteFromNametableMemory(tileNumberAddress));
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
        if (regPPUCTRL.usingHighBackgroundPatternTable)
            patternByteAddress |= 0x1000;
        nextTilePatternLowByte = cartridge.ppuReadByte((short)patternByteAddress);
    }

    private void readBackgroundPatternHighByte() {
        int patternByteAddress = nextTileNumber * 16 + 8 + (vramAddress >>> 12);
        if (regPPUCTRL.usingHighBackgroundPatternTable)
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

    private Integer getBackgroundPixelColorCode() {
        int pixel = 0x8000 >>> fineXScroll;
        backgroundColorNumber =
                ((backgroundPatternLowByteShiftRegister & pixel) != 0 ? 1 : 0) +
                2 * ((backgroundPatternHighByteShiftRegister & pixel) != 0 ? 1 : 0);
        int attribute =
                ((backgroundAttributeLowByteShiftRegister & pixel) != 0 ? 1 : 0) +
                2 * ((backgroundAttributeHighByteShiftRegister & pixel) != 0 ? 1 : 0);
        int colorCode = Byte.toUnsignedInt(
                readByteFromPaletteMemory(attribute * 4 + backgroundColorNumber, true));
        return colorCode;
    }

    private Integer getSpritePixelColorCode() {
        renderingSpriteZero = false;
        for (int i = 0; i < MAX_SPRITES_PER_SCANLINE; i++) {
            int xPosition = spriteXPositions[i];
            if (xPosition == 0) {
                byte lowByte = spritePatternLowByteShiftRegisters[i];
                byte highByte = spritePatternHighByteShiftRegisters[i];
                spriteColorNumber = ((lowByte & 0x80) != 0 ? 1 : 0) +
                        2 * ((highByte & 0x80) != 0 ? 1 : 0);
                int palette = spriteAttributes[i] & 3;
                spriteHasPriorityOverBackground = (spriteAttributes[i] & 0x20) == 0;
                int colorCode = Byte.toUnsignedInt(
                        readByteFromPaletteMemory(16 + palette * 4 + spriteColorNumber, true));
                if (spriteColorNumber != 0) {
                    renderingSpriteZero = isSpriteZeroInScanline && i == 0;
                    return colorCode;
                }
            }
        }
        return null;
    }

    private void clearSecondaryOam() {
        Arrays.fill(secondaryOamMemory, (byte)0xFF);
    }

    /* Sprite evaluation refers to the process of deciding which sprites will be
     * rendered on the next scanline, and where. See
     * https://www.nesdev.org/wiki/PPU_sprite_evaluation
     *
     * The sprite data is stored in OAM (Object Attribute Memory), whose format
     * is specified at https://www.nesdev.org/wiki/PPU_OAM
     */
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
            if (visibleSpriteCount >= 9) {
                if (isRenderingEnabled())
                    regPPUSTATUS.spriteOverflow = true;
                break;
            }
            spriteNumber++;
        }
    }

    void readSpriteData() {
        isSpriteZeroInScanline = isSpriteZeroLoadedToSecondaryOam;
        for (int i = 0; i < MAX_SPRITES_PER_SCANLINE; i++) {
            spriteXPositions[i] = secondaryOamMemory[i * 4 + 3];
            if (spriteXPositions[i] == -1) {
                spritePatternLowByteShiftRegisters[i] = 0;
                spritePatternHighByteShiftRegisters[i] = 0;
            } else {
                spriteAttributes[i] = secondaryOamMemory[i * 4 + 2];
                boolean isFlippedHorizontally = (spriteAttributes[i] & 0x40) != 0;
                boolean isFlippedVertically = (spriteAttributes[i] & 0x80) != 0;
                int yPosition = Byte.toUnsignedInt(secondaryOamMemory[i * 4]);
                int patternByteAddress;
                if (regPPUCTRL.eightBySixteenMode) {
                    byte tileNumberByte = secondaryOamMemory[i * 4 + 1];
                    int tileNumber = Byte.toUnsignedInt(tileNumberByte) >>> 1;
                    patternByteAddress = tileNumber * 32;
                    if (isFlippedVertically && scanline - yPosition <= 7 ||
                            !isFlippedVertically && scanline - yPosition > 7)
                        patternByteAddress += 16;
                    patternByteAddress += isFlippedVertically ?
                            7 - (scanline - yPosition) % 8: (scanline - yPosition) % 8;
                    if ((tileNumberByte & 1) != 0)
                        patternByteAddress |= 0x1000;
                } else {
                    int tileNumber = Byte.toUnsignedInt(secondaryOamMemory[i * 4 + 1]);
                    patternByteAddress = tileNumber * 16;
                    patternByteAddress += isFlippedVertically ?
                            7 - scanline + yPosition : scanline - yPosition;
                    if (regPPUCTRL.usingHighSpritePatternTable)
                        patternByteAddress |= 0x1000;
                }
                byte lowByte = cartridge.ppuReadByte((short)patternByteAddress);
                byte highByte = cartridge.ppuReadByte((short)(patternByteAddress + 8));
                if (isFlippedHorizontally) {
                    lowByte = reverseBits(lowByte);
                    highByte = reverseBits(highByte);
                }
                spritePatternLowByteShiftRegisters[i] = lowByte;
                spritePatternHighByteShiftRegisters[i] = highByte;
            }
        }
    }

    private static byte reverseBits(byte b) {
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
        int paletteMemoryIndex = address % 4 != 0 ? address :
                rendering ? 0 : address & ~0x10;
        byte colorByte = paletteMemory[paletteMemoryIndex];
        return (byte)(regPPUMASK.grayscale ? colorByte & 0x30 : colorByte);
    }

    private void writeByteToPaletteMemory(int address, byte value) {
        if ((address & 0x10) != 0 && address % 4 == 0)
            paletteMemory[address & ~0x10] = value;
        else
            paletteMemory[address] = value;
    }

    private int getNametableNumberFromAddress(int address) {
        switch (cartridge.mirroring) {
            case SINGLE_SCREEN_LOWER -> {
                return 0;
            }
            case SINGLE_SCREEN_UPPER -> {
                return 1;
            }
            case HORIZONTAL -> {
                return (address & 0x800) >>> 11;
            }
            case VERTICAL -> {
                return (address & 0x400) >>> 10;
            }
        }
        return (address & 0xC00) >>> 10;
    }

    private byte readByteFromNametableMemory(int address) {
        int nametableNumber = getNametableNumberFromAddress(address);
        return nametableMemory[nametableNumber][address & 0x3FF];
    }

    private void writeByteToNametableMemory(int address, byte value) {
        int nametableNumber = getNametableNumberFromAddress(address);
        nametableMemory[nametableNumber][address & 0x3FF] = value;
    }

    private byte readByteFromVramAddress() {
        byte buffer = regPPUDATA;
        if (vramAddress >= 0x3F00) {
            regPPUDATA = readByteFromNametableMemory(vramAddress & 0xFFF);
            return readByteFromPaletteMemory(vramAddress & 0x1F, false);
        }
        if (vramAddress < 0x2000)
            regPPUDATA = cartridge.ppuReadByte(vramAddress);
        else if (vramAddress >= 0x2000 && vramAddress < 0x3F00)
            regPPUDATA = readByteFromNametableMemory(vramAddress & 0xFFF);
        return buffer;
    }

    private void writeByteAtVramAddress(byte value) {
        short address = (short)(vramAddress & 0x3FFF);
        if (address < 0x2000)
            cartridge.ppuWriteByte(address, value);
        else if (address >= 0x2000 && address < 0x3F00)
            writeByteToNametableMemory(address & 0xFFF, value);
        else if (address >= 0x3F00)
            writeByteToPaletteMemory(address & 0x1F, value);
    }

    @Override
    public boolean addressIsMapped(short address) {
        return address >= 0x2000 && address <= 0x3FFF;
    }

    @Override
    public byte readByteFromDevice(short address) {
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
                vramAddress += regPPUCTRL.incrementVramAddressByWholeRow ? 32 : 1;
                return value;
            }
        }
        return 0;
    }

    @Override
    public void writeByteToDevice(short address, byte value) {
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
                vramAddress += regPPUCTRL.incrementVramAddressByWholeRow ? 32 : 1;
            }
        }
    }
}
