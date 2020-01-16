package com.example.remotecar;

import androidx.appcompat.app.AppCompatActivity;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.androidplot.Plot;
import com.androidplot.util.PixelUtils;
import com.androidplot.util.Redrawer;
import com.androidplot.xy.BarFormatter;
import com.androidplot.xy.BoundaryMode;
import com.androidplot.xy.CatmullRomInterpolator;
import com.androidplot.xy.LineAndPointFormatter;
import com.androidplot.xy.SimpleXYSeries;
import com.androidplot.xy.StepMode;
import com.androidplot.xy.XYGraphWidget;
import com.androidplot.xy.XYPlot;
import com.androidplot.xy.XYSeries;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Array;
import java.nio.charset.StandardCharsets;
import java.text.DecimalFormat;
import java.text.FieldPosition;
import java.text.Format;
import java.text.ParsePosition;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import io.github.controlwear.virtual.joystick.android.JoystickView;

public class MainActivity extends AppCompatActivity {
    //private final String DEVICE_ADDRESS="20:13:10:15:33:66";
    private final String DEVICE_ADDRESS = "30:14:12:02:28:06";
    private final UUID PORT_UUID = UUID.fromString("00001101-0000-1000-8000-00805f9b34fb");//Serial Port Service ID
    private BluetoothDevice device;
    private BluetoothSocket socket;
    private OutputStream outputStream;
    private InputStream inputStream;
    Button startButton, sendButton, clearButton, stopButton;
    TextView textView;
    EditText editText;
    JoystickView joystick;
    boolean deviceConnected = false;
    Thread thread;
    byte[] buffer;
    int bufferPosition;
    boolean stopThread;
    private String prevDirection;
    private long prevDirectionTime;
    int globalAngle = 0;
    int globalStrength = 0;
    private XYPlot plot;

    private final static int MAX_ACC_LENGHT = 15;

    private SimpleXYSeries xHistorySeries = null;
    private SimpleXYSeries yHistorySeries = null;
    private SimpleXYSeries zHistorySeries = null;
    private Redrawer redrawer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        startButton = findViewById(R.id.buttonStart);
        sendButton = findViewById(R.id.buttonSend);
        clearButton = findViewById(R.id.buttonClear);
        stopButton = findViewById(R.id.buttonStop);
        editText = findViewById(R.id.editText);
        textView = findViewById(R.id.textView);
        joystick = findViewById(R.id.joystickView);
        plot = findViewById(R.id.plot);

        xHistorySeries = new SimpleXYSeries("Az.");
        xHistorySeries.useImplicitXVals();
        yHistorySeries = new SimpleXYSeries("Pitch");
        yHistorySeries.useImplicitXVals();
        zHistorySeries = new SimpleXYSeries("Roll");
        zHistorySeries.useImplicitXVals();

