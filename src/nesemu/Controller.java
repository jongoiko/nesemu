package nesemu;

import java.awt.event.KeyEvent;

public class Controller extends MemoryMapped {

    public enum Button {
        BUTTON_A(KeyEvent.VK_X, (byte)1),
        BUTTON_B(KeyEvent.VK_Z, (byte)(1 << 1)),
        BUTTON_SELECT(KeyEvent.VK_SHIFT, (byte)(1 << 2)),
        BUTTON_START(KeyEvent.VK_ENTER, (byte)(1 << 3)),
        BUTTON_UP(KeyEvent.VK_UP, (byte)(1 << 4)),
        BUTTON_DOWN(KeyEvent.VK_DOWN, (byte)(1 << 5)),
        BUTTON_LEFT(KeyEvent.VK_LEFT, (byte)(1 << 6)),
        BUTTON_RIGHT(KeyEvent.VK_RIGHT, (byte)(1 << 7));

        public int keyCode;
        public boolean isPressedByPlayerOne;
        public boolean isPressedByPlayerTwo;
        public byte bit;

        private Button(int keyCode, byte bit) {
            this.keyCode = keyCode;
            this.isPressedByPlayerOne = false;
            this.isPressedByPlayerTwo = false;
            this.bit = bit;
        }

        public static Button fromKeyCode(int keyCode) {
            for (Button button : Button.values()) {
                if (keyCode == button.keyCode)
                    return button;
            }
            return null;
        }
    }

    private boolean poll;
    private byte playerOneBuffer;
    private byte playerTwoBuffer;

    public Controller() {
        poll = false;
        playerOneBuffer = 0;
        playerTwoBuffer = 0;
    }

    private void getKeyBytes() {
        playerOneBuffer = 0;
        playerTwoBuffer = 0;
        for (Button button : Button.values()) {
            if (button.isPressedByPlayerOne)
                playerOneBuffer |= button.bit;
            if (button.isPressedByPlayerTwo)
                playerTwoBuffer |= button.bit;
        }
    }

    @Override
    boolean addressIsMapped(short address) {
        return address == 0x4016 || address == 0x4017;
    }

    @Override
    byte readByteFromDevice(short address) {
        int bit;
        if (address == 0x4016) {
            bit = playerOneBuffer & 1;
            playerOneBuffer >>>= 1;
            playerOneBuffer |= 0x80;
        } else {
            bit = playerTwoBuffer & 1;
            playerTwoBuffer >>>= 1;
            playerTwoBuffer |= 0x80;
        }
        return (byte)(bit | 0x40);
    }

    @Override
    void writeByteToDevice(short address, byte value) {
        boolean prevPoll = poll;
        if (address == 0x4016) {
            poll = (value & 1) != 0;
            if (prevPoll && !poll)
                getKeyBytes();
        }
    }

    public void buttonPress(Button button, boolean isPlayerOne) {
        if (isPlayerOne)
            button.isPressedByPlayerOne = true;
        else
            button.isPressedByPlayerTwo = true;
    }

    public void buttonRelease(Button button, boolean isPlayerOne) {
        if (isPlayerOne)
            button.isPressedByPlayerOne = false;
        else
            button.isPressedByPlayerTwo = false;
    }

    public void handleNetplayButtonPress(String line, boolean isPlayerOne) {
        final String[] strings = line.split(" ");
        final Button button = Button.valueOf(strings[0]);
        final boolean pressed = strings[1].equals("PRESSED");
        if (pressed)
            buttonPress(button, !isPlayerOne);
        else
            buttonRelease(button, !isPlayerOne);
    }
}
