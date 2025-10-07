package com.example.voiceshifter;

import android.Manifest;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.AudioTrack;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

public class MainActivity extends AppCompatActivity {

    private static final int SAMPLE_RATE = 44100;
    private static final int BUFFER_SIZE = AudioRecord.getMinBufferSize(
            SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);

    private AudioRecord audioRecord;
    private AudioTrack audioTrack;
    private boolean isRecording = false;
    private Thread recordingThread;
    private VoiceEffect currentEffect = VoiceEffect.NORMAL;

    private Spinner effectSpinner;
    private Button toggleButton;

    private enum VoiceEffect {
        NORMAL("原声", 1.0f, 1.0f),
        GIRL("少女", 1.5f, 1.2f),
        MATURE("御姐", 0.8f, 0.9f),
        LOLI("萝莉", 1.8f, 1.3f),
        MANBO("曼波", 1.2f, 0.7f);

        private String name;
        private float pitch;
        private float speed;

        VoiceEffect(String name, float pitch, float speed) {
            this.name = name;
            this.pitch = pitch;
            this.speed = speed;
        }

        public String getName() { return name; }
        public float getPitch() { return pitch; }
        public float getSpeed() { return speed; }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initializeUI();
        requestPermissions();
    }

    private void initializeUI() {
        effectSpinner = findViewById(R.id.effectSpinner);
        toggleButton = findViewById(R.id.toggleButton);

        ArrayAdapter<String> adapter = new ArrayAdapter<>(
                this, android.R.layout.simple_spinner_item,
                new String[]{"原声", "少女", "御姐", "萝莉", "曼波"});
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        effectSpinner.setAdapter(adapter);

        effectSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                switch (position) {
                    case 0: currentEffect = VoiceEffect.NORMAL; break;
                    case 1: currentEffect = VoiceEffect.GIRL; break;
                    case 2: currentEffect = VoiceEffect.MATURE; break;
                    case 3: currentEffect = VoiceEffect.LOLI; break;
                    case 4: currentEffect = VoiceEffect.MANBO; break;
                }
                Toast.makeText(MainActivity.this, "已选择: " + currentEffect.getName() + "音效", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {}
        });

        toggleButton.setOnClickListener(v -> toggleRecording());
    }

    private void requestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.RECORD_AUDIO}, 1);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "麦克风权限已获取", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "需要麦克风权限才能使用变声功能", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void toggleRecording() {
        if (!isRecording) {
            startRecording();
            toggleButton.setText("停止变声");
        } else {
            stopRecording();
            toggleButton.setText("开始变声");
        }
        isRecording = !isRecording;
    }

    private void startRecording() {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "需要麦克风权限", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            audioRecord = new AudioRecord(MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE);

            audioTrack = new AudioTrack(AudioTrack.MODE_STREAM,
                    SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT, BUFFER_SIZE,
                    AudioTrack.MODE_STREAM);

            audioRecord.startRecording();
            audioTrack.play();

            recordingThread = new Thread(new RecordingRunnable());
            recordingThread.start();

            Toast.makeText(this, "变声已启动 - " + currentEffect.getName(), Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "启动录音失败", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopRecording() {
        try {
            if (audioRecord != null) {
                audioRecord.stop();
                audioRecord.release();
                audioRecord = null;
            }
            if (audioTrack != null) {
                audioTrack.stop();
                audioTrack.release();
                audioTrack = null;
            }
            if (recordingThread != null) {
                recordingThread.interrupt();
                recordingThread = null;
            }
            Toast.makeText(this, "变声已停止", Toast.LENGTH_SHORT).show();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private class RecordingRunnable implements Runnable {
        @Override
        public void run() {
            byte[] buffer = new byte[BUFFER_SIZE];

            while (!Thread.currentThread().isInterrupted() && isRecording) {
                int bytesRead = audioRecord.read(buffer, 0, BUFFER_SIZE);
                if (bytesRead > 0) {
                    byte[] processedBuffer = applyVoiceEffect(buffer, bytesRead);
                    audioTrack.write(processedBuffer, 0, processedBuffer.length);
                }
            }
        }

        private byte[] applyVoiceEffect(byte[] input, int length) {
            byte[] output = new byte[length];

            float pitch = currentEffect.getPitch();
            float speed = currentEffect.getSpeed();

            for (int i = 0; i < length - 1; i += 2) {
                short sample = (short) ((input[i + 1] << 8) | (input[i] & 0xFF));

                sample = (short) (sample * pitch);

                if (sample > Short.MAX_VALUE) sample = Short.MAX_VALUE;
                if (sample < Short.MIN_VALUE) sample = Short.MIN_VALUE;

                int outIndex = (int) (i * speed);
                if (outIndex < length - 1) {
                    output[outIndex] = (byte) (sample & 0xFF);
                    output[outIndex + 1] = (byte) ((sample >> 8) & 0xFF);
                }
            }

            return output;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopRecording();
    }
}
