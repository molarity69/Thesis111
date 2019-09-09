package georgiou.thesis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.View;
import android.widget.Button;
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
import java.io.BufferedWriter;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static georgiou.thesis.FFT.fft;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestConvertTimeSeriesFiletoSequenceFileWithSAX;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestSAX_SingleTimeSeries;
import edu.berkeley.compbio.jlibsvm.kernel.GaussianRBFKernel;

public class MainActivity extends AppCompatActivity implements DataClient.OnDataChangedListener, View.OnClickListener {

    private final static String TAG = "MobileMainActivity"; //Debug TAG

    private float[] values = new float[3]; //Array containing incoming X,Y,Z data from watch

    ///////////////////////////////////////////////////////////////////////////////////////////// LAYOUT COMPONENTS
    private TextView coords;
    private TextView packetView;
    private TextView currentAlgo;
    private Button newGesture;
    private Button d3Button;
    private Button fftButton;
    private Button saxButton;
    private Button trainButton;
    /////////////////////////////////////////////////////////////////////////////////////////////

    private int recordedCount = 0;  //checking recorded data
    private int receivedCount = 1;  //checking received data
    private volatile boolean start = false; //checking if recording has started
    private int gesturesComplete = 0;   //checking total recorded gestures
    private String chosenAlgorithm = null;  //checking which button is pressed

    public BluetoothAdapter mBluetoothAdapter; //bluetooth initializer
    public String datapath = "/data_path"; //path for bluetooth communication

    private float[][] dataSet = new float[120][90]; //array that holds the imported data set from CSV file
    private Complex[][] fftDataset = new Complex[120][64];  //array that holds the imported data set in Complex type


    private Complex[] row = new Complex[64];    //array that helps with copying each imported data row to the data set after transformation
    private Complex[] bufferrow = new Complex[64];  //same as above but for the buffer array that holds data for recognition

    public static String baseDir = Environment.getExternalStoragePublicDirectory("/DCIM").getAbsolutePath();    //path to phone storage folder

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT RAW ACCELEROMETER DATA TO CSV

    String fileName = "AnalysisData.csv";
    String filePath = baseDir + File.separator + fileName;
    File f = new File(filePath);
    CSVWriter writer;   //registering CSVWriter

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT FFT DATA IN STRING TO CSV

    String fileNameFFT = "FFTAnalysisData.csv";
    String filePathFFT = baseDir + File.separator + fileNameFFT;
    File fFFT = new File(filePathFFT);

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT FFT DATA IN FLOAT TO CSV

    String fileNameFFTfloat = "FFTAnalysisDataFloat.csv";
    String filePathFFTfloat = baseDir + File.separator + fileNameFFTfloat;
    File fFFTfloat = new File(filePathFFTfloat);

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////IMPORT TRANSPOSED DATA FROM CSV

    String fileNameRead = "TransposedAnalysisData.csv";
    String filePathRead = baseDir + File.separator + fileNameRead;
    CSVReader reader;   //registering CSVReader

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT BUFFER DATA TO TXT

    String fileNameSAX = "saxInput.txt";
    String filePathSAX = baseDir + File.separator + fileNameSAX;
    File fSAX = new File(filePathSAX);

    /////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////// BUFFER ARRAYLISTS FOR INCOMING WATCH DATA
    List<Float> gestureValuesBufferX = new ArrayList<>();
    List<Float> gestureValuesBufferY = new ArrayList<>();
    List<Float> gestureValuesBufferZ = new ArrayList<>();
    List<Float> gestureGeneralBuffer = new ArrayList<>();
    /////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "OnCreate ");

        ////////////////////////////////////////////////////////////////////////////////////LAYOUT COMPONENTS
        coords = (TextView) findViewById(R.id.coords);
        packetView = (TextView) findViewById(R.id.packets);
        currentAlgo = (TextView) findViewById(R.id.currentAlgo);
        newGesture = (Button) findViewById(R.id.buttonZeroValue);
        d3Button = (Button) findViewById(R.id.d3);
        fftButton = (Button) findViewById(R.id.fft);
        saxButton = (Button) findViewById(R.id.sax);
        trainButton = (Button) findViewById(R.id.trainButton);

