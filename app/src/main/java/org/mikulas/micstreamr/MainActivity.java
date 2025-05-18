package org.mikulas.micstreamr;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.media.audiofx.NoiseSuppressor;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {

    private EditText ip;
    private EditText port;
    private AudioRecord recorder;
    private File audioFile;
    private String serverIp = "192.168.0.106";
    private int serverPort = 1337;
    private boolean isRecording = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ip = findViewById(R.id.ipField);
        port = findViewById(R.id.portField);
        Button connectButton = findViewById(R.id.connectButton);
        Button recordButton = findViewById(R.id.recordButton);

        checkMicPermission();

        connectButton.setOnClickListener(v -> verifyConnection());

        recordButton.setOnClickListener(v -> {
            if (!isRecording) {
                startRecording();
                recordButton.setText("Stop Recording");
            } else {
                stopRecordingAndSend();
                recordButton.setText("Start Recording");
            }
        });
    }

    // Request mic permission, if it's not already enabled
    private void checkMicPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    // Verify if the address is correct
    private void verifyConnection() {
        AtomicBoolean success = new AtomicBoolean(false);

        // Run the socket on a thread because android doesn't allow sockets on main
        Thread test_connection = getThread(success);

        // Join the thread - you need the result
        try {
            test_connection.join();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        if (success.get()) {
            Toast.makeText(this, "Server set to " + serverIp + ":" + serverPort, Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "Could not connect to the server...", Toast.LENGTH_SHORT).show();
        }
    }

    private @NotNull Thread getThread(AtomicBoolean success) {
        Thread test_connection = new Thread(() -> {
            // Try connecting to the server
            try {
                serverIp = ip.getText().toString();
                serverPort = Integer.parseInt(port.getText().toString());

                Socket socket = new Socket(serverIp, serverPort);
                socket.close();
                success.set(true);
            } catch (IOException | NumberFormatException e) {
                e.printStackTrace();
            }
        });

        test_connection.start();
        return test_connection;
    }

    // Start Recording
    private void startRecording() {
        // Configurations for the recorder
        int sampleRate = 16000;
        int channelConfig = AudioFormat.CHANNEL_IN_MONO;
        int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
        int bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat);

        // Create the recorde
        try {
        recorder = new AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION, // Very important - it's the first filter
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
        );
        } catch (SecurityException e) {
            e.printStackTrace();
            return;
        }

        // Attach noise suppressor if available - second filter
        if (NoiseSuppressor.isAvailable()) {
            System.out.println("Using noise suppressor");
            NoiseSuppressor.create(recorder.getAudioSessionId());
        }

        System.out.println("Noise suppressor: " + NoiseSuppressor.isAvailable());

        System.out.println("Creating temp file");
        // Create temp file
        try {
            File outputDir = getFilesDir();
            audioFile = File.createTempFile("recording_", ".pcm", outputDir);
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }


        // Start the recording thread
        recorder.startRecording();
        isRecording = true;
        new Thread(() -> {
            try (FileOutputStream os = new FileOutputStream(audioFile)) {
                byte[] buffer = new byte[bufferSize];
                while (isRecording) {
                    int read = recorder.read(buffer, 0, buffer.length);
                    if (read > 0) {
                        os.write(buffer, 0, read);
                    }
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Stop the recording if it isn't initialized
        if (recorder.getState() != AudioRecord.STATE_INITIALIZED) {
            System.out.println("[MicStreamr] " + "AudioRecord not initialized!");
            isRecording = false;
            return;
        }

        Toast.makeText(this, "Recording...", Toast.LENGTH_SHORT).show();
    }

    // Stop recording
    private void stopRecordingAndSend() {
        // Stop the recorder
        isRecording = false;
        if (recorder != null) {
            recorder.stop();
            recorder.release();
            recorder = null;
        }

        Toast.makeText(this, "Recording stopped. Sending...", Toast.LENGTH_SHORT).show();

        // Send the file and delete it
        new Thread(() -> {
            try {
                sendFileOverSocket(audioFile);
                audioFile.delete();
                runOnUiThread(() -> Toast.makeText(this, "Sent & Deleted", Toast.LENGTH_SHORT).show());
            } catch (IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> Toast.makeText(this, "Failed to send", Toast.LENGTH_SHORT).show());
            }
        }).start();
    }

    // Send file
    private void sendFileOverSocket(File file) throws IOException {
        // Create socket
        System.out.println("Sending " + file.getName());
        Socket socket = new Socket(serverIp, serverPort);
        OutputStream out = socket.getOutputStream();
        FileInputStream in = new FileInputStream(file);

        // Send data
        byte[] buffer = new byte[4096];
        int read;
        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }

        // Close socket
        in.close();
        out.close();
        socket.close();
    }
}
