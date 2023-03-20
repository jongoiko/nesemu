package nesemu;

import java.awt.Color;
import java.io.IOException;

public class NES {
    private static final int SCREEN_WIDTH_PX = 256;
    private static final int SCREEN_HEIGHT_PX = 240;

    private final AddressSpace addressSpace;
    private final CPU cpu;
    private final PPU ppu;
    private final RAM ram;
    private final Cartridge cartridge;
    public final Color[][] frameBuffer;

    public NES(String cartridgeFilePath) throws IOException {
        addressSpace = new AddressSpace();
        cpu = new CPU();
        ppu = new PPU();
        ram = new RAM();
        cartridge = Cartridge.fromINESFile(cartridgeFilePath);
        frameBuffer = new Color[SCREEN_HEIGHT_PX][SCREEN_WIDTH_PX];
        addressSpace.addDevice(cartridge);
        addressSpace.addDevice(ppu);
        addressSpace.addDevice(ram);
        addressSpace.addDevice(cpu);
        cpu.reset();
    }

    public void runUntilFrameReady() {
        while (!ppu.isFrameReady) {
            ppu.clockTick(frameBuffer, cpu, cartridge);
            ppu.clockTick(frameBuffer, cpu, cartridge);
            ppu.clockTick(frameBuffer, cpu, cartridge);
            cpu.clockTick();
        }
        ppu.isFrameReady = false;
    }
}
