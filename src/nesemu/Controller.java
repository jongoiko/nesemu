package nesemu;

import java.awt.event.KeyEvent;

public class Controller extends MemoryMapped {

    private enum Button {
        BUTTON_A(KeyEvent.VK_X, false, (byte)1),
        BUTTON_B(KeyEvent.VK_Z, false, (byte)(1 << 1)),
        BUTTON_SELECT(KeyEvent.VK_SHIFT, false, (byte)(1 << 2)),
        BUTTON_START(KeyEvent.VK_ENTER, false, (byte)(1 << 3)),
        BUTTON_UP(KeyEvent.VK_UP, false, (byte)(1 << 4)),
        BUTTON_DOWN(KeyEvent.VK_DOWN, false, (byte)(1 << 5)),
        BUTTON_LEFT(KeyEvent.VK_LEFT, false, (byte)(1 << 6)),
        BUTTON_RIGHT(KeyEvent.VK_RIGHT, false, (byte)(1 << 7));

        public int keyCode;
        public boolean isPressed;
        public byte bit;

        private Button(int keyCode, boolean pressed, byte bit) {
            this.keyCode = keyCode;
            this.isPressed = pressed;
            this.bit = bit;
        }
    }

    private boolean poll;
    private byte buffer;

    public Controller() {

    }

    private void getKeyByte() {
        buffer = 0;
        for (Button button : Button.values())
            if (button.isPressed)
                buffer |= button.bit;
    }

    @Override
    boolean addressIsMapped(short address) {
        return address == 0x4016 || address == 0x4017;
    }

    @Override
    byte readByteFromDevice(short address) {
        if (address == 0x4016) {
            int bit = buffer & 1;
            buffer >>>= 1;
            buffer |= 0x80;
            return (byte)(bit | 0x40);
        }
        return 0x40;
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        boolean prevPoll = poll;
        if (address == 0x4016) {
            poll = (value & 1) != 0;
            if (prevPoll && !poll)
                getKeyByte();
        }
    }

    public void keyPressed(KeyEvent ke) {
        int keyCode = ke.getKeyCode();
        for (Button button : Button.values())
            if (keyCode == button.keyCode) {
                button.isPressed = true;
                return;
            }
    }

    public void keyReleased(KeyEvent ke) {
        int keyCode = ke.getKeyCode();
        for (Button button : Button.values()) {
            if (keyCode == button.keyCode) {
                button.isPressed = false;
                return;
            }
        }
    }
}
