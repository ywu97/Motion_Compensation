import java.awt.*;
import java.awt.image.*;
import java.io.*;
import java.util.*;
import javax.swing.*;
import java.util.List;
import java.util.ArrayList;

// To Run this program
// $ javac VideoPrediction.java
// $ java java VideoPrediction /Path of frameN.rgb/ /Path of frameN+1.rgb/ k
// Note: k must be an integer between 1 and 32
// Output will be two image: One is the prediction Y-Channel FrameN+1;
// The other is the Y-Channel error difference between predictionFrame and targetFrame

public class VideoPrediction {
    JFrame frame;
    JLabel lbIm1;
    int width = 640;
    int height = 320;
    int k = 0;

    int macroblockSize = 16;

    BufferedImage referenceFrame, targetFrame, predictionFrame;
    BufferedImage ydifferenceFrame;

    double[][] referenceFrameY = new double[height][width];
    double[][] targetFrameY = new double[height][width];
    double[][] predictionFrameY = new double[height][width];

    /**
     * Read Image RGB Reads the image of given width and height at the given imgPath
     * into the provided BufferedImage.
     */
    private void readImageRGB(int width, int height, String imgPath, BufferedImage img) {
        try {
            int frameLength = width * height * 3;

            File file = new File(imgPath);
            RandomAccessFile raf = new RandomAccessFile(file, "r");
            raf.seek(0);

            long len = frameLength;
            byte[] bytes = new byte[(int) len];

            raf.read(bytes);

            int ind = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    byte a = 0;
                    byte r = bytes[ind];
                    byte g = bytes[ind + height * width];
                    byte b = bytes[ind + height * width * 2];

                    int pix = 0xff000000 | ((r & 0xff) << 16) | ((g & 0xff) << 8) | (b & 0xff);
                    // int pix = ((a << 24) + (r << 16) + (g << 8) + b);

                    img.setRGB(x, y, pix);
                    ind++;
                }
            }
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // int testK
    public void showIms(String[] args) throws IOException {

        String param0 = args[0];
        System.out.println("The reference frame n is: " + param0);

        String param1 = args[1];
        System.out.println("The target frame n+1 is: " + param1);

        String param2 = args[2];
        k = Integer.parseInt(args[2]);
//         k = testK;
        System.out.println("The search parameter k is: " + param2);

        // Read in the specified image
        referenceFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, args[0], referenceFrame);

        targetFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        readImageRGB(width, height, args[1], targetFrame);

        predictionFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);

        calculateY(referenceFrame, referenceFrameY);
        calculateY(targetFrame, targetFrameY);

        findBlock();

        ydifferenceFrame = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        calculateYDifference();// predictionFrameY with targetFrameY

        jframeImg(predictionFrame);
        jframeImg(ydifferenceFrame);

    }

    private void jframeImg(BufferedImage img) {
        // Use label to display the image
        frame = new JFrame();
        GridBagLayout gLayout = new GridBagLayout();
        frame.getContentPane().setLayout(gLayout);

        lbIm1 = new JLabel(new ImageIcon(img));

        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.anchor = GridBagConstraints.CENTER;
        c.weightx = 0.5;
        c.gridx = 0;
        c.gridy = 0;

        c.fill = GridBagConstraints.HORIZONTAL;
        c.gridx = 0;
        c.gridy = 1;
        frame.getContentPane().add(lbIm1, c);

        frame.pack();
        frame.setVisible(true);
    }

    private void calculateY(BufferedImage inputRGB, double[][] outputYMatrix) {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int rgb = inputRGB.getRGB(x, y);
                double r = (rgb >> 16) & 0xff;
                double g = (rgb >> 8) & 0xff;
                double b = (rgb) & 0xff;
                double yValue = 0.299 * r + 0.587 * g + 0.114 * b;
                outputYMatrix[y][x] = yValue;
            }
        }
    }

    private void findBlock() {
        for (int row = 0; row < height; row += macroblockSize) {
            for (int col = 0; col < width; col += macroblockSize) {
                List<Integer> coordinate = new ArrayList<>();
                coordinate = matchBlock(row, col);
                int x = coordinate.get(0);
                int y = coordinate.get(1);
                for (int i = 0; i < macroblockSize; i++) {
                    for (int j = 0; j < macroblockSize; j++) {
                        // check
                        // if (y + i >= 0 && y + i < height && x + j >= 0 && x + j < width && col + j <
                        // width && row + i < height) {
                        predictionFrameY[row + i][col + j] = referenceFrameY[y + i][x + j];
                        int val = (int) referenceFrameY[y + i][x + j] << 16 | (int) referenceFrameY[y + i][x + j] << 8
                                | (int) referenceFrameY[y + i][x + j];
                        predictionFrame.setRGB(col + j, row + i, val);
                        // }
                    }
                }
            }
        }
    }

    private List<Integer> matchBlock(int y, int x) {
        double minMAD = Integer.MAX_VALUE;
        List<Integer> coord = new ArrayList<>();
        coord.add(0);
        coord.add(1);
        for (int n = y - k; n < y + k; n++) {
            for (int m = x - k; m < x + k; m++) {
                if (n >= 0 && n + macroblockSize < height && m >= 0 && m + macroblockSize < width) {
                    double currentMAD = calculateMAD(m, n, x, y);
                    if (currentMAD < minMAD) {
                        minMAD = currentMAD;
                        coord.set(0, m);
                        coord.set(1, n);
                    }
                }
            }
        }
        return coord;
    }

    private double calculateMAD(int referX, int referY, int targetX, int targetY) {
        double currentMAD = 0;
        for (int i = 0; i < macroblockSize; i++) {
            for (int j = 0; j < macroblockSize; j++) {
                currentMAD += Math
                        .abs(targetFrameY[targetY + i][targetX + j] - referenceFrameY[referY + i][referX + j]);
            }
        }
        return currentMAD;

    }

    public void calculateYDifference() {
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                double diff = Math.abs(targetFrameY[y][x] - predictionFrameY[y][x]);
                int pixel = (int) diff << 16 | (int) diff << 8 | (int) diff;
                ydifferenceFrame.setRGB(x, y, pixel);
            }
        }
    }

    public static void main(String[] args) throws IOException {
//         for (int i = 1; i <= 32; i++) {
//         VideoPrediction imgcomp = new VideoPrediction();
//         imgcomp.showIms(args, i);
//         }
        VideoPrediction imgcomp = new VideoPrediction();
        imgcomp.showIms(args);
    }
}