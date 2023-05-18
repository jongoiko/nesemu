package nesemu;

import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class ScreenPanel extends javax.swing.JPanel {
    private final static int SCREEN_WIDTH = 256;
    private final static int SCREEN_HEIGHT = 240;
    BufferedImage img;

    public ScreenPanel() {
        initComponents();
    }

    @Override
    public void paint(Graphics g) {
        int width = getWidth(), height = getHeight();
        double ratio = Math.min((double)width / SCREEN_WIDTH,
                (double)height / SCREEN_HEIGHT);
        int targetWidth = (int)(ratio * SCREEN_WIDTH);
        int targetHeight = (int)(ratio * SCREEN_HEIGHT);
        int targetX = (width - targetWidth) / 2, targetY = (height - targetHeight) / 2;
        g.drawImage(img, targetX, targetY, targetWidth, targetHeight, null);
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

    public void init() {
        img = new BufferedImage(SCREEN_WIDTH, SCREEN_HEIGHT,
                BufferedImage.TYPE_INT_ARGB);
    }

    // Variables declaration - do not modify//GEN-BEGIN:variables
    // End of variables declaration//GEN-END:variables
}
