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
import com.opencsv.CSVReader;
import com.opencsv.CSVWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
//import java.util.Arrays;
import java.util.List;

import static georgiou.thesis.FFT.fft;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestConvertTimeSeriesFiletoSequenceFileWithSAX;

public class MainActivity extends AppCompatActivity implements DataClient.OnDataChangedListener, View.OnClickListener {

    private final static String TAG = "MobileMainActivity";

    private float[] values = new float[3];
    private TextView coords;
    private TextView packetView;
    private TextView currentAlgo;
    private Button newGesture;
    private Button d3Button;
    private Button fftButton;
    private Button saxButton;
    //private Chronometer chrono;
    //private long timeStamp;
    //private long diff;
    //private long startTime;
    //private long startTimeAfterPause;
    private int recordedCount = 0;
    private int receivedCount = 1;
    //private boolean flag = false;
    private volatile boolean start = false;
    //private long pauseOffset;
    private int gesturesComplete = 0;
    private int r;

    BluetoothAdapter mBluetoothAdapter;
    String datapath = "/data_path";

    float[][] dataSet = new float[120][90];
    Complex[][] fftDataset = new Complex[120][64];

    int fftCell = 0;
    Complex[] row = new Complex[64];

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT ACCELEROMETER DATA

    public static String baseDir = Environment.getExternalStoragePublicDirectory("/DCIM").getAbsolutePath();
    String fileName = "AnalysisData.csv";
    String filePath = baseDir + File.separator + fileName;
    File f = new File(filePath);
    CSVWriter writer;

    /////////////////////////////////////////////////////////////////////////////////////////////ACC DATA END

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT FFT DATA IN STRING

    String fileNameFFT = "FFTAnalysisData.csv";
    String filePathFFT = baseDir + File.separator + fileNameFFT;
    File fFFT = new File(filePathFFT);

    /////////////////////////////////////////////////////////////////////////////////////////////FFT DATA STRING END

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT FFT DATA IN FLOAT

    String fileNameFFTfloat = "FFTAnalysisDataFloat.csv";
    String filePathFFTfloat = baseDir + File.separator + fileNameFFTfloat;
    File fFFTfloat = new File(filePathFFTfloat);

    /////////////////////////////////////////////////////////////////////////////////////////////FFT DATA FLOAT END

    /////////////////////////////////////////////////////////////////////////////////////////////IMPORT TRANSPOSED DATA

    String fileNameRead = "TransposedAnalysisData.csv";
    String filePathRead = baseDir + File.separator + fileNameRead;
    //File fRead = new File(filePathRead);
    CSVReader reader;

