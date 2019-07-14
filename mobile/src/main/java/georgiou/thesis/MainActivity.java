package georgiou.thesis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity implements DataClient.OnDataChangedListener{

    private final static String TAG = "MobileMainActivity";

    private float[] values = new float[3];
    private TextView coords;
    private TextView packetView;
    private long timeStamp;
    private long diff;
    private long startTime;
    private long startTimeAfterPause;
    private int expectedCount = 0;
    private int receivedCount = 0;
    private boolean flag = false;

    BluetoothAdapter mBluetoothAdapter;
    String datapath = "/data_path";

    /////////////////////////////////////////////////////////////////////////////////////////////

    String baseDir = Environment.getExternalStoragePublicDirectory("/DCIM").getAbsolutePath();
    String fileName = "AnalysisData.csv";
    String filePath = baseDir + File.separator + fileName;
    File f = new File(filePath);
    CSVWriter writer;

    /////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "OnCreate--> StartTime: " + startTime);

        coords = (TextView) findViewById(R.id.coords);
        packetView = (TextView) findViewById(R.id.packets);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Wearable.getDataClient(this).addListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        }

    @Override
    public void onResume(){
        Log.d(TAG, "onResume");
        Wearable.getDataClient(this).addListener(this);
        super.onResume();
    }

    @Override
    public void onPause() {
        Log.d(TAG, "OnPause");
        super.onPause();
    }

    @Override
    public void onStop(){
        Wearable.getDataClient(this).removeListener(this);
        Log.d(TAG, "OnStop");
        super.onStop();
    }

    @Override
    public void onDestroy(){
        Wearable.getDataClient(this).removeListener(this);
        Log.d(TAG, "OnDestroy");
        super.onDestroy();
    }


    public void logthis(){
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                    coords.setText("X: "+ values[0] + "\nY: " + values[1] + "\nZ: " + values[2] + "\n Stamp: " + timeStamp);
                    if(flag==true) {

                        expectedCount = (int) ((timeStamp - startTime) / 300);
                        Log.d(TAG, "expectedCount: " + expectedCount);
                    }
                    packetView.setText("Expected/Received: " + expectedCount + "/" + receivedCount);
            }
        });
    }


    @Override
    public void onDataChanged (@NonNull DataEventBuffer dataEventBuffer) {

        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                String path = event.getDataItem().getUri().getPath();
                if (path.equals(datapath)) {
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());
                    values = dataMapItem.getDataMap().getFloatArray("SensorValues");
                    timeStamp = dataMapItem.getDataMap().getLong("ValueTimestamp");
                    if(values!=null) {
                        //Log.d(TAG, "X: "+ values[0] + " Y: " + values[1] + " Z: " + values[2]);
                        diff = timeStamp - System.currentTimeMillis();
                        if(flag == false) startTime = System.currentTimeMillis();
                        flag = true;

                        Log.d(TAG, "\nonDataChanged:"+"\nX: "+ values[0] + "\nY: " + values[1] +
                                "\nZ: " + values[2] + "\n WatchStamp: " + timeStamp +
                                "\nMobileStamp: " + System.currentTimeMillis() + "\nDiff: " + diff + " ns\n");
                        logthis();
                        receivedCount++;
                        //exportDataToCSV();
                    }
                    else{
                        Log.d(TAG, "onDataChanged: SHIT HAPPENS");
                    }
                } else {
                    Log.e(TAG, "Unrecognized path: " + path);
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.v(TAG, "Data deleted : " + event.getDataItem().toString());
            } else {
                Log.e(TAG, "Unknown data event Type = " + event.getType());
            }
        }
        Log.d(TAG, "onDataChanged: EXITING THIS SHIT");
    }

    public void exportDataToCSV(){

        try{
        if(f.exists()&&!f.isDirectory())
        {

            FileWriter mFileWriter = new FileWriter(filePath, true);
            writer = new CSVWriter(mFileWriter);
        }
        else
        {
                writer = new CSVWriter(new FileWriter(filePath));
        }

        String[] val = new String[4];
        val[0] = Float.toString(values[0]);
        val[1] = Float.toString(values[1]);
        val[2] = Float.toString(values[2]);
        val[3] = Long.toString(timeStamp);

        Log.d(TAG, baseDir+"\nonEXPORT:"+val[0]+" "+val[1]+" "+val[2]+" "+val[3]);

        writer.writeNext(val);


            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
