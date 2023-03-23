package nesemu;

import java.awt.Color;
import java.awt.event.KeyEvent;
import java.io.IOException;

public class NES {
    private static final int SCREEN_WIDTH_PX = 256;
    private static final int SCREEN_HEIGHT_PX = 240;

    private final AddressSpace addressSpace;
    private final CPU cpu;
    private final PPU ppu;
    private final RAM ram;
    private final Cartridge cartridge;
    public final Controller controller;
    public final Color[][] frameBuffer;

    public NES(String cartridgeFilePath) throws IOException {
        addressSpace = new AddressSpace();
        cpu = new CPU();
        ram = new RAM();
        cartridge = Cartridge.fromINESFile(cartridgeFilePath);
        ppu = new PPU(cartridge);
        controller = new Controller();
        frameBuffer = new Color[SCREEN_HEIGHT_PX][SCREEN_WIDTH_PX];
        addressSpace.addDevice(cartridge);
        addressSpace.addDevice(ppu);
        addressSpace.addDevice(ram);
        addressSpace.addDevice(controller);
        addressSpace.addDevice(cpu);
        cpu.reset();
    }

    public void runUntilFrameReady() {
        while (!ppu.isFrameReady) {
            ppu.clockTick(frameBuffer, cpu);
            ppu.clockTick(frameBuffer, cpu);
            ppu.clockTick(frameBuffer, cpu);
            cpu.clockTick();
        }
        ppu.isFrameReady = false;
    }
}
