package georgiou.thesis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.Chronometer;
import android.widget.TextView;
import android.widget.Toast;

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
import java.sql.Time;

public class MainActivity extends AppCompatActivity implements DataClient.OnDataChangedListener, View.OnClickListener {

    private final static String TAG = "MobileMainActivity";

    private float[] values = new float[3];
    private TextView coords;
    private TextView packetView;
    private Button newGesture;
    private Chronometer chrono;
    private long timeStamp;
    //private long diff;
    //private long startTime;
    //private long startTimeAfterPause;
    private int recordedCount = 0;
    private int receivedCount = 1;
    //private boolean flag = false;
    private volatile boolean start = false;
    private long pauseOffset;
    private int gesturesComplete = 0;

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
        //Log.d(TAG, "OnCreate--> StartTime: " + startTime);

        coords = (TextView) findViewById(R.id.coords);
        packetView = (TextView) findViewById(R.id.packets);
        newGesture = (Button) findViewById(R.id.buttonZeroValue);
        chrono = (Chronometer) findViewById(R.id.timer);
        chrono.setFormat("Total Recording Time - %s");

        newGesture.setOnClickListener(this);
        newGesture.setEnabled(false);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Wearable.getDataClient(this).addListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        }

    @Override
    public void onClick(View v){
        if(!start){
            chrono.setBase(SystemClock.elapsedRealtime() - pauseOffset);
            chrono.start();
            exportDataToCSV(new float[] {30.00f, 30.00f, 30.00f, 30.00f}, 30);
            start = true;
        }
        else{
            Toast.makeText(this, "Already Running!", Toast.LENGTH_LONG);
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
                    //coords.setText("X: "+ values[0] + "\nY: " + values[1] + "\nZ: " + values[2] + "\n Stamp: " + timeStamp);
                        coords.setText("Recorded Gestures: " + gesturesComplete);
                        packetView.setText("Received/Recorded: " + receivedCount + "/" + recordedCount);
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
                    if(values!=null && start) {
                        //Log.d(TAG, "X: "+ values[0] + " Y: " + values[1] + " Z: " + values[2]);
                        //diff = timeStamp - System.currentTimeMillis();
                        //if(!flag) startTime = System.currentTimeMillis();
                        //flag = true;

                        /*
                        Log.d(TAG, "\nonDataChanged:"+"\nX: "+ values[0] + "\nY: " + values[1] +
                                "\nZ: " + values[2] + "\n WatchStamp: " + timeStamp +
                                "\nMobileStamp: " + System.currentTimeMillis() + "\nDiff: " + diff + " ns\n");
                        */
                        if((recordedCount - gesturesComplete) % 31 == 0){
                            chrono.stop();
                            pauseOffset = SystemClock.elapsedRealtime() - chrono.getBase();
                            exportDataToCSV(new float[] {-30.00f, -30.00f, -30.00f, -30.00f}, -30);
                            start = false;
                            gesturesComplete++;
                        }
                        else{
                            logthis();
                            exportDataToCSV(values, timeStamp);
                        }
                    }
                    else{
                        if(!start) {
                            //Log.d(TAG, "onDataChanged: RECEIVING BUT NOT RECORDING MATE --> Received Count:" + receivedCount);
                            logthis();
                            if(receivedCount > 200) newGesture.setEnabled(true);
                        }
                        else Log.d(TAG, "onDataChanged: SHIT HAPPENS");
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
        receivedCount++;
        //newGesture.setEnabled(false);
        //Log.d(TAG, "onDataChanged: EXITING THIS SHIT");
    }

    public void exportDataToCSV(float[] accData, long stamp){

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
        val[0] = Float.toString(accData[0]);
        val[1] = Float.toString(accData[1]);
        val[2] = Float.toString(accData[2]);
        val[3] = Long.toString(stamp);

        recordedCount++;
        //Log.d(TAG, baseDir+"\nonEXPORT: "+val[0]+" "+val[1]+" "+val[2]+" "+val[3] + " --> Recorded Count: " + recordedCount);

        writer.writeNext(val);

        //writer.writeNext(new String[] {"0","0","0","0"});

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
