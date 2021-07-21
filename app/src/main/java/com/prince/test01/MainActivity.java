package com.prince.test01;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.Message;
import android.os.PersistableBundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.GridLabelRenderer;
import com.jjoe64.graphview.LegendRenderer;
import com.jjoe64.graphview.helper.StaticLabelsFormatter;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import static android.content.ContentValues.TAG;

import static android.icu.lang.UCharacter.IndicPositionalCategory.BOTTOM_AND_RIGHT;
import static android.icu.lang.UCharacter.IndicPositionalCategory.TOP;
import static android.view.View.VISIBLE;
import static androidx.constraintlayout.widget.ConstraintProperties.LEFT;
import static androidx.constraintlayout.widget.ConstraintProperties.BOTTOM;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    //HC08 CHARACTERISTICS UUID
    String address = "50:65:83:86:B5:BB";
    String deviceName = "HC-08";
    UUID customServiceUUID = UUID.fromString("0000FFE0-0000-1000-8000-00805F9B34FB");
    UUID customCharacteristicsUUID = UUID.fromString("0000FFE1-0000-1000-8000-00805F9B34FB");

    private static final long SCAN_PERIOD = 2000;
    public BluetoothAdapter mBluetoothAdapter;
    public BluetoothGatt mBluetoothGatt;
    BluetoothDevice mBluetoothDevice;
    BluetoothGattService mBluetoothGattService;
    BluetoothGattCharacteristic mBluetoothGattCharacteristic;
    private Handler mHandler;
    private int rssi_update = 0;
    public boolean mTimerEnabled = false;
    private Handler mTimerHandler = new Handler(Looper.getMainLooper());
    private Handler mGraphRassi = new Handler(Looper.getMainLooper());
    private Handler mGraphMov = new Handler(Looper.getMainLooper());
    private Handler mGraphKal = new Handler(Looper.getMainLooper());

    public boolean mConnected = false;
    public boolean mScanning = false;
    int windowsize = 1;
    private final int PLOTTING = 0;

    EditText etMovingWindow;
    TextView mtvdisplay1;
    TextView mtvdisplay2;
    TextView mtvdisplay3;
    TextView mtvdisplay4;

    String[] Permission = {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"};

    GraphView graph;
    LineGraphSeries<DataPoint> seriesExactRSSI = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> seriesMovingRSSI = new LineGraphSeries<DataPoint>();
    LineGraphSeries<DataPoint> seriesKalmanRSSI = new LineGraphSeries<DataPoint>();
    double x = 0, y_exact, y_moving, y_kalman;

    MovingAverage mMovingAverage = new MovingAverage(windowsize);

    private static final double KALMAN_R = 0.125d;
    private static final double KALMAN_Q = 0.5d;
    private KalmanFilter mKalmanFilter = new KalmanFilter(KALMAN_R, KALMAN_Q); // init Kalman Filter

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_scan).setOnClickListener(this);
        findViewById(R.id.button_stop).setOnClickListener(this);
        etMovingWindow = findViewById(R.id.et_moving_window);
        mtvdisplay1 = findViewById(R.id.tv_display1);
        mtvdisplay2 = findViewById(R.id.tv_display2);
        mtvdisplay3 = findViewById(R.id.tv_display3);
        mtvdisplay4 = findViewById(R.id.tv_display4);

        if (!hasPermissions(this, Permission)) {
            ActivityCompat.requestPermissions(this, Permission, 4);
        }
        // Check if the current mobile phone supports ble Bluetooth, if not, exit the program
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        // Initializes Bluetooth adapter.

        mHandler = new Handler(getApplicationContext().getMainLooper());
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }

        mBluetoothAdapter = ((BluetoothManager) Objects.requireNonNull(getSystemService(BLUETOOTH_SERVICE))).getAdapter();
        if (this.mBluetoothAdapter == null) {
            Toast.makeText(this, "BLE not supported", Toast.LENGTH_SHORT).show();
            finish();
        }
        Log.i(TAG, "Current Thread On create " + Thread.currentThread().getId());

        graph = (GraphView) findViewById(R.id.graph1);
        // styling grid/labels
        //graph.getGridLabelRenderer().setGridColor(Color.RED);
        //graph.getGridLabelRenderer().setHighlightZeroLines(false);
        //graph.getGridLabelRenderer().setHorizontalLabelsColor(Color.GREEN);
        //graph.getGridLabelRenderer().setVerticalLabelsColor(Color.RED);
        //graph.getGridLabelRenderer().setVerticalLabelsVAlign(GridLabelRenderer.VerticalLabelsVAlign.ABOVE);
        //graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.HORIZONTAL);
        //graph.getGridLabelRenderer().reloadStyles();

        // styling viewport
        //graph.getViewport().setBackgroundColor(Color.argb(255, 222, 222, 222));
        //graph.getViewport().setDrawBorder(true);
        //graph.getViewport().setBorderColor(Color.RED);

        // styling grid/labels
        graph.getGridLabelRenderer().setHighlightZeroLines(false);
        graph.getGridLabelRenderer().setVerticalLabelsAlign(Paint.Align.LEFT);
        graph.getGridLabelRenderer().setLabelVerticalWidth(50);
        graph.getGridLabelRenderer().setTextSize(30);
        graph.getGridLabelRenderer().setGridStyle(GridLabelRenderer.GridStyle.BOTH);
        //graph.getGridLabelRenderer().setHorizontalLabelsAngle(120);
        graph.getGridLabelRenderer().reloadStyles();

        // styling series
        seriesExactRSSI.setTitle("Exact RSSI");
        seriesExactRSSI.setColor(Color.RED);
        seriesExactRSSI.setDrawDataPoints(true);
        seriesExactRSSI.setDataPointsRadius(8);
        seriesExactRSSI.setThickness(5);

        seriesMovingRSSI.setTitle("Moving RSSI");
        seriesMovingRSSI.setColor(Color.GREEN);
        seriesMovingRSSI.setDrawDataPoints(true);
        seriesMovingRSSI.setDataPointsRadius(8);
        seriesMovingRSSI.setThickness(5);

        seriesKalmanRSSI.setTitle("Kalman RSSI");
        seriesKalmanRSSI.setColor(Color.BLUE);
        seriesKalmanRSSI.setDrawDataPoints(true);
        seriesKalmanRSSI.setDataPointsRadius(8);
        seriesKalmanRSSI.setThickness(5);

        // styling legend
        graph.getLegendRenderer().setVisible(true);
        graph.getLegendRenderer().setTextSize(35);
        graph.getLegendRenderer().setBackgroundColor(Color.argb(150, 50, 0, 0));
        graph.getLegendRenderer().setTextColor(Color.WHITE);
        //graph.getLegendRenderer().setAlign(LegendRenderer.LegendAlign.BOTTOM);
        //graph.getLegendRenderer().setMargin(10);
        graph.getLegendRenderer().setFixedPosition(700, 1100);

        graph.addSeries(seriesExactRSSI);
        graph.addSeries(seriesMovingRSSI);
        graph.addSeries(seriesKalmanRSSI);

        graph.setTitle("RSSI Plot in dB");
        graph.setTitleTextSize(50);

        // set manual X,Y bounds
        graph.getViewport().setXAxisBoundsManual(true);
        graph.getViewport().setMinX(10);
        graph.getViewport().setMaxX(20);
        graph.getViewport().setYAxisBoundsManual(true);
        graph.getViewport().setMinY(-100);
        graph.getViewport().setMaxY(-20);

        // enable scrolling
        graph.getViewport().setScrollable(true);


