package nesemu;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class ScreenPanel extends javax.swing.JPanel {
    private static final int PIXEL_SIZE = 2;

    BufferedImage img;

    public ScreenPanel() {
        initComponents();
    }

    @Override
    public void paint(Graphics g) {
        g.drawImage(img, 0, 0, null);
    }

    @SuppressWarnings("unchecked")
    // <editor-fold defaultstate="collapsed" desc="Generated Code">//GEN-BEGIN:initComponents
    private void initComponents() {

        javax.swing.GroupLayout layout = new javax.swing.GroupLayout(this);
        this.setLayout(layout);
        layout.setHorizontalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 400, Short.MAX_VALUE)
        );
        layout.setVerticalGroup(
            layout.createParallelGroup(javax.swing.GroupLayout.Alignment.LEADING)
            .addGap(0, 300, Short.MAX_VALUE)
        );
    }// </editor-fold>//GEN-END:initComponents

    void init() {
        final int width = getWidth();
        final int height = getHeight();
        img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
    }

    void renderFrameBuffer(Color[][] frameBuffer) {
        Graphics2D g2 = img.createGraphics();
        for (int row = 0; row < 240; row++)
            for (int column = 0; column < 256; column++) {
                g2.setColor(frameBuffer[row][column]);
                g2.fillRect(column * PIXEL_SIZE, row * PIXEL_SIZE, PIXEL_SIZE, PIXEL_SIZE);
            }
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
