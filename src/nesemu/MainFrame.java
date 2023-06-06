package nesemu;

import java.io.IOException;
import javax.swing.UIManager;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.Color;
import java.awt.GridLayout;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.NumberFormat;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.UnsupportedLookAndFeelException;
import javax.swing.filechooser.FileNameExtensionFilter;
import java.util.logging.Logger;
import java.util.logging.Level;
import javax.swing.JFormattedTextField;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;
import javax.swing.JTextPane;
import javax.swing.text.NumberFormatter;

public class MainFrame extends javax.swing.JFrame {
    private static final int TARGET_FPS = 60;
    private static final int NANOSECS_PER_FRAME = (int)((1.0 / TARGET_FPS) * 1000000000);

    private static final String NETPLAY_DEFAULT_HOST = "localhost";
    private static final int NETPLAY_DEFAULT_PORT = 6502;

    private NES nes;
    private NESRunnerThread nesRunnerThread;

    private Socket netplaySocket;
    private boolean isNetplayServer;
    private NetplayServerWaitForConnectionThread netplayServerThread;

    public static final AtomicBoolean shouldSendSerializedNES =
            new AtomicBoolean(false);
    public static final AtomicBoolean shouldReset = new AtomicBoolean(false);
    public static final AtomicBoolean shouldSwitchCartridge =
            new AtomicBoolean(false);

    private final ScreenPanel screenPanel;

    public MainFrame() {
        try {
            UIManager.setLookAndFeel(new FlatDarkLaf());
        } catch (UnsupportedLookAndFeelException e) {
            System.err.println("Failed to initialize theme; using fallback");
        }
        nes = null;
        screenPanel = new ScreenPanel();
        initComponents();
        setAutoRequestFocus(true);
        screenPanel.init();
    }

    /* NESRunnerThread runs the emulator and, when a netplay connection is active
     * (i. e. netplaySocket != null), handles the communication with the peer.
     *
     * As for the netplay architecture, sending the whole screen image from the
     * server to the client each frame is too slow, so a distributed approach is
     * used instead: both client and server have a running emulator.
     *
     * If both instances of the emulator are equal AND they receive the same button
     * presses at the same time, they will stay synchronized. To achieve this,
     * both hosts send the states of their respective buttons on each frame, and
     * wait to receive those of their peer before progressing to the next frame.
     * Although this is vulnerable to network delays, it ensures that both
     * emulators will stay synched at all times.
     *
     * Note that when first establishing a connection and when the server switches
     * the running cartridge, the NES instance of the server is serialized and
     * sent to the client over the socket.
     */
    private class NESRunnerThread extends Thread {
        @Override
        public void run() {
            final boolean isPlayerOne = netplaySocket == null || isNetplayServer;
            if (isPlayerOne)
                nes.reset();
            else {
                netplayReceiveMessage(isPlayerOne, false);
                if (nes == null)
                    return;
            }
            boolean sendResetMessage = false;
            while (!Thread.currentThread().isInterrupted()) {
                if (shouldSwitchCartridge.compareAndSet(true, false)) {
                    boolean switched = loadROM(false);
                    if (switched && netplaySocket != null)
                        shouldSendSerializedNES.set(true);
                }
                if (shouldSendSerializedNES.compareAndSet(true, false))
                    netplaySendSerializedNES();
                if (shouldReset.compareAndSet(true, false)) {
                    nes.reset();
                    sendResetMessage = true;
                }
                long frameStartTime = System.nanoTime(), frameEndTime;
                nes.controller.commitButtonStates(isPlayerOne);
                if (netplaySocket != null) {
                    try {
                        if (sendResetMessage && isNetplayServer) {
                            netplaySendResetMessage();
                            sendResetMessage = false;
                        }
                        netplaySendButtonStates(isPlayerOne);
                        netplayReceiveMessage(isPlayerOne, true);
                    } catch (IOException ex) {
                        showConnectionClosedMessage();
                    }
                }
                nes.runUntilFrameReady(screenPanel.img);
                repaint();
                do {
                    frameEndTime = System.nanoTime();
                } while (frameEndTime - frameStartTime < NANOSECS_PER_FRAME);
            }
        }

