package georgiou.thesis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.media.SoundPool;
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

import java.io.BufferedReader;
import java.io.BufferedWriter;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

import static ca.pfv.spmf.algorithms.timeseries.sax.MainTestSAX_SingleTimeSeries.constant;
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
    private TextView gestureRecognized;
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
    int txtVals[][] = new int[120][constant];

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

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT BUFFER DATA TO TXT

    String fileNameSAXRead = "saxOutput.txt";
    String filePathSAXRead = baseDir + File.separator + fileNameSAXRead;
    File fSAXRead = new File(filePathSAXRead);

    /////////////////////////////////////////////////////////////////////////////////////////////

    ///////////////////////////////////////////////////////////////////////////////////////////// BUFFER ARRAYLISTS FOR INCOMING WATCH DATA
    List<Float> gestureValuesBufferX = new ArrayList<>();
    List<Float> gestureValuesBufferY = new ArrayList<>();
    List<Float> gestureValuesBufferZ = new ArrayList<>();
    List<Float> gestureGeneralBuffer = new ArrayList<>();
    /////////////////////////////////////////////////////////////////////////////////////////////

    ////////////////////////////////////////////////////////////////////////////////////////////NOTIFICATION SOUNDS

    MediaPlayer player;

    ////////////////////////////////////////////////////////////////////////////////////////////

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "OnCreate ");

        ////////////////////////////////////////////////////////////////////////////////////LAYOUT COMPONENTS
        coords = (TextView) findViewById(R.id.coords);
        packetView = (TextView) findViewById(R.id.packets);
        currentAlgo = (TextView) findViewById(R.id.currentAlgo);
        gestureRecognized = (TextView) findViewById(R.id.gestureRecognized);
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
        //datasetFFT();
        writeSAXtoTXT();
        try{
        MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(null);
        }catch (IOException e){
            e.printStackTrace();
        }
        try{initializeFromSAXtxt();}catch (Exception e){e.printStackTrace();}


    }

    public void enableButtons(boolean doIt){
        d3Button.setEnabled(doIt); //disabling the other buttons to avoid transforming the data before gesture is complete
        saxButton.setEnabled(doIt);
        fftButton.setEnabled(doIt);
        trainButton.setEnabled(doIt);
        newGesture.setEnabled(doIt);
    }

    /** onClick Override handles each button click for choosing an algorithm or recording */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonZeroValue:
                if (!start) {
                    if(chosenAlgorithm == "train") {
                        exportDataToCSV(new float[]{30.00f, 30.00f, 30.00f}, filePath, f);  //calling method with 3 float cells having the value of 30 to distinguish where every new gesture starts (used during development)
                    }
                    start = true;
                    //Log.d(TAG, "onClick: BEFORE START" + recordedCount);
                    recordedCount++;
                    //Log.d(TAG, "onClick: AFTER START"  + recordedCount);
                    enableButtons(false);
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
                enableButtons(false);
                if(!gestureGeneralBuffer.isEmpty()) {
                    bufferFFT();
                    gestureGeneralBuffer.clear();
                }
                //datasetFFT();
                enableButtons(true);
                break;

            case R.id.sax:
                currentAlgo.setText(R.string.current_algorithm_sax);
                chosenAlgorithm = "sax";
                enableButtons(false);
                if(!gestureGeneralBuffer.isEmpty()) {
                    try {
                        MainTestSAX_SingleTimeSeries.main(bufferSAX());
                        int[] liveSAX = MainTestSAX_SingleTimeSeries.getSym();

                        for(int i = 0; i<liveSAX.length; i++){
                            Log.e(TAG, "USER INPUT -----> \t"+liveSAX[i]);

                        }
                        recognitionAlgo(chosenAlgorithm,liveSAX);
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
                enableButtons(true);
                break;

            case R.id.trainButton:
                currentAlgo.setText(R.string.current_algorithm_none);
                chosenAlgorithm = "train";
                break;
            default:
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
                            if(chosenAlgorithm == "train"){
                                Toast.makeText(this, "Recording Exported as Training Data!", Toast.LENGTH_LONG).show();
                                exportDataToCSV(new float[]{-30.00f, -30.00f, -30.00f}, filePath, f);
                            }
                            else {
                                Toast.makeText(this, "Recording completed", Toast.LENGTH_LONG).show();
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
                            d3Button.setEnabled(true); //re-enabling all the buttons after the gesture is complete
                            saxButton.setEnabled(true);
                            fftButton.setEnabled(true);
                            trainButton.setEnabled(true);
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
            if(gestureGeneralBuffer.size() >= 90){
                gestureGeneralBuffer.clear();
            }
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

    public void initializeFromSAXtxt() throws IOException {
        BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(filePathSAXRead))));
        String thisLine, separator =",";
        int i , j = 0;
        while ((thisLine = myInput.readLine()) != null) {
            if(thisLine.charAt(0) == '@'){
                continue;
            }
            else{
                i=0;
                for(String currLine : thisLine.split(separator)){
                    txtVals[j][i] = Integer.parseInt(currLine);
                    i++;
                    if(i==constant) break;
                }
                j++;
            }

        }
        int a=0;
        outerloop:
        for(int[] rows: txtVals){
            for(int cols: rows){
                System.out.println(cols);
                a++;
                if(a==36) break outerloop;
            }
        }

    }

    public String recognitionAlgo(String algorithm, int[] sax){

        switch (algorithm){
            case "d3":
                break;
            case "fft":
                break;
            case "sax":
                int[] diff = new int[txtVals[0].length];
                double[][] diffDev = new double[txtVals.length][txtVals[0].length];
                double[] diffSum = new double[txtVals.length];
                double dev = 0.16;
                double min = 100;
                //Log.e(TAG, "txtVals[0].length -->\t" + txtVals[0].length + "\ttxtVals.length -->\t" + txtVals.length + "\tsax.length -->\t" + sax.length + "\tdiff.length -->\t" + diff.length);
                for(int i = 0; i < txtVals.length; i++){
                    for(int j = 0; j < txtVals[0].length; j++){
                        diff[j] = sax[j] - txtVals[i][j];
                        //Log.e(TAG, "recognitionAlgo: SAX[J] -->\t" + sax[j] +"\tj = "+j);
                        if(diff[j] < -1 || diff[j] > 1){
                            diffDev[i][j] = (Math.abs(diff[j])-1) * dev;
                        }
                        else{
                            diffDev[i][j] = 0;
                        }
                        //Log.e(TAG, "recognitionAlgo: diffDev -->\t" + diffDev[i][j] + "\tdiff -->\t" + diff[j]);

                    }
                }
                for(int i = 0; i<diffDev.length; i++){
                    for( int j = 0; j<diffDev[0].length; j++){
                        diffSum[i] += diffDev[i][j];
                        //Log.e(TAG, "recognitionAlgo: DIFF SUM ----->\t" + diffSum[i] + "\tfor i = " + i);
                    }
                    min = (diffSum[i] < min) ? diffSum[i] : min;
                    //Log.d(TAG, "recognitionAlgo: SHOW ME THE MINIMUM YOU SOB ------------------------------------------->>>\t" + min);
                    if(min == 0) { findGestureWithSAX(i); break; }
                }

                Log.e(TAG, "recognitionAlgo: MINIMUM VALUE -->\t" + min);
                int index = findIndex(diffSum,min);
                findGestureWithSAX(index);
                break;
            default:
                break;
        }
        return "";
    }

    public int findIndex(double[] array, double min){

        for(int i = 0; i<array.length; i++){

                if(array[i] == min){
                    Log.e(TAG, "findIndex: The index that a match was found is:\t" + i);
                    return i;
                }

        }
        Log.e(TAG, "findIndex: \t\t\t I WILL RETURN -1");
        return -1;
    }

    public void playDaSound(int rawID){
        if(player == null){
            player = MediaPlayer.create(this, rawID);
            player.setOnCompletionListener(mediaPlayer -> {
                player.release();
                player = null;
            });
        }

        player.start();
    }

    public void findGestureWithSAX(int index){
        if(index < 24) {
            gestureRecognized.setText(R.string.currGestHH);
            playDaSound(R.raw.hh);
        }
        else if (index >= 24 && index < 48) {
            gestureRecognized.setText(R.string.currGestHU);
            playDaSound(R.raw.hu);
        }
        else if (index >= 48 && index < 72) {
            gestureRecognized.setText(R.string.currGestHUD);
            playDaSound(R.raw.hud);
        }
        else if(index >= 72 && index < 96) {
            gestureRecognized.setText(R.string.currGestHH2);
            playDaSound(R.raw.hh2);
        }
        else if (index >= 96 && index < 120) {
            gestureRecognized.setText(R.string.currGestHU2);
            playDaSound(R.raw.hu2);
        }
        else
            System.out.println("I WILL NOT BE PRINTED UNDER ANY CIRCUMSTANCES");

    }

    public String[] bufferSAX(){
        String[] buffData = new String[90];
        for(int i = 0; i<gestureGeneralBuffer.size(); i++){
            buffData[i] = gestureGeneralBuffer.get(i).toString();
        }
        return buffData;
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