        plot.setRangeBoundaries(-180, 359, BoundaryMode.FIXED);
        plot.setDomainBoundaries(0, MAX_ACC_LENGHT, BoundaryMode.FIXED);
        plot.addSeries(xHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 100, 200), null, null, null));
        plot.addSeries(yHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(100, 200, 100), null, null, null));
        plot.addSeries(zHistorySeries,
                new LineAndPointFormatter(
                        Color.rgb(200, 100, 100), null, null, null));
        plot.setDomainStepMode(StepMode.INCREMENT_BY_VAL);
        plot.setDomainStepValue(MAX_ACC_LENGHT / 10);
        plot.setLinesPerRangeLabel(3);
        plot.setDomainLabel("Sample Index");
        plot.getDomainTitle().pack();
        plot.setRangeLabel("Angle (Degs)");
        plot.getRangeTitle().pack();

        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.LEFT).
                setFormat(new DecimalFormat("#"));

        plot.getGraph().getLineLabelStyle(XYGraphWidget.Edge.BOTTOM).
                setFormat(new DecimalFormat("#"));
        redrawer = new Redrawer(
                Arrays.asList(new Plot[]{plot}),
                100, false);

        setUiEnabled(false);
    }

    private void onNewAccValues(int x, int y, int z) {
        xHistorySeries.addFirst(null, x);
        yHistorySeries.addFirst(null, y);
        zHistorySeries.addFirst(null, z);
    }

    @Override
    public void onResume() {
        super.onResume();
        redrawer.start();
    }

    @Override
    public void onPause() {
        redrawer.pause();
        super.onPause();
    }

    @Override
    public void onDestroy() {
        redrawer.finish();
        super.onDestroy();
    }

    private void handleAccMessage(String msg) {
        Log.d("handleAccMessage", "msg: " + msg);
        boolean isAcc = msg.startsWith("x=");
        if (isAcc) {
            try {
                String[] accs = msg.split(",");
                int[] iAccs = new int[3];
                for (int i = 0; i < 3; ++i) {
                    String acc = accs[i];

                    boolean isAccX = acc.contains("x=");
                    boolean isAccY = acc.contains("y=");


                    int accValue = Integer.parseInt(
                            acc.replace("x=", "")
                                    .replace("y=", "")
                                    .replace("z=", "")
                                    .replace("\n", "")
                                    .replace(",", "")
                                    .replace(" ", "")
                    );
                    iAccs[i] = accValue;

                }

                onNewAccValues(iAccs[0], iAccs[1], iAccs[2]);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    public void setUiEnabled(boolean bool) {
        startButton.setEnabled(!bool);
        sendButton.setEnabled(bool);
        stopButton.setEnabled(bool);
        textView.setEnabled(bool);
        if (bool) {
            final long DELAY = 500;

            final Handler handler = new Handler();
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {

                    String direction = "d3\n";
                    if (globalStrength < 50) {
                        direction = "d3\n";
                    } else {
                        if (globalAngle >= 45 && globalAngle <= 135) direction = "d1\n";
                        else if (globalAngle > 135 && globalAngle < 225) direction = "d5\n";
                        else if (globalAngle >= 225 && globalAngle <= 315) direction = "d2\n";
                        else if ((globalAngle > 315 && globalAngle <= 360) || (globalAngle >= 0 && globalAngle < 45))
                            direction = "d4\n";
                        else direction = "d5\n";
                    }

                    long time = System.currentTimeMillis();

                    if (!direction.equals(prevDirection) && (time - prevDirectionTime) >= DELAY) {
                        try {
                            outputStream.write(direction.getBytes());
                        } catch (IOException e) {
                            e.printStackTrace();
                        }

                        prevDirection = direction;
                        prevDirectionTime = time;
                    }
                    handler.postDelayed(this, DELAY);

                }
            }, DELAY);
            joystick.setOnMoveListener(new JoystickView.OnMoveListener() {
                @Override
                public void onMove(int angle, int strength) {
                    globalAngle = angle;
                    globalStrength = strength;
                    Log.e("Angle", String.valueOf(angle));
                    Log.e("Strenght", String.valueOf(strength));
                }
            });
        }

    }

    public boolean BTinit() {
        boolean found = false;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter == null) {
            Toast.makeText(getApplicationContext(), "Device doesnt Support Bluetooth", Toast.LENGTH_SHORT).show();
        }
        if (!bluetoothAdapter.isEnabled()) {
            Intent enableAdapter = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableAdapter, 0);
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        Set<BluetoothDevice> bondedDevices = bluetoothAdapter.getBondedDevices();
        if (bondedDevices.isEmpty()) {
            Toast.makeText(getApplicationContext(), "Please Pair the Device first", Toast.LENGTH_SHORT).show();
        } else {
            for (BluetoothDevice iterator : bondedDevices) {
                if (iterator.getAddress().equals(DEVICE_ADDRESS)) {
                    device = iterator;
                    found = true;
                    break;
                }
            }
        }
        return found;
    }

    public boolean BTconnect() {
        boolean connected = true;
        try {
            socket = device.createRfcommSocketToServiceRecord(PORT_UUID);
            socket.connect();
        } catch (IOException e) {
            e.printStackTrace();
            connected = false;
        }
        if (connected) {
            try {
                outputStream = socket.getOutputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }
            try {
                inputStream = socket.getInputStream();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }


        return connected;
    }

    public void onClickStart(View view) {
        if (BTinit()) {
            if (BTconnect()) {
                setUiEnabled(true);
                deviceConnected = true;
                beginListenForData();
                textView.append("\nConnection Opened!\n");
            }

        }
    }

    void beginListenForData() {
        final Handler handler = new Handler();
        stopThread = false;
        buffer = new byte[1024];
        Thread thread = new Thread(new Runnable() {
            public void run() {
                String msg = "";
                while (!Thread.currentThread().isInterrupted() && !stopThread) {
                    try {
                        int byteCount = inputStream.available();
                        if (byteCount > 0) {
                            byte[] rawBytes = new byte[byteCount];
                            inputStream.read(rawBytes);
                            final String string = new String(rawBytes, StandardCharsets.UTF_8);
                            msg = msg.concat(string);

                            if (string.contains("\n")) {
                                handleAccMessage(msg);
                                msg = "";
                            }
                            handler.post(new Runnable() {
                                public void run() {
                                    textView.setText(string);
                                }
                            });

                        }
                    } catch (IOException ex) {
                        stopThread = true;
                    }
                }
            }
        });

        thread.start();
    }

    public void onClickSend(View view) {
        String string = editText.getText().toString();
        string.concat("\n");
        try {
            outputStream.write(string.getBytes());
        } catch (IOException e) {
            e.printStackTrace();
        }
        textView.append("\nSent Data:" + string + "\n");

    }

    public void onClickStop(View view) throws IOException {
        stopThread = true;
        outputStream.close();
        inputStream.close();
        socket.close();
        setUiEnabled(false);
        deviceConnected = false;
        textView.append("\nConnection Closed!\n");
    }

    public void onClickClear(View view) {
        textView.setText("");
    }
}