        private void netplaySendSerializedNES() {
            try {
                final DataOutputStream out =
                        new DataOutputStream(netplaySocket.getOutputStream());
                out.writeUTF("SYNC");
                out.flush();
                (new ObjectOutputStream(netplaySocket.getOutputStream()))
                        .writeObject(nes);
            } catch (IOException ex) {
                Logger.getLogger(MainFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        }

        private void netplayReceiveSerializedNES() {
            try {
                nes = (NES)(new ObjectInputStream(netplaySocket.getInputStream()))
                        .readObject();
            } catch (IOException | ClassNotFoundException ex) {
                Logger.getLogger(MainFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
        }

        private void netplaySendResetMessage() throws IOException {
            final DataOutputStream out =
                    new DataOutputStream(netplaySocket.getOutputStream());
            out.writeUTF("RESET");
            out.flush();
        }

        private void netplaySendButtonStates(boolean isPlayerOne) throws IOException {
            final DataOutputStream out =
                    new DataOutputStream(netplaySocket.getOutputStream());
            out.writeUTF("BUTTONS " + nes.controller.getNetplayButtonStatesMessage(isPlayerOne));
            out.flush();
        }

        private void netplayReceiveMessage(boolean isPlayerOne,
                boolean waitForButtonStatesMessage) {
            try {
                final DataInputStream in =
                        new DataInputStream(netplaySocket.getInputStream());
                boolean buttonsMessageReceived = false;
                do {
                    String words[] = in.readUTF().split(" ");
                    switch (words[0]) {
                        case "RESET":
                            nes.reset();
                            break;
                        case "SYNC":
                            netplayReceiveSerializedNES();
                            break;
                        default:
                            nes.controller
                                    .processNetplayButtonStatesMessage(words[1], isPlayerOne);
                            buttonsMessageReceived = true;
                    }
                } while (!buttonsMessageReceived && waitForButtonStatesMessage);
            } catch (IOException ex) {
                showConnectionClosedMessage();
            }
        }

        private void showConnectionClosedMessage() {
            try {
                netplaySocket.close();
            } catch (IOException ex) {
                Logger.getLogger(MainFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
            statusBarLabel.setText("Connection closed by " +
                    (isNetplayServer ? "client" : "server"));
            netplaySocket = null;
            loadROMMenuItem.setEnabled(true);
            resetMenuItem.setEnabled(true);
        }
    }

    private class NetplayServerWaitForConnectionThread extends Thread {
        private final int portNumber;
        private ServerSocket serverSocket;

        public NetplayServerWaitForConnectionThread(int portNumber) {
            this.portNumber = portNumber;
        }

        @Override
        public void interrupt() {
            attemptClosingServerSocket();
            super.interrupt();
        }

        @Override
        public void run() {
            try {
                serverSocket = new ServerSocket(portNumber);
                statusBarLabel.setText("Netplay server started; waiting for connections");
                netplaySocket = serverSocket.accept();
                statusBarLabel.setText("Accepted connection from "
                    + netplaySocket.getInetAddress() + ":" + netplaySocket.getPort());
                isNetplayServer = true;
                shouldSendSerializedNES.set(true);
            } catch (IOException ex) {
                if (!isInterrupted())
                    JOptionPane.showMessageDialog(null, "Could not start netplay server " +
                            "(" + ex.getLocalizedMessage() + ").", "Netplay server error",
                            JOptionPane.ERROR_MESSAGE);
            } finally {
                attemptClosingServerSocket();
            }
        }

        private void attemptClosingServerSocket() {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (IOException ex) {

            }
        }
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        jPanel1 = screenPanel;
        statusBarLabel = new javax.swing.JLabel();
        menuBar = new javax.swing.JMenuBar();
        systemMenu = new javax.swing.JMenu();
        loadROMMenuItem = new javax.swing.JMenuItem();
        resetMenuItem = new javax.swing.JMenuItem();
        exitMenuItem = new javax.swing.JMenuItem();
        helpMenu = new javax.swing.JMenu();
        emulatorHelpMenuItem = new javax.swing.JMenuItem();
        netplayHelpMenuItem = new javax.swing.JMenuItem();
        netplayMenu = new javax.swing.JMenu();
        localServerMenu = new javax.swing.JMenu();
        startServerMenuItem = new javax.swing.JMenuItem();
        stopServerMenuItem = new javax.swing.JMenuItem();
        connectToServerMenuItem = new javax.swing.JMenuItem();
        disconnectFromServerMenuItem = new javax.swing.JMenuItem();

        setDefaultCloseOperation(javax.swing.WindowConstants.EXIT_ON_CLOSE);
        setTitle("NESemu");
        setAutoRequestFocus(false);
        setCursor(new java.awt.Cursor(java.awt.Cursor.DEFAULT_CURSOR));
        setFont(new java.awt.Font("Fira Code SemiBold", 0, 10)); // NOI18N
        addKeyListener(new java.awt.event.KeyAdapter() {
            public void keyPressed(java.awt.event.KeyEvent evt) {
                formKeyPressed(evt);
            }
            public void keyReleased(java.awt.event.KeyEvent evt) {
                formKeyReleased(evt);
            }
        });

        javax.swing.GroupLayout jPanel1Layout = new javax.swing.GroupLayout(jPanel1);
        jPanel1.setLayout(jPanel1Layout);
        jPanel1Layout.setHorizontalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 512, Short.MAX_VALUE)
        );
        jPanel1Layout.setVerticalGroup(
            jPanel1Layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 480, Short.MAX_VALUE)
        );

        statusBarLabel.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        statusBarLabel.setText("Load a ROM to play with System -> Load ROM.");

        menuBar.setFont(new java.awt.Font("Fira Code Medium", 0, 13)); // NOI18N

        systemMenu.setText("System");

        loadROMMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        loadROMMenuItem.setText("Load ROM");
        loadROMMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                loadROMMenuItemActionPerformed(evt);
            }
        });
        systemMenu.add(loadROMMenuItem);

        resetMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        resetMenuItem.setText("Reset");
        resetMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                resetMenuItemActionPerformed(evt);
            }
        });
        systemMenu.add(resetMenuItem);

        exitMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        exitMenuItem.setText("Exit");
        exitMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                exitMenuItemActionPerformed(evt);
            }
        });
        systemMenu.add(exitMenuItem);

        menuBar.add(systemMenu);

        helpMenu.setText("Help");

        emulatorHelpMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        emulatorHelpMenuItem.setText("Emulator");
        emulatorHelpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                emulatorHelpMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(emulatorHelpMenuItem);

        netplayHelpMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        netplayHelpMenuItem.setText("Netplay");
        netplayHelpMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                netplayHelpMenuItemActionPerformed(evt);
            }
        });
        helpMenu.add(netplayHelpMenuItem);

        menuBar.add(helpMenu);

        netplayMenu.setText("Netplay");

        localServerMenu.setText("Local server");
        localServerMenu.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N

        startServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        startServerMenuItem.setText("Start");
        startServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                startServerMenuItemActionPerformed(evt);
            }
        });
        localServerMenu.add(startServerMenuItem);

        stopServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        stopServerMenuItem.setText("Stop");
        stopServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                stopServerMenuItemActionPerformed(evt);
            }
        });
        localServerMenu.add(stopServerMenuItem);

        netplayMenu.add(localServerMenu);

        connectToServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        connectToServerMenuItem.setText("Connect to server");
        connectToServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                connectToServerMenuItemActionPerformed(evt);
            }
        });
        netplayMenu.add(connectToServerMenuItem);

        disconnectFromServerMenuItem.setFont(new java.awt.Font("Fira Code", 0, 13)); // NOI18N
        disconnectFromServerMenuItem.setText("Disconnect from server");
        disconnectFromServerMenuItem.addActionListener(new java.awt.event.ActionListener() {
            public void actionPerformed(java.awt.event.ActionEvent evt) {
                disconnectFromServerMenuItemActionPerformed(evt);
            }
        });
        netplayMenu.add(disconnectFromServerMenuItem);

        menuBar.add(netplayMenu);

        setJMenuBar(menuBar);

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(getContentPane());
        getContentPane().setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addGroup(layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
                    .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                    .addComponent(statusBarLabel))
                .addContainerGap())
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGroup(layout.createSequentialGroup()
                .addContainerGap()
                .addComponent(jPanel1, javax.swing.GroupLayout.DEFAULT_SIZE, javax.swing.GroupLayout.DEFAULT_SIZE, Short.MAX_VALUE)
                .addPreferredGap(javax.swing.LayoutStyle.ComponentPlacement.RELATED)
                .addComponent(statusBarLabel)
                .addContainerGap())
        );

        pack();
    }// </editor-fold>//GEN-END:initComponents

    private void formKeyPressed(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyPressed
        Controller.Button button;
        if (nes == null || (button =
                Controller.Button.fromKeyCode(evt.getKeyCode())) == null)
            return;
        button.isPressedLocally = true;
    }//GEN-LAST:event_formKeyPressed

    private void formKeyReleased(java.awt.event.KeyEvent evt) {//GEN-FIRST:event_formKeyReleased
        Controller.Button button;
        if (nes == null || (button =
                Controller.Button.fromKeyCode(evt.getKeyCode())) == null)
            return;
        button.isPressedLocally = false;
    }//GEN-LAST:event_formKeyReleased

    private void resetMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_resetMenuItemActionPerformed
        if (nes == null)
            return;
        int result = JOptionPane.showConfirmDialog(null,
                "Are you sure you want to reset the system?", "Reset",
                JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION)
            shouldReset.set(true);
    }//GEN-LAST:event_resetMenuItemActionPerformed

    private void exitMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_exitMenuItemActionPerformed
        int result = JOptionPane.showConfirmDialog(null,
                    "Are you sure you want to exit?", "Exit",
                    JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION)
            System.exit(0);
    }//GEN-LAST:event_exitMenuItemActionPerformed

    private void loadROMMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_loadROMMenuItemActionPerformed
        if (nes == null)
            loadROM(true);
        else
            shouldSwitchCartridge.set(true);
    }//GEN-LAST:event_loadROMMenuItemActionPerformed

    private boolean loadROM(boolean isFirstLoadedROM) {
        JFileChooser fileChooser = new JFileChooser();
        FileNameExtensionFilter filter = new FileNameExtensionFilter(
                "iNES cartridge files (.nes)", "nes");
        fileChooser.setFileFilter(filter);
        int result = fileChooser.showOpenDialog(null);
        if (result != JFileChooser.APPROVE_OPTION)
            return false;
        String fileName = fileChooser.getSelectedFile().getName();
        String filePath = fileChooser.getSelectedFile().getAbsolutePath();
        try {
            if (isFirstLoadedROM) {
                nesRunnerThread = new NESRunnerThread();
                nes = new NES(filePath);
                nesRunnerThread.start();
            } else
                nes.exchangeCartridge(filePath);
            statusBarLabel.setText("Running \"" + nes.cartridge.getName() + "\"");
            return true;
        } catch (IOException ex) {
            Logger.getLogger(this.getClass().getName())
                    .log(Level.SEVERE, null, ex);
        } catch (UnsupportedMapperException ex) {
            JOptionPane.showMessageDialog(null,
                    "The mapper associated to \"" + fileName + "\" (iNES mapper number " +
                    ex.mapperNumber + ") is not supported yet.",
                    "Unsupported mapper", JOptionPane.ERROR_MESSAGE);
        } catch (IllegalArgumentException ex) {
            JOptionPane.showMessageDialog(null, "\"" + fileName +
                    "\" is not a valid cartridge file.", "Invalid cartridge file",
                    JOptionPane.ERROR_MESSAGE);
        }
        return false;
    }

    private class HostnameAndPortInputPanel extends JPanel {
        private final JTextField hostnameField;
        private final JFormattedTextField portNumberField;

        public HostnameAndPortInputPanel(boolean portNumberOnly) {
            NumberFormat format = NumberFormat.getInstance();
            format.setGroupingUsed(false);
            NumberFormatter formatter = new NumberFormatter(format);
            formatter.setMinimum(0);
            formatter.setMaximum(Integer.MAX_VALUE);
            formatter.setValueClass(Integer.class);
            formatter.setAllowsInvalid(true);
            GridLayout layout = new GridLayout(portNumberOnly ? 1 : 2, 2);
            layout.setHgap(20);
            layout.setVgap(10);
            setLayout(layout);
            hostnameField = new JTextField(NETPLAY_DEFAULT_HOST);
            if (!portNumberOnly) {
                add(new JLabel("Server's hostname or IP address:"));
                add(hostnameField);
            }
            add(new JLabel("Port number:"));
            portNumberField = new JFormattedTextField(formatter);
            portNumberField.setValue(NETPLAY_DEFAULT_PORT);
            add(portNumberField);
        }

        public String getHostname() {
            return hostnameField.getText();
        }

        public int getPortNumber() {
            return Integer.parseInt(portNumberField.getText());
        }
    }

    private void startServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_startServerMenuItemActionPerformed
        HostnameAndPortInputPanel inputPanel = new HostnameAndPortInputPanel(true);
        int result = JOptionPane.showConfirmDialog(null, inputPanel,
                "Start netplay server", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
            return;
        netplayServerThread =
                new NetplayServerWaitForConnectionThread(inputPanel.getPortNumber());
        netplayServerThread.start();
    }//GEN-LAST:event_startServerMenuItemActionPerformed

    private void stopServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_stopServerMenuItemActionPerformed
        if (netplayServerThread == null && netplaySocket == null)
            return;
        statusBarLabel.setText("Netplay server stopped");
        if (netplayServerThread != null && netplayServerThread.isAlive())
            netplayServerThread.interrupt();
        if (netplaySocket != null)
            try {
                netplaySocket.close();
                netplaySocket = null;
            } catch (IOException ex) {
                Logger.getLogger(MainFrame.class.getName())
                        .log(Level.SEVERE, null, ex);
            }
    }//GEN-LAST:event_stopServerMenuItemActionPerformed

    private void connectToServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_connectToServerMenuItemActionPerformed
        HostnameAndPortInputPanel inputPanel = new HostnameAndPortInputPanel(false);
        int result = JOptionPane.showConfirmDialog(null, inputPanel,
                "Netplay server connection", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.QUESTION_MESSAGE);
        if (result != JOptionPane.OK_OPTION)
            return;
        String hostname = inputPanel.getHostname();
        int portNumber = inputPanel.getPortNumber();
        if (netplayServerThread != null && netplayServerThread.isAlive())
            netplayServerThread.interrupt();
        try {
            netplaySocket = new Socket(hostname, portNumber);
            statusBarLabel.setText("Successfully connected to server");
            loadROMMenuItem.setEnabled(false);
            resetMenuItem.setEnabled(false);
            isNetplayServer = false;
            if (nes != null)
                nesRunnerThread.interrupt();
            nesRunnerThread = new NESRunnerThread();
            nesRunnerThread.start();
        } catch (IOException ex) {
            JOptionPane.showMessageDialog(null, "Could not connect to server " +
                    "(" + ex.getLocalizedMessage() + ").", "Connection error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }//GEN-LAST:event_connectToServerMenuItemActionPerformed

    private void disconnectFromServerMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_disconnectFromServerMenuItemActionPerformed
        if (netplaySocket == null || isNetplayServer)
            return;
        try {
            netplaySocket.close();
            netplaySocket = null;
            statusBarLabel.setText("Disconnected from server");
            loadROMMenuItem.setEnabled(true);
            resetMenuItem.setEnabled(true);
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
    }//GEN-LAST:event_disconnectFromServerMenuItemActionPerformed

    private void emulatorHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_emulatorHelpMenuItemActionPerformed
        showHelpHTMLFile("/resources/EmulatorHelp.html", "Emulator help");
    }//GEN-LAST:event_emulatorHelpMenuItemActionPerformed

    private void netplayHelpMenuItemActionPerformed(java.awt.event.ActionEvent evt) {//GEN-FIRST:event_netplayHelpMenuItemActionPerformed
        showHelpHTMLFile("/resources/NetplayHelp.html", "Netplay help");
    }//GEN-LAST:event_netplayHelpMenuItemActionPerformed

    private void showHelpHTMLFile(String fileName, String helpWindowTitle) {
        final JTextPane helpPane = new JTextPane();
        helpPane.setEditable(false);
        helpPane.setContentType("text/html");
        final InputStream in = MainFrame.class.getResourceAsStream(fileName);
        try {
            helpPane.read(in, null);
        } catch (IOException ex) {
            Logger.getLogger(MainFrame.class.getName())
                    .log(Level.SEVERE, null, ex);
        }
        JOptionPane.showMessageDialog(null, helpPane,
                helpWindowTitle, JOptionPane.INFORMATION_MESSAGE);
    }

    public static void main(String args[]) throws IOException {
        MainFrame mf = new MainFrame();
        mf.setLocationRelativeTo(null);
        mf.setVisible(true);
        mf.getContentPane().setBackground(new Color(60, 63, 65));
        mf.toFront();
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    private javax.swing.JMenuItem connectToServerMenuItem;
    private javax.swing.JMenuItem disconnectFromServerMenuItem;
    private javax.swing.JMenuItem emulatorHelpMenuItem;
    private javax.swing.JMenuItem exitMenuItem;
    private javax.swing.JMenu helpMenu;
    private javax.swing.JPanel jPanel1;
    private javax.swing.JMenuItem loadROMMenuItem;
    private javax.swing.JMenu localServerMenu;
    private javax.swing.JMenuBar menuBar;
    private javax.swing.JMenuItem netplayHelpMenuItem;
    private javax.swing.JMenu netplayMenu;
    private javax.swing.JMenuItem resetMenuItem;
    private javax.swing.JMenuItem startServerMenuItem;
    private javax.swing.JLabel statusBarLabel;
    private javax.swing.JMenuItem stopServerMenuItem;
    private javax.swing.JMenu systemMenu;
    // End of variables declaration//GEN-END:variables
}