    /////////////////////////////////////////////////////////////////////////////////////////////IMPORT END
    List<Float> gestureValuesBufferX = new ArrayList<>();
    List<Float> gestureValuesBufferY = new ArrayList<>();
    List<Float> gestureValuesBufferZ = new ArrayList<>();
    List<Float> gestureGeneralBuffer = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "OnCreate ");

        coords = (TextView) findViewById(R.id.coords);
        packetView = (TextView) findViewById(R.id.packets);
        currentAlgo = (TextView) findViewById(R.id.currentAlgo);
        newGesture = (Button) findViewById(R.id.buttonZeroValue);
        d3Button = (Button) findViewById(R.id.d3);
        fftButton = (Button) findViewById(R.id.fft);
        saxButton = (Button) findViewById(R.id.sax);
        //chrono = (Chronometer) findViewById(R.id.timer);
        //chrono.setFormat("Total Recording Time - %s");

        newGesture.setOnClickListener(this);
        newGesture.setEnabled(false);

        d3Button.setOnClickListener(this);
        fftButton.setOnClickListener(this);
        saxButton.setOnClickListener(this);


        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Wearable.getDataClient(this).addListener(this);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }
        String[] args = new String[0];
        initializeFromCSV();

        try {
            MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(args);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonZeroValue:
                if (!start) {
                    //chrono.setBase(SystemClock.elapsedRealtime() - pauseOffset);
                    //chrono.start();
                    exportDataToCSV(new float[]{30.00f, 30.00f, 30.00f}, filePath, f/*, 30*/);
                    start = true;
                } else {
                    Toast.makeText(this, "Already Running!", Toast.LENGTH_LONG).show();
                }
                break;

            case R.id.d3:
                currentAlgo.setText(R.string.current_algorithm_d3);
                break;

            case R.id.fft:
                currentAlgo.setText(R.string.current_algorithm_fft);
                tryFFT();
                break;

            case R.id.sax:
                currentAlgo.setText(R.string.current_algorithm_sax);
                break;

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
                    //timeStamp = dataMapItem.getDataMap().getLong("ValueTimestamp");
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
                            //chrono.stop();
                            //pauseOffset = SystemClock.elapsedRealtime() - chrono.getBase();
                            exportDataToCSV(new float[] {-30.00f, -30.00f, -30.00f}, filePath, f/*, -30*/);
                            start = false;
                            gesturesComplete++;

                        }
                        else{
                            logthis();
                            dataBuffer();
                            exportDataToCSV(values,filePath,f/*, timeStamp*/);
                        }
                    }
                    else{
                        if(!start) {
                            //Log.d(TAG, "onDataChanged: RECEIVING BUT NOT RECORDING MATE --> Received Count:" + receivedCount);
                            logthis();
                            newGesture.setEnabled(true);
                            //if(receivedCount > 200) newGesture.setEnabled(true);
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

    public void dataBuffer(){

        //Log.d(TAG, "\ndataBuffer: SIZE:" + gestureValuesBuffer.size());

        if(gestureValuesBufferX.size() == 30 && gestureValuesBufferY.size() == 30 && gestureValuesBufferZ.size() == 30) {

            Log.d(TAG, "\ndataBuffer: GENERAL SIZE BEFORE CLEAR:" + gestureGeneralBuffer.size());

            if(gestureGeneralBuffer.size() >= 180){ gestureGeneralBuffer.clear(); Log.d(TAG, "\ndataBuffer: SIZE AFTER CLEAR:" + gestureGeneralBuffer.size()); }

                gestureGeneralBuffer.addAll(0, gestureValuesBufferX);
                gestureGeneralBuffer.addAll(30, gestureValuesBufferY);
                gestureGeneralBuffer.addAll(60, gestureValuesBufferZ);

            Log.d(TAG, "\ndataBuffer: SIZEs BEFORE CLEAR (X, Y, Z):" + gestureValuesBufferX.size() + gestureValuesBufferY.size() + gestureValuesBufferZ.size());

            gestureValuesBufferX.clear();
            gestureValuesBufferY.clear();
            gestureValuesBufferZ.clear();

            Log.d(TAG, "\ndataBuffer: SIZEs AFTER CLEAR (X, Y, Z):" + gestureValuesBufferX.size() + gestureValuesBufferY.size() + gestureValuesBufferZ.size());
        }


        gestureValuesBufferX.add(values[0]);
        gestureValuesBufferY.add(values[1]);
        gestureValuesBufferZ.add(values[2]);
    }

    public void tryFFT(){
        Log.d(TAG, "tryFFT");
        int count = 0;
        for(int i = 0; i < 120; i++){
            r = 0;
            for(int j = 0; j<90; j++){
                if(( count == 2 || count == 5) && j != 14 && j != 44 && j != 74 && j != 83 ){
                    count=0;
                    continue;
                }

                row[r] = new Complex(dataSet[i][j],0);
                r++;

                count++;
            }
            fft(row);
            for(int k = 0; k<64; k++){
                fftDataset[i][k] = row[k];
            }
        }
        Log.d(TAG, "********************************************************************************************************r = " + r);
        exportDataToCSV(new float[]{1},filePathFFT, fFFT);
        exportDataToCSV(new float[]{},filePathFFTfloat, fFFTfloat);
    }

    public void initializeFromCSV(){
        try{
            Log.d(TAG, "initializeFromCSV: *************************\n*************************\n*************************\n*************************\n*************************\n");
            FileReader read = new FileReader(filePathRead);
            reader = new CSVReader(read);
            String[] lines;
            int j = 0;
            int i = 0;
            String[] linesOut = reader.readNext();
            while((lines = reader.readNext()) != null){
                for(String current : lines){
                    if(j==90)
                        break;
                    dataSet[i][j] = Float.parseFloat(current);


                    //Log.d("","" + dataSet[i][j]);
                    j++;

                }
                //Log.d("", "************\n");
                j = 0;
                i++;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void exportDataToCSV(float[] accData, String fp, File FL/*, long stamp*/){

        try{
        if(FL.exists()&&!FL.isDirectory())
        {

            FileWriter mFileWriter = new FileWriter(fp, true);
            writer = new CSVWriter(mFileWriter);
        }
        else
        {
                writer = new CSVWriter(new FileWriter(fp));
        }

        if(accData.length == 3) {
            String[] val = new String[accData.length];
            val[0] = Float.toString(accData[0]);
            val[1] = Float.toString(accData[1]);
            val[2] = Float.toString(accData[2]);
            //val[3] = Long.toString(stamp);

            recordedCount++;
            //Log.d(TAG, baseDir+"\nonEXPORT: "+val[0]+" "+val[1]+" "+val[2]+" "+val[3] + " --> Recorded Count: " + recordedCount);

            writer.writeNext(val);
        }
        else {
            String[] val = new String[64];
            for(int j = 0; j< fftDataset.length; j++){
                for(int k = 0; k<fftDataset[0].length; k++){
                    if(accData.length == 1) val[k] = fftDataset[j][k].toStringZ();
                    else val[k] = Float.toString((float)(fftDataset[j][k].abs()/64.0));
                }
                writer.writeNext(val);
            }
            //Log.d("fftExport",  row[fftCell].toStringZ() + "\tCell: " + (fftCell+1));
        }

        //writer.writeNext(new String[] {"0","0","0","0"});

            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