//        // use static labels for horizontal and vertical labels
//        StaticLabelsFormatter staticLabelsFormatter = new StaticLabelsFormatter(graph);
//        staticLabelsFormatter.setHorizontalLabels(new String[] {"old", "middle", "new"});
//        staticLabelsFormatter.setVerticalLabels(new String[] {"low", "middle", "high"});
//        graph.getGridLabelRenderer().setLabelFormatter(staticLabelsFormatter);

        mHandler = new Handler(getApplicationContext().getMainLooper()) {
            @Override
            public void handleMessage(Message inputMessage) {
                switch (inputMessage.what) {
                    case PLOTTING:
                        mtvdisplay1.setText(String.format("Exact  RSSI:%sdB", String.valueOf(rssi_update)));
                        mtvdisplay2.setText(String.format("Moving RSSI:%s", String.valueOf(mMovingAverage.next(rssi_update) + "dB")));
                        mtvdisplay3.setText(String.format("Kalman RSSI:%s", String.valueOf((int) mKalmanFilter.applyFilter(rssi_update) + "dB")));
                        plotting();
                        break;
                }
            }
        };

    }

    public void BLEScan(boolean scan) {
        if (scan) {
            mScanning = true;
            Log.i(TAG, "Discovering Our Module");
            List<ScanFilter> scanFilters = new ArrayList<ScanFilter>();
            ScanFilter scanFilter = new ScanFilter.Builder()
                    .setDeviceName(deviceName)
                    .setDeviceAddress(address)
                    .build();
            scanFilters.add(scanFilter);
            ScanSettings scanSettings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .setReportDelay(0L)
                    .build();

            new Handler(Looper.getMainLooper()).post(new Runnable() {
                @Override
                public void run() {
                    Log.d(TAG, "I am the UI thread");
                    mBluetoothAdapter.getBluetoothLeScanner().startScan(scanFilters, scanSettings, mLeScanCallback);
                }
            });
        } else {
            mScanning = false;
            mBluetoothAdapter.getBluetoothLeScanner().stopScan(mLeScanCallback);
        }
    }

    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.i(TAG, "Found a device ==> " + result.toString());
            ScanRecord mScanRecord = result.getScanRecord();
            if (mScanRecord != null) {
                rssi_update = result.getRssi();
                mHandler.sendEmptyMessage(PLOTTING);
                mBluetoothDevice = result.getDevice();
                // attempt to connect here
                mBluetoothDevice.connectGatt(getApplicationContext(), true, mBluetoothGattCallback);
            } else {
                Log.i(TAG, "Not any device found");
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.e(TAG, "Error Scanning [" + errorCode + "]");
        }
    };

    private final BluetoothGattCallback mBluetoothGattCallback = new BluetoothGattCallback() {
        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            mConnected = true;
            mBluetoothGatt = gatt;
            Log.i(TAG, "OnServiceDiscovered [" + status + "] " + gatt.toString());
            mBluetoothGattService = gatt.getService(customServiceUUID);
            if (mBluetoothGattService != null) {
                Log.i(TAG, "Got HC08 Custom Service ");
                mBluetoothGattCharacteristic = mBluetoothGattService.getCharacteristic(customCharacteristicsUUID);
                if (mBluetoothGattCharacteristic != null) {
                    mBluetoothGatt.setCharacteristicNotification(mBluetoothGattCharacteristic, true);
                    Log.i(TAG, "Got HC08 Custom Service Characteristics");
                    readPeriodicalyRssiValue(true);
                    mHandler.sendEmptyMessage(PLOTTING);
                } else {
                    Log.i(TAG, "Not Got HC08 Custom Service Characteristics");
                }
            } else {
                Log.i(TAG, "Not Got HC08 Custom Service ");
            }
        }

        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i(TAG, "onConnectionStatChange [" + status + "][" + newState + "]");
            if (status == BluetoothGatt.GATT_SUCCESS && newState == BluetoothGatt.STATE_CONNECTED) {
                Log.i(TAG, "Connected to [" + gatt.toString() + "]");
                gatt.discoverServices();
                mConnected = true;
            }
            if (newState == BluetoothGatt.STATE_DISCONNECTED && !mScanning) {
                Log.i(TAG, "Disconnected");
                mConnected = false;
            }
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "characteristic written [" + status + "][" + characteristic.getStringValue(0) + "]");
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic) {

        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, int status) {
            Log.i(TAG, "characteristic read [" + characteristic.getUuid() + "] [" + characteristic.getStringValue(0) + "]");
        }

        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status) {
            try {
                Log.i(TAG, "onDescriptorRead status is [" + status + "]");
                Log.i(TAG, "descriptor read [" + descriptor.getCharacteristic().getUuid() + "]");
                Log.i(TAG, "descriptor value is [" + new String(descriptor.getValue(), "UTF-8") + "]");
            } catch (Exception e) {
                Log.e(TAG, "Error reading descriptor " + Arrays.toString(e.getStackTrace()));
            }
        }

        @Override
        public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
            super.onReadRemoteRssi(gatt, rssi, status);
            rssi_update = rssi;
            mHandler.sendEmptyMessage(PLOTTING);
        }

    };

    public void showMessage(String message) {
        Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
        Log.i(getClass().getName(), message);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_scan:
                windowsize = Integer.parseInt(etMovingWindow.getText().toString());
                if (windowsize > 0)
                    mMovingAverage.setWindowSize(windowsize);
                else
                    mMovingAverage.setWindowSize(1);

                if (!mScanning)
                    BLEScan(true);
                break;
            case R.id.button_stop:
                BLEScan(false);
                mBluetoothGatt.disconnect();
                mBluetoothGatt.close();
                break;
        }
    }

    public static boolean hasPermissions(Context context, String... strArr) {
        if (!(Build.VERSION.SDK_INT < 23 || context == null || strArr == null)) {
            for (String checkSelfPermission : strArr) {
                if (ActivityCompat.checkSelfPermission(context, checkSelfPermission) != 0) {
                    return false;
                }
            }
        }
        return true;
    }

    //moving average for RSSI data
    static class MovingAverage {
        double sum;
        int windowSize;
        LinkedList<Integer> list;

        /**
         * Initialize your data structure here.
         */
        public MovingAverage(int windowSize) {
            this.list = new LinkedList<>();
            this.windowSize = windowSize;
        }

        public int next(int val) {
            sum += val;
            list.offer(val);
            if (list.size() <= windowSize) {
                return (int) sum / list.size();
            }
            sum -= list.poll();
            return (int) sum / windowSize;
        }

        public void setWindowSize(int window) {
            this.windowSize = window;
        }
    }

    //Kalman's filter
    private static class KalmanFilter implements Serializable {

        private double R;   //  Process Noise
        private double Q;   //  Measurement Noise
        private double A;   //  State Vector
        private double B;   //  Control Vector
        private double C;   //  Measurement Vector

        private Double x;   //  Filtered Measurement Value (No Noise)
        private double cov; //  Covariance

        public KalmanFilter(double r, double q, double a, double b, double c) {
            R = r;
            Q = q;
            A = a;
            B = b;
            C = c;
        }

        public KalmanFilter(double r, double q) {
            R = r;
            Q = q;
            A = 1;
            B = 0;
            C = 1;
        }

        /**
         * Public Methods
         **/
        public double applyFilter(double rssi) {
            return applyFilter(rssi, 0.0d);
        }

        /**
         * Filters a measurement
         *
         * @param measurement The measurement value to be filtered
         * @param u           The controlled input value
         * @return The filtered value
         */
        public double applyFilter(double measurement, double u) {
            double predX;           //  Predicted Measurement Value
            double K;               //  Kalman Gain
            double predCov;         //  Predicted Covariance
            if (x == null) {
                x = (1 / C) * measurement;
                cov = (1 / C) * Q * (1 / C);
            } else {
                predX = predictValue(u);
                predCov = getUncertainty();
                K = predCov * C * (1 / ((C * predCov * C) + Q));
                x = predX + K * (measurement - (C * predX));
                cov = predCov - (K * C * predCov);
            }
            return x;
        }

        /**
         * Private Methods
         **/
        private double predictValue(double control) {
            return (A * x) + (B * control);
        }

        private double getUncertainty() {
            return ((A * cov) * A) + R;
        }

        @Override
        public String toString() {
            return "KalmanFilter{" +
                    "R=" + R +
                    ", Q=" + Q +
                    ", A=" + A +
                    ", B=" + B +
                    ", C=" + C +
                    ", x=" + x +
                    ", cov=" + cov +
                    '}';
        }
    }

    //Hide the keyboard when touched outside the focused area.
    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (getCurrentFocus() != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(getCurrentFocus().getWindowToken(), 0);
        }
        return super.dispatchTouchEvent(ev);
    }

    public void readPeriodicalyRssiValue(boolean status) {
        this.mTimerEnabled = status;
        if (!mConnected || !mTimerEnabled) {
            mTimerEnabled = false;
        } else {
            mTimerHandler.postDelayed(new Runnable() {
                public void run() {
                    if (mTimerEnabled) {
                        ReadRSSI();
                    }
                    readPeriodicalyRssiValue(mTimerEnabled);
                }
            }, (long) 100);
        }
    }

    public void ReadRSSI() {
        if (mBluetoothGatt != null) {
            mBluetoothGatt.readRemoteRssi();
        }
    }

    void plotting() {
        x = x + 0.5;
        y_exact = rssi_update;
        y_moving = mMovingAverage.next(rssi_update);
        y_kalman = mKalmanFilter.applyFilter(rssi_update);

        if ((y_exact) < -50)
            mtvdisplay4.setText("Away");
        else
            mtvdisplay4.setText("Near");

        seriesExactRSSI.appendData(new DataPoint(x, y_exact), true, 10000);
        seriesMovingRSSI.appendData(new DataPoint(x, y_moving), true, 10000);
        seriesKalmanRSSI.appendData(new DataPoint(x, y_kalman), true, 10000);
        new Handler().postDelayed(new Runnable() {
            public void run() {
                graph.addSeries(seriesExactRSSI);
                graph.addSeries(seriesMovingRSSI);
                graph.addSeries(seriesKalmanRSSI);
            }
        }, 50);

    }

}