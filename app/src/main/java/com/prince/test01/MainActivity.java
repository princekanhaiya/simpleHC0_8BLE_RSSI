package com.prince.test01;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanRecord;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.RunnableFuture;

import static android.content.ContentValues.TAG;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {
    private static final long SCAN_PERIOD = 2000 ;
    public BluetoothAdapter mBluetoothAdapter;
    private Handler mHandler;
    private  int rssi_update=0;
    String[] Permission = {"android.permission.ACCESS_COARSE_LOCATION", "android.permission.WRITE_EXTERNAL_STORAGE", "android.permission.READ_EXTERNAL_STORAGE"};

    MovingAverage mMovingAverage=new MovingAverage(5);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.button_scan).setOnClickListener(this);
        findViewById(R.id.button_stop).setOnClickListener(this);
        findViewById(R.id.tv_display).setOnClickListener(this);



        if (!hasPermissions(this, this.Permission)) {
            ActivityCompat.requestPermissions(this, this.Permission, 4);
        }

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
        Log.i(TAG, "Current Thread On create "+Thread.currentThread().getId());

    }

    private final ScanCallback mLeScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            ScanRecord scanRecord = result.getScanRecord();
            if (scanRecord != null) {
                if (scanRecord.getDeviceName() != null && scanRecord.getDeviceName().equals("HC-08")) {
                    //mBluetoothAdapter.getBluetoothLeScanner().stopScan(this);
                    Log.i(TAG, "Found our HC-08!");
                    BluetoothDevice myBLEModuleDevice = result.getDevice();
                    if (myBLEModuleDevice != null) {
                        Log.i("PRINCE", result.toString());
                        rssi_update = result.getRssi();
                        TextView mtvdisplay = findViewById(R.id.tv_display);
                        mtvdisplay.post(new Runnable() {
                            @Override
                            public void run() {
                                mtvdisplay.setText(String.valueOf(rssi_update));
                                mtvdisplay.setText("RSSI: "+String.valueOf(mMovingAverage.next(rssi_update)+"dB"));
                            }
                        });
                    }
                }
            }
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            Log.i("PRINCE", results.toString());
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            Log.i("PRINCE", "Error"+errorCode);
        }
    };


    private void scanLeDevice(final boolean enable) {

        final BluetoothLeScanner bluetoothLeScanner = mBluetoothAdapter.getBluetoothLeScanner();

        if (enable) {
//            // Stops scanning after a pre-defined scan period.
//            mHandler.postDelayed(new Runnable() {
//                @Override
//                public void run() {
//                    Log.i(TAG, "Current Thread on button click "+Thread.currentThread().getId());
//                    bluetoothLeScanner.stopScan(mLeScanCallback);
//                }
//            }, SCAN_PERIOD);
            bluetoothLeScanner.startScan(mLeScanCallback);
        } else {
            bluetoothLeScanner.stopScan(mLeScanCallback);
        }
    }

    public void showMessage(String message) {
        Toast.makeText(getBaseContext(), message, Toast.LENGTH_SHORT).show();
        Log.i(getClass().getName(), message);
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.button_scan:
                showMessage("Scan Button");
                Log.i(TAG, "Current Thread on button click "+Thread.currentThread().getId());
                scanLeDevice(true);
                break;
            case R.id.button_stop:
                showMessage("Stop Button");
                scanLeDevice(false);
                break;
            case R.id.tv_display:
                showMessage("Text View");
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
        /** Initialize your data structure here. */
        public MovingAverage(int windowSize) {
            this.list = new LinkedList<>();
            this.windowSize = windowSize;
        }

        public int next(int val) {
            sum += val;
            list.offer(val);
            if(list.size()<=windowSize){
                return (int) sum/list.size();
            }
            sum -= list.poll();
            return (int) sum/windowSize;
        }
    }
}