package bubbleSort;

import javax.swing.*;
import java.awt.*;
import java.util.Random;
import javax.sound.sampled.*;
import java.lang.reflect.InvocationTargetException;

public class BubbleSortVisualizer extends JPanel {

    private int[] array;
    private int highlightedIndex = -1;
    private boolean sortedPhase = false;

    private final int panelWidth;
    private final int panelHeight;
    private final int delayMs = 0;

    private SourceDataLine audioLine;
    private final float sampleRate = 44100f;

    public BubbleSortVisualizer(int width, int height, int size) {
        this.panelWidth = width;
        this.panelHeight = height;

        setPreferredSize(new Dimension(width, height));
        setBackground(Color.BLACK);

        array = new int[size];
        fillRandomArray();
        setupAudio();
    }

    private void fillRandomArray() {
        Random rand = new Random();
        for (int i = 0; i < array.length; i++) {
            array[i] = rand.nextInt(array.length - 10) + 10;
        }
    }

    private void setupAudio() {
        try {
            AudioFormat format = new AudioFormat(sampleRate, 16, 1, true, false);
            audioLine = AudioSystem.getSourceDataLine(format);
            audioLine.open(format, 4096);
            audioLine.start();
        } catch (Exception e) {
            audioLine = null;
        }
    }

    private void paintNow() {
        try {
            SwingUtilities.invokeAndWait(() -> paintImmediately(0, 0, getWidth(), getHeight()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        }
    }

    public void startSort() {
        Thread sortThread = new Thread(() -> {
            try {
                bubbleSort();

                // Final green sweep synced with one rising "woooop"
                sortedPhase = true;
                playSweepToneWithVisual(400, 2600, 260);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                if (audioLine != null) {
                    audioLine.drain();
                    audioLine.stop();
                    audioLine.close();
                }
            }
        });

        sortThread.start();
    }

    private void bubbleSort() throws InterruptedException {
        for (int i = 0; i < array.length - 1; i++) {
            for (int j = 0; j < array.length - 1 - i; j++) {
                highlightedIndex = j;
                paintNow();

                if (array[j] > array[j + 1]) {
                    int temp = array[j];
                    array[j] = array[j + 1];
                    array[j + 1] = temp;

                    animateSwap(j, j + 1);
                } else {
                    playTone(valueToFrequency(array[j]), 4);
                    Thread.sleep(delayMs);
                }
            }
        }
    }

    private void animateSwap(int index1, int index2) throws InterruptedException {
        highlightedIndex = index2;
        paintNow();

        int avgValue = (array[index1] + array[index2]) / 2;
        playTone(valueToFrequency(avgValue), 6);

        Thread.sleep(delayMs);
    }

    private int valueToFrequency(int value) {
        int minFreq = 250;
        int maxFreq = 1800;

        int minVal = 10;
        int maxVal = array.length;

        double normalized = (double) (value - minVal) / (maxVal - minVal);
        return (int) (minFreq + normalized * (maxFreq - minFreq));
    }

    private void playTone(int hz, int durationMs) {
        if (audioLine == null) {
            return;
        }

        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        byte[] buffer = new byte[numSamples * 2];

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / numSamples;
            double angle = 2.0 * Math.PI * i * hz / sampleRate;

            double envelope;
            if (t < 0.2) {
                envelope = t / 0.2;
            } else {
                envelope = 1.0 - (t - 0.2) / 0.8;
            }

            envelope = Math.max(0, envelope);

            short sample = (short) (Math.sin(angle) * 8000 * envelope);

            buffer[2 * i] = (byte) (sample & 0xff);
            buffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);
        }

        audioLine.write(buffer, 0, buffer.length);
    }

    private void playSweepToneWithVisual(int startHz, int endHz, int durationMs) {
        if (audioLine == null) {
            return;
        }

        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        byte[] fullBuffer = new byte[numSamples * 2];

        double phase = 0.0;
        int lastHighlighted = -1;

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / numSamples;

            int currentHighlight = Math.min(array.length - 1, (int) (t * array.length));
            if (currentHighlight != lastHighlighted) {
                highlightedIndex = currentHighlight;
                paintNow();
                lastHighlighted = currentHighlight;
            }

            double curvedT = t * t;
            double hz = startHz + (endHz - startHz) * curvedT;

            hz += 20 * Math.sin(2 * Math.PI * 8 * t);

            double envelope;
            if (t < 0.08) {
                envelope = t / 0.08;
            } else if (t > 0.85) {
                envelope = (1.0 - t) / 0.15;
            } else {
                envelope = 1.0;
            }

            envelope = Math.max(0, envelope);

            phase += 2.0 * Math.PI * hz / sampleRate;
            short sample = (short) (Math.sin(phase) * 15000 * envelope);

            fullBuffer[2 * i] = (byte) (sample & 0xff);
            fullBuffer[2 * i + 1] = (byte) ((sample >> 8) & 0xff);
        }

        audioLine.write(fullBuffer, 0, fullBuffer.length);

        highlightedIndex = array.length - 1;
        paintNow();
    }

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        for (int i = 0; i < array.length; i++) {
            int x1 = (int) ((double) i * panelWidth / array.length);
            int x2 = (int) ((double) (i + 1) * panelWidth / array.length);
            int barWidth = x2 - x1;

            int barHeight = (int) (((double) array[i] / array.length) * (panelHeight - 20));
            int y = panelHeight - barHeight;

            if (sortedPhase) {
                if (i <= highlightedIndex) {
                    g.setColor(Color.GREEN);
                } else {
                    g.setColor(Color.WHITE);
                }
            } else {
                if (i == highlightedIndex || i == highlightedIndex + 1) {
                    g.setColor(Color.RED);
                } else {
                    g.setColor(Color.WHITE);
                }
            }

            g.fillRect(x1, y, barWidth, barHeight);
        }
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Bubble Sort Visualizer + Sound");
            BubbleSortVisualizer panel = new BubbleSortVisualizer(1400, 700, 400);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            panel.startSort();
        });
    }
}