        newGesture.setOnClickListener(this);    //listener for recording button
        newGesture.setEnabled(false);           //can't be pressed if watch hasn't connected yet
        d3Button.setOnClickListener(this);      //choosing recognition algorithm buttons
        fftButton.setOnClickListener(this);
        saxButton.setOnClickListener(this);
        trainButton.setOnClickListener(this);
        ////////////////////////////////////////////////////////////////////////////////////

        /** register bluetooth adapter and listener */
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Wearable.getDataClient(this).addListener(this);

        /** prompt user to grant storage permission for I/O */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        initializeFromCSV(); //method for loading raw data from csv for transforming them into a new data set (used during development)
        datasetFFT();
        //writeSAXtoTXT();
        //try{
        //MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(null);
        //}catch (IOException e){
        //    e.printStackTrace();
        //}

    }

    /** onClick Override handles each button click for choosing an algorithm or recording */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonZeroValue:
                if (!start) {
                    //if(chosenAlgorithm == null) {
                    //    Toast.makeText(this, "Recording Exported as Training Data!", Toast.LENGTH_LONG).show();
                        //exportDataToCSV(new float[]{30.00f, 30.00f, 30.00f}, filePath, f);  //calling method with 3 float cells having the value of 30 to distinguish where every new gesture starts (used during development)
                    //}
                    start = true;
                    //Log.d(TAG, "onClick: BEFORE START" + recordedCount);
                    recordedCount++;
                    //Log.d(TAG, "onClick: AFTER START"  + recordedCount);
                    d3Button.setEnabled(false); //disabling the other buttons to avoid transforming the data before gesture is complete
                    saxButton.setEnabled(false);
                    fftButton.setEnabled(false);
                }
                else {
                    Toast.makeText(this, "Already Recording!", Toast.LENGTH_LONG).show();
                }
                break;

            case R.id.d3:
                currentAlgo.setText(R.string.current_algorithm_d3);
                chosenAlgorithm = "d3";
                break;

            case R.id.fft:
                currentAlgo.setText(R.string.current_algorithm_fft);
                chosenAlgorithm = "fft";
                if(!gestureGeneralBuffer.isEmpty()) {
                    bufferFFT();
                    gestureGeneralBuffer.clear();
                }
                //datasetFFT();
                break;

            case R.id.sax:
                currentAlgo.setText(R.string.current_algorithm_sax);
                chosenAlgorithm = "sax";
                if(!gestureGeneralBuffer.isEmpty()) {
                    try {
                        MainTestSAX_SingleTimeSeries.main(bufferSAX());
                        gestureGeneralBuffer.clear();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
                else{
                    Toast.makeText(this, "Record something to process!",Toast.LENGTH_LONG).show();
                }
                /*
                String[] args = new String[0];
                try {
                    MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(args);
                } catch (IOException e) {
                    e.printStackTrace();
                }
                */
                break;

            case R.id.trainButton:
                currentAlgo.setText(R.string.current_algorithm_none);
                chosenAlgorithm = null;
                break;

        }
    }

    /** onStop and onDestroy unregister the listener and register again onResume */

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

    /** Thread that updates the UI with info about incoming data and completed gestures */
    public void logthis(){
        runOnUiThread(() -> {
            coords.setText("Recorded Gestures: " + gesturesComplete + " Gesture General Buffer: " + gestureGeneralBuffer.size());
            //Log.d(TAG, "Received/Recorded: " + receivedCount + "/" + recordedCount + " Gesture General Buffer: " + gestureGeneralBuffer.size());
            packetView.setText("Received/Recorded: " + receivedCount + "/" + recordedCount );
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
                    if(values!=null && start) {
                        if((recordedCount - gesturesComplete) % 31 == 0){
                            if(chosenAlgorithm == "sax"){
                                Toast.makeText(this, "Under Construction!", Toast.LENGTH_LONG).show();
                                //gestureGeneralBuffer.clear();

                            }
                            else if(chosenAlgorithm == "fft"){
                                Toast.makeText(this, "Under Construction!", Toast.LENGTH_LONG).show();
                                //gestureGeneralBuffer.clear();

                            }
                            else if(chosenAlgorithm == "d3"){
                                Toast.makeText(this, "Under Construction!", Toast.LENGTH_LONG).show();
                                //gestureGeneralBuffer.clear();
                            }
                            else{
                                //Toast.makeText(this, "Recording Exported as Training Data!", Toast.LENGTH_LONG).show();
                                //Toast.makeText(this, "Recording Exported as Training Data!", Toast.LENGTH_LONG).show();
                                //exportDataToCSV(new float[]{-30.00f, -30.00f, -30.00f}, filePath, f);
                                //gestureGeneralBuffer.clear();
                            }
                            start = false;
                            //Log.d(TAG, "onClick: BEFORE STOP" + recordedCount);
                            recordedCount++;
                            //Log.d(TAG, "onClick: AFTER STOP"  + recordedCount);
                            gesturesComplete++;

                        }
                        else{
                            //Log.d(TAG, "onDataChanged: I AM HERE BITCHEZZZZZZ!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!");
                            //coords.setText("Recorded Gestures: " + gesturesComplete);
                            //Log.d(TAG, "Received/Recorded: " + receivedCount + "/" + recordedCount + " Gesture General Buffer: " + gestureGeneralBuffer.size());
                            //packetView.setText("Received/Recorded: " + receivedCount + "/" + recordedCount + " Gesture General Buffer: " + gestureGeneralBuffer);
                            dataBuffer();
                            logthis();
                            if(chosenAlgorithm == null){
                                //Toast.makeText(this, "Recording Exported as Training Data!", Toast.LENGTH_LONG).show();
                                //exportDataToCSV(values,filePath,f);
                            }
                        }
                    }
                    else{
                        if(!start) {
                            logthis();
                            d3Button.setEnabled(true);
                            saxButton.setEnabled(true);
                            fftButton.setEnabled(true);
                            if(receivedCount > 100) newGesture.setEnabled(true);
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
    }

    public void dataBuffer(){

        gestureValuesBufferX.add(values[0]);
        gestureValuesBufferY.add(values[1]);
        gestureValuesBufferZ.add(values[2]);
        //Log.d(TAG, "\ndataBufferX: SIZE:" + gestureValuesBufferX.size());
        //Log.d(TAG, "\ndataBufferY: SIZE:" + gestureValuesBufferY.size());
        //Log.d(TAG, "\ndataBufferZ: SIZE:" + gestureValuesBufferZ.size());
        //Log.d(TAG, "dataBuffer: before increment"+ recordedCount);
        recordedCount++;
        //Log.d(TAG, "dataBuffer: after increment"+ recordedCount);

        if(gestureValuesBufferX.size() == 30 && gestureValuesBufferY.size() == 30 && gestureValuesBufferZ.size() == 30) {

//            Log.d(TAG, "\ndataBuffer: GENERAL SIZE BEFORE CLEAR:" + gestureGeneralBuffer.size());
//
//            if(gestureGeneralBuffer.size() >= 90){
//                if(chosenAlgorithm.equals("sax")){  //Write data to txt file
//                    writeSAXtoTXT();
//                }
//                else if(chosenAlgorithm.equals("fft")){
//                    bufferFFT();
//                }
//                gestureGeneralBuffer.clear();
//                Log.d(TAG, "\ndataBuffer: SIZE AFTER CLEAR:" + gestureGeneralBuffer.size());
//            }

            gestureGeneralBuffer.addAll(0, gestureValuesBufferX);
            gestureGeneralBuffer.addAll(30, gestureValuesBufferY);
            gestureGeneralBuffer.addAll(60, gestureValuesBufferZ);

            //Log.d(TAG, "\ndataBuffer: SIZEs BEFORE CLEAR (X, Y, Z):" + gestureValuesBufferX.size() + gestureValuesBufferY.size() + gestureValuesBufferZ.size());

            gestureValuesBufferX.clear();
            gestureValuesBufferY.clear();
            gestureValuesBufferZ.clear();

            //Log.d(TAG, "\ndataBuffer: SIZEs AFTER CLEAR (X, Y, Z):" + gestureValuesBufferX.size() + gestureValuesBufferY.size() + gestureValuesBufferZ.size());
        }

    }

    public void writeSAXtoTXT(){
        int inc = 0;
        try {
            BufferedWriter TXTwriter = new BufferedWriter(new FileWriter(fSAX));
            for(float[] valsRow : dataSet){
                for(float valsCol : valsRow) {
                    TXTwriter.append(Float.toString(valsCol));
                    TXTwriter.append(",");
                }
                TXTwriter.newLine();
            }
            TXTwriter.flush();
            TXTwriter.close();
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    public String[] bufferSAX(){
        String[] buffData = new String[90];
        for(int i = 0; i<gestureGeneralBuffer.size(); i++){
            buffData[i] = gestureGeneralBuffer.get(i).toString();
        }
        return buffData;
    }

    public void bufferFFT(){
        int count = 0;
        //array that keeps the absolute values of the data after being transformed
        float[] absFFTbuffer = new float[64];
        int r = 0;
        for(int j = 0; j<90; j++){
            if(( count == 2 || count == 5) && j != 14 && j != 44 && j != 74 && j != 83 ){
                count=0;
                continue;
            }

            bufferrow[r] = new Complex(gestureGeneralBuffer.get(j),0);
            r++;
            count++;
        }
        fft(bufferrow);
        for(int a = 0; a < bufferrow.length; a++){
            absFFTbuffer[a] = (float) (bufferrow[a].abs()/64.0);
            Log.d((a+1)+" --> ",  " " + absFFTbuffer[a] + System.lineSeparator());
        }
    }

    public void datasetFFT(){
        int count = 0;
        for(int i = 0; i < 120; i++){
            int r = 0;
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
        exportDataToCSV(new float[]{1},filePathFFT, fFFT);
        exportDataToCSV(new float[]{},filePathFFTfloat, fFFTfloat);
    }

    public void initializeFromCSV(){
        try{
            Log.d(TAG, "initializeFromCSV: *************************\n*************************\n*************************\n*************************\n*************************\n");
            FileReader read = new FileReader(filePathRead);
            reader = new CSVReader(read);
            String[] lines;
            int i = 0,j = 0;
            String[] linesOut = reader.readNext();
            while((lines = reader.readNext()) != null){
                for(String current : lines){
                    if(j==90)
                        break;
                    dataSet[i][j] = Float.parseFloat(current);
                    j++;

                }
                j = 0;
                i++;
            }
        }catch(Exception e){
            e.printStackTrace();
        }
    }

    public void exportDataToCSV(float[] accData, String fp, File FL){

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

            recordedCount++;
            writer.writeNext(val);
        }
        else {
            String[] val = new String[64];
            char axis = ' ';
            int valIter = 1;
            for(int j = 0; j< fftDataset.length; j++){
//                if(j == 0) {
//
//                    for(int line = 0; line < fftDataset[0].length; line++){
//                        if(line<=30) {axis = 'X';}
//                        else if(line>30 || line<=60) {axis = 'Y';}
//                        else if(line>60 || line<=90) {axis = 'Z';}
//
//                        if(valIter == 31) {valIter = 1;}
//
//                        val[line] = ""+axis+(valIter);
//                        valIter++;
//                    }
//                    writer.writeNext(val, false);
//                }
                for(int k = 0; k<fftDataset[0].length; k++){

                    if(accData.length == 1) val[k] = fftDataset[j][k].toStringZ();
                    else val[k] = Float.toString((float)(fftDataset[j][k].abs()/64.0));
                }
                writer.writeNext(val,false);
            }
        }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
