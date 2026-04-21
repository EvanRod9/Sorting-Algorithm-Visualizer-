package bogoSort;

import javax.swing.*;
import java.awt.*;
import java.util.Random;
import javax.sound.sampled.*;
import java.lang.reflect.InvocationTargetException;

public class BogoSort extends JPanel {

    private int[] array;
    private int highlightedIndex = -1;
    private boolean sortedPhase = false;

    private final int panelWidth;
    private final int panelHeight;

    // Higher = slower
    private final int delayMs = 200;

    private SourceDataLine audioLine;
    private final float sampleRate = 44100f;

    private final Random rand = new Random();

    public BogoSort(int width, int height, int size) {
        this.panelWidth = width;
        this.panelHeight = height;

        setPreferredSize(new Dimension(width, height));
        setBackground(Color.BLACK);

        array = new int[size];
        fillRandomArray();
        setupAudio();
    }

    private void fillRandomArray() {
        for (int i = 0; i < array.length; i++) {
            array[i] = rand.nextInt(array.length) + 1;
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
                bogoSort();

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

    private void bogoSort() throws InterruptedException {
        while (!isSorted()) {
            shuffleArray();
            Thread.sleep(delayMs);
        }
    }

    private boolean isSorted() throws InterruptedException {
        for (int i = 0; i < array.length - 1; i++) {
            highlightedIndex = i;
            paintNow();

            playCheckTone(array[i]);
            Thread.sleep(delayMs);

            if (array[i] > array[i + 1]) {
                return false;
            }
        }
        return true;
    }

    private void shuffleArray() throws InterruptedException {
        for (int i = array.length - 1; i > 0; i--) {
            int j = rand.nextInt(i + 1);

            highlightedIndex = Math.min(i, j);
            paintNow();

            int temp = array[i];
            array[i] = array[j];
            array[j] = temp;

            int avgValue = (array[i] + array[j]) / 2;
            playShuffleTone(avgValue);

            Thread.sleep(delayMs);
        }
    }

    private int valueToFrequency(int value) {
        int minFreq = 250;
        int maxFreq = 1800;

        int minVal = 1;
        int maxVal = array.length;

        double normalized = (double) (value - minVal) / Math.max(1, (maxVal - minVal));
        return (int) (minFreq + normalized * (maxFreq - minFreq));
    }

    private void playCheckTone(int value) {
        int baseHz = valueToFrequency(value);

        // Smaller randomness for sorted check
        int hz = baseHz + rand.nextInt(121) - 60; // -60 to +60
        int duration = 10 + rand.nextInt(8);      // 10 to 17 ms

        playRandomizedTone(hz, duration, 0.30, 0.12, 7000);
    }

    private void playShuffleTone(int value) {
        int baseHz = valueToFrequency(value);

        // Bigger randomness for chaotic shuffle
        int hz = baseHz + rand.nextInt(401) - 200; // -200 to +200
        int duration = 14 + rand.nextInt(16);      // 14 to 29 ms

        playRandomizedTone(hz, duration, 0.85, 0.35, 12000);
    }

    private void playRandomizedTone(int hz, int durationMs, double vibratoDepth,
                                    double harmonicStrength, int volume) {
        if (audioLine == null) {
            return;
        }

        hz = Math.max(80, hz);

        int numSamples = (int) ((durationMs / 1000.0) * sampleRate);
        byte[] buffer = new byte[numSamples * 2];

        double phase1 = 0.0;
        double phase2 = 0.0;

        double vibratoRate = 12.0 + rand.nextDouble() * 18.0; // 12 to 30 Hz
        double wobbleRate = 4.0 + rand.nextDouble() * 10.0;   // 4 to 14 Hz
        double detune = 1.5 + rand.nextDouble() * 4.0;        // slight extra harmonic detune

        for (int i = 0; i < numSamples; i++) {
            double t = (double) i / numSamples;

            // Quick attack, uneven decay
            double envelope;
            if (t < 0.08) {
                envelope = t / 0.08;
            } else {
                envelope = 1.0 - ((t - 0.08) / 0.92);
            }
            envelope = Math.max(0, envelope);

            // Random-feeling wobble
            double vibrato =
                    1.0
                    + vibratoDepth * 0.04 * Math.sin(2.0 * Math.PI * vibratoRate * t)
                    + vibratoDepth * 0.02 * Math.sin(2.0 * Math.PI * wobbleRate * t);

            double currentHz1 = hz * vibrato;
            double currentHz2 = (hz + detune * 20.0) * (1.0 + 0.02 * Math.sin(2.0 * Math.PI * 9.0 * t));

            phase1 += 2.0 * Math.PI * currentHz1 / sampleRate;
            phase2 += 2.0 * Math.PI * currentHz2 / sampleRate;

            double wave1 = Math.sin(phase1);
            double wave2 = Math.signum(Math.sin(phase2)); // harsher square-ish component

            double mixed = wave1 + harmonicStrength * wave2;

            short sample = (short) (mixed * volume * envelope);

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
            JFrame frame = new JFrame("Bogo Sort Visualizer");
            BogoSort panel = new BogoSort(1400, 700, 10);

            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.add(panel);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);

            panel.startSort();
        });
    }
}
