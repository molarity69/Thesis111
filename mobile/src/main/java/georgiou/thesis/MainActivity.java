package georgiou.thesis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.DialogInterface;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Environment;
import android.text.InputType;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.PopupMenu;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
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
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

import static ca.pfv.spmf.algorithms.timeseries.sax.MainTestSAX_SingleTimeSeries.constant;
import static georgiou.thesis.FFT.fft;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestConvertTimeSeriesFiletoSequenceFileWithSAX;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestSAX_SingleTimeSeries;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;

public class MainActivity extends AppCompatActivity implements DataClient.OnDataChangedListener, View.OnClickListener, PopupMenu.OnMenuItemClickListener {

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
    private String chosenAlgorithm = "0";  //checking which button is pressed

    public BluetoothAdapter mBluetoothAdapter; //bluetooth initializer
    public String datapath = "/data_path"; //path for bluetooth communication

    private float[][] dataSet;  //array that holds the imported data set from CSV file
    private float[][] globalDataSet;    //array that holds the data from the CSV that gets updated
    private boolean changed = false;    //checks which is the active data set for recognition
    private int newInput = 0;   //increments if there is a new gesture added in the set
    private int newInputCheck = 0;  //checks if a new gesture is added since the last initialization
    private Complex[][] fftDataset;  //array that holds the imported data set in Complex type
    int[][] txtVals;    //array that holds dataset values after being transformed with sax
    int hhCount = 0, huCount = 0, hudCount = 0, hh2Count = 0, hu2Count = 0; //variables that help with the sorting and the classification of the constantly changing data set
    int hhCountPersonal = 0, huCountPersonal = 0, hudCountPersonal = 0, hh2CountPersonal = 0, hu2CountPersonal = 0;
    double[][] fftFloatDataset;   //array that holds dataset values after being transformed with FFT, last cell is the gesture class
    private int fftSetValuesCount = 64; //constant of the number of values used in fft
    public static svm_node[] nodes1;    //array svm_node type that holds the incoming gesture data as prediction input for SVM
    svm_model model1;   //an svm_model object that holds the SVM parameters for fft recognition

    private Complex[] row = new Complex[fftSetValuesCount];    //array that helps with copying each imported data row to the data set after transformation
    private Complex[] bufferrow = new Complex[fftSetValuesCount];  //same as above but for the buffer array that holds data for recognition

    public static String baseDir = Environment.getExternalStoragePublicDirectory("/DCIM").getAbsolutePath();    //path to phone storage folder
    public svm_model model = new svm_model();   //svm_model that holds all information about the SVM parameters

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

    /////////////////////////////////////////////////////////////////////////////////////////////IMPORT FFT DATA IN FLOAT FROM CSV

//    String fileNameFFTfloat = "FFTAnalysisDataFloat.csv";
//    String filePathFFTfloat = baseDir + File.separator + fileNameFFTfloat;
//    File fFFTfloat = new File(filePathFFTfloat);

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////IMPORT TRANSPOSED DATA FROM PERSONAL CSV

    String fileNameRead = "TransposedAnalysisData.csv";
    String filePathRead = baseDir + File.separator + fileNameRead;
    CSVReader reader;   //registering CSVReader

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT TRANSPOSED DATA TO GLOBAL CSV

    String fileNameWrite = "TransposedAnalysisDataGlobal.csv";
    String filePathWrite = baseDir + File.separator + fileNameWrite;
    File fGlobalWrite = new File(filePathWrite);

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT PERSONAL BUFFER DATA TO TXT

    String fileNameSAX = "saxInput.txt";
    String filePathSAX = baseDir + File.separator + fileNameSAX;
    File fSAX = new File(filePathSAX);

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT GLOBAL BUFFER DATA TO TXT

    String fileNameSAXGlobal = "saxInputGlobal.txt";
    String filePathSAXGlobal = baseDir + File.separator + fileNameSAXGlobal;
    File fSAXGlobal = new File(filePathSAXGlobal);

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////EXPORT TRANSFORMED SAX DATA SET TO TXT

    String fileNameSAXRead = "saxOutput.txt";
    String filePathSAXRead = baseDir + File.separator + fileNameSAXRead;

    /////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////IMPORT SVM MODEL DATA FROM TXT

    String fileNameCoefsRead = "classifier_coefs.txt";
    String filePathCoefsRead = baseDir + File.separator + fileNameCoefsRead;
    String fileNameRhoRead = "classifier_rho.txt";
    String filePathRhoRead = baseDir + File.separator + fileNameRhoRead;
    String fileNameSVRead = "classifier_SV.txt";
    String filePathSVRead = baseDir + File.separator + fileNameSVRead;
    String modelName = "newClass.txt";
    String filePathModelName = baseDir + File.separator +modelName;

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
        coords = findViewById(R.id.coords);
        packetView = findViewById(R.id.packets);
        currentAlgo = findViewById(R.id.currentAlgo);
        gestureRecognized = findViewById(R.id.gestureRecognized);
        newGesture = findViewById(R.id.buttonZeroValue);
        d3Button = findViewById(R.id.d3);
        fftButton = findViewById(R.id.fft);
        saxButton = findViewById(R.id.sax);
        trainButton = findViewById(R.id.trainButton);

        newGesture.setOnClickListener(this);    //listener for recording button
        newGesture.setEnabled(false);           //can't be pressed if watch hasn't connected yet
        d3Button.setOnClickListener(this);      //choosing recognition algorithm buttons
        fftButton.setOnClickListener(this);
        saxButton.setOnClickListener(this);
        trainButton.setOnClickListener(this);
        ////////////////////////////////////////////////////////////////////////////////////

        /* register bluetooth adapter and listener */
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        Wearable.getDataClient(this).addListener(this);

        /* prompt user to grant storage permission for I/O */
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},0);
        }

        //method for loading raw data from csv for transforming them into a new data set (used during development)
        //initializeFromCSV(true, filePathWrite);
        initializeFromCSV(false, filePathRead);
        datasetFFT();
        //method for exporting raw data to txt for transforming them into a new data set with SAX (used during development)
        //writeSAXtoTXT();
        try{
            //spmf library method that transforms raw data from txt file with SAX and exports them to another txt file (used during development)
            //MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(null);

            initializeFromSAXtxt(false); //method that loads dataset in SAX form into an array for comparing with input data
            //importSVMmodelDataAndBuild(filePathCoefsRead, filePathSVRead, filePathRhoRead); //method that builds the SVM model
            model1 = svm.svm_load_model(filePathModelName);
            System.out.println("Model Gamma ---> \t"+ model1.param.gamma);
            System.out.println("Model Kernel ---> \t"+model1.param.kernel_type);
            System.out.println("Model SVM Type ---> \t"+model1.param.svm_type);
            System.out.println("Model Number of Classes ---> \t"+svm.svm_get_nr_class(model1));
            System.out.println("Model Classes ---> \t"+Arrays.toString(model1.label));
        }catch (IOException e){
            e.printStackTrace();    //exception handling
        }
    }

    //disabling the other buttons to avoid transforming the data before gesture is complete and re-enabling them after it's done
    public void enableButtons(boolean doIt){

        d3Button.setEnabled(doIt);
        saxButton.setEnabled(doIt);
        fftButton.setEnabled(doIt);
        trainButton.setEnabled(doIt);
        newGesture.setEnabled(doIt);
    }

    @Override
    public boolean onMenuItemClick(MenuItem item){
        switch (item.getItemId()){
            case R.id.train1:

                Toast.makeText(this, "Training Under Construction", Toast.LENGTH_LONG).show();
                return true;
            case R.id.recognize:

                Toast.makeText(this, "Recognition Under Construction", Toast.LENGTH_LONG).show();
                return true;
            case R.id.personalSet:
                if(changed) {
                    initializeFromCSV(false, filePathRead);
                    writeSAXtoTXT(dataSet, fSAX);
                    try{
                        String arg[] = new String[]{""};
                        //spmf library method that transforms raw data from txt file with SAX and exports them to another txt file (used during development)
                        MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(arg);
                        initializeFromSAXtxt(false); //method that loads dataset in SAX form into an array for comparing with input data
                    }catch (IOException e){e.printStackTrace();}
                }
                try{
                    MainTestSAX_SingleTimeSeries.main(bufferSAX()); //spmf library method that transforms the incoming data with SAX
                    int[] liveSAX = MainTestSAX_SingleTimeSeries.getSym();  //custom method placed in spmf library to return the transformed timeseries data
                    findGestureWithSAXDataEuclidean(liveSAX);   //run the SAX recognition algorithm
                    gestureGeneralBuffer.clear();   //make space for the next incoming gesture
                }catch (IOException e){
                    e.printStackTrace();    //exception handling
                }
                return true;
            case R.id.globalSet:
                if(!changed || newInput > newInputCheck){
                    if(newInput > newInputCheck){
                        newInputCheck = newInput;
                    }
                    initializeFromCSV(true, filePathWrite);
                    writeSAXtoTXT(globalDataSet, fSAXGlobal);
                    try{
                        String arg[] = new String[]{"global"};
                        //spmf library method that transforms raw data from txt file with SAX and exports them to another txt file (used during development)
                        MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(arg);
                        initializeFromSAXtxt(true); //method that loads dataset in SAX form into an array for comparing with input data
                    }catch (IOException e){e.printStackTrace();}
                }

                try{
                    MainTestSAX_SingleTimeSeries.main(bufferSAX()); //spmf library method that transforms the incoming data with SAX
                    int[] liveSAX = MainTestSAX_SingleTimeSeries.getSym();  //custom method placed in spmf library to return the transformed timeseries data
                    findGestureWithSAXDataEuclidean(liveSAX);   //run the SAX recognition algorithm
                    gestureGeneralBuffer.clear();   //make space for the next incoming gesture
                }catch (IOException e){
                    e.printStackTrace();    //exception handling
                }
                return true;
            default:
                return false;
        }
    }

    /** onClick Override handles each button click for choosing an algorithm or recording */
    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.buttonZeroValue:  //button RECORD A GESTURE
                if (!start) {
                    start = true;   //start variable means start recording the incoming data in onDataChanged method
                    recordedCount++;    //increment recording by one when button is pressed (used for knowing when to stop recording)
                    enableButtons(false); //disable all buttons while recording
                }
                else {
                    Toast.makeText(this, "Already Recording!", Toast.LENGTH_LONG).show(); //this will never print
                }
                break;

            case R.id.d3:   //under construction
                currentAlgo.setText(R.string.current_algorithm_d3); //change UI text
                chosenAlgorithm = "d3";
                enableButtons(false);   //disable buttons when recognising
                if(!gestureGeneralBuffer.isEmpty()) {   //if there is a recorded gesture
                    PopupMenu popupMenu = new PopupMenu(this, v);
                    popupMenu.setOnMenuItemClickListener(this);
                    popupMenu.inflate(R.menu.popup_menu);
                    popupMenu.show();
                    gestureGeneralBuffer.clear();   //make space for the next incoming gesture
                }
                else{
                    //this pops up when the button is pressed while there aren't any data recorded
                    Toast.makeText(this, "Record something to process!",Toast.LENGTH_LONG).show();
                }
                enableButtons(true);    //re-enable the buttons after being done
                break;

            case R.id.fft:
                currentAlgo.setText(R.string.current_algorithm_fft);    //change UI text
                chosenAlgorithm = "fft";
                enableButtons(false);   //disable buttons when recognising
                if(!gestureGeneralBuffer.isEmpty()) {   //if there is a recorded gesture
                    findGestureClass(findGestureWithFFTDataSVM(model1,bufferFFT()),120); //run the fft recognition algorithm
                    gestureGeneralBuffer.clear();   //make space for the next incoming gesture
                }
                else{
                    //this pops up when the button is pressed while there aren't any data recorded
                    Toast.makeText(this, "Record something to process!",Toast.LENGTH_LONG).show();
                }
                enableButtons(true);    //re-enable the buttons after being done
                break;

            case R.id.sax:
                currentAlgo.setText(R.string.current_algorithm_sax);    //change UI text
                chosenAlgorithm = "sax";
                enableButtons(false);   //disable buttons when recognising
                if(!gestureGeneralBuffer.isEmpty()) {   //if there is a recorded gesture
                    PopupMenu popupMenu = new PopupMenu(this, v);
                    popupMenu.setOnMenuItemClickListener(this);
                    popupMenu.inflate(R.menu.popup_menu_sax);
                    popupMenu.show();
//                    try {
//
//                    } catch (IOException e) {
//                        e.printStackTrace();    //exception handling
//                    }
                }
                else{
                    //this pops up when the button is pressed while there aren't any data recorded
                    Toast.makeText(this, "Record something to process!",Toast.LENGTH_LONG).show();
                }
                enableButtons(true);
                break;

            case R.id.trainButton:
                currentAlgo.setText(R.string.current_algorithm_none);   //change UI text
                chosenAlgorithm = "train";  //this tag is important so that the app knows when to export things to files
                if(!gestureGeneralBuffer.isEmpty()){
                    promptForResult("Input", "Please name the Gesture you performed.", new DialogInputInterface(){
                        EditText input;
                        public View onBuildDialog() {
                            // procedure to build the dialog view
                            input = new EditText(MainActivity.this);
                            // cast the editText as a view
                            input.setInputType(InputType.TYPE_CLASS_TEXT);
                            // return the view to the dialog builder
                            return (View) input;
                        }
                        public void onCancel() {
                            // user has canceled the dialog by hitting cancel
                            gestureGeneralBuffer.clear();
                            Toast.makeText(MainActivity.this, "Operation Canceled!\nRecording discarded", Toast.LENGTH_LONG).show();
                        }
                        public void onResult(View v) {
                            // get the value from the dialog
                            String value = input.getText().toString();
                            // check if the user entered one of the correct gesture classes
                            if(value.equals("hh") || value.equals("hu") || value.equals("hud") || value.equals("hh2") || value.equals("hu2")){
                                //calling the export method with 3 float cells having the value of -30 to distinguish where every new gesture ends in the csv(used during development)
                                exportDataToCSV(new float[]{-30.00f, -30.00f, -30.00f}, filePathWrite, fGlobalWrite, true, value);
                                newInput++;
                                Toast.makeText(MainActivity.this, "Recording exported successfully", Toast.LENGTH_LONG).show();
                            }
                            else{
                                Toast.makeText(MainActivity.this, "Invalid Input!\nRecording wasn't exported", Toast.LENGTH_LONG).show();
                            }
                        }
                    });

                }
                break;

            default:
                break;

        }
    }

    /* onStop and onDestroy unregister the listener and register again onResume */
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
            coords.setText("Recorded Gestures: " + gesturesComplete + "\nBuffer: " + gestureGeneralBuffer.size());
            packetView.setText("Received/Recorded: " + receivedCount + "/" + recordedCount );
        });
    }


    /** onDataChanged is the method that handles the incoming data from smart watch via the DataLayer */
    @Override
    public void onDataChanged (@NonNull DataEventBuffer dataEventBuffer) {

        for (DataEvent event : dataEventBuffer) {   //for every incoming event
            if (event.getType() == DataEvent.TYPE_CHANGED) {    //if there is a data change
                String path = event.getDataItem().getUri().getPath();   //get the path
                if (path.equals(datapath)) {    //if path equals the path to our smart watch
                    DataMapItem dataMapItem = DataMapItem.fromDataItem(event.getDataItem());    //unwrap the item
                    values = dataMapItem.getDataMap().getFloatArray("SensorValues");    //get the contents into the array
                    if(values!=null && start) { //if the array has data and the button to start recording has been pressed
                        //this if-statement checks when the incoming values number reaches 30
                        //it's written this way so that the logthis function can update the total recorded number on the UI
                        //and knowing when to stop recording
                        if((recordedCount - gesturesComplete) % 31 == 0){
                            Toast.makeText(this, "Recording completed", Toast.LENGTH_LONG).show();
                            start = false;  //stop recording the incoming data in onDataChanged method
                            recordedCount++;    //increment recordings by one in order to make the above if-statement work
                            gesturesComplete++; //increment the gestures completed by one to keep track
                        }
                        else {  //while recording each incoming data packet
                            dataBuffer();   //call the method that stores the incoming data in the arrayList
                            logthis();  //update the UI
                        }
                    }
                    else{
                        if(!start) {    //if start button hasn't been pressed
                            logthis();  //update the UI about the received but not recorded data
                            d3Button.setEnabled(true); //keep the buttons enabled when not recording
                            saxButton.setEnabled(true);
                            fftButton.setEnabled(true);
                            trainButton.setEnabled(true);
                            //enable the RECORD A GESTURE button only when received data has reached 100
                            //it's necessary in order to establish a stable bluetooth connection after the app has started
                            if(receivedCount > 100) newGesture.setEnabled(true);
                        }
                        else Log.d(TAG, "onDataChanged: SHIT HAPPENS"); //debug info
                    }
                } else {
                    Log.e(TAG, "Unrecognized path: " + path);   //debug info
                }
            } else if (event.getType() == DataEvent.TYPE_DELETED) {
                Log.v(TAG, "Data deleted : " + event.getDataItem().toString()); //debug info
            } else {
                Log.e(TAG, "Unknown data event Type = " + event.getType()); //debug info
            }
        }
        receivedCount++;    //increment the received packets count
    }

    /** dataBuffer is the method that when called stores the incoming data in an ArrayList */
    public void dataBuffer(){

        gestureValuesBufferX.add(values[0]);    //add the next X value int the List
        gestureValuesBufferY.add(values[1]);    //add the next Y value int the List
        gestureValuesBufferZ.add(values[2]);    //add the next Z value int the List

        recordedCount++;    //increment the recorded count by one

        if(gestureValuesBufferX.size() == 30 && gestureValuesBufferY.size() == 30 && gestureValuesBufferZ.size() == 30) {   //when all lists are "full"

            if(gestureGeneralBuffer.size() >= 90){  //check if there is any data left in the buffer list from a previous call in case I forgot to clear it
                gestureGeneralBuffer.clear();       //and clear it
            }

            gestureGeneralBuffer.addAll(0, gestureValuesBufferX);   //copy the X values into the first 30 positions
            gestureGeneralBuffer.addAll(30, gestureValuesBufferY);  //copy the Y values into the next 30 positions
            gestureGeneralBuffer.addAll(60, gestureValuesBufferZ);  //copy the Z values into the last 30 positions
            gestureValuesBufferX.clear();   //then clear all the small buffers
            gestureValuesBufferY.clear();
            gestureValuesBufferZ.clear();
        }
    }

    /** findGestureClass method is called with the index of the data set that the incoming gesture matches with */
    public void findGestureClass(int index, int length){

        if(length==dataSet.length){
            //when there's a match update the UI accordingly and play the notification sound
            if(index < hhCountPersonal) { gestureRecognized.setText(R.string.currGestHH); playDaSound(R.raw.hh); }
            else if (index < hhCountPersonal+huCountPersonal) { gestureRecognized.setText(R.string.currGestHU); playDaSound(R.raw.hu); }
            else if (index < hhCountPersonal+huCountPersonal+hudCountPersonal) { gestureRecognized.setText(R.string.currGestHUD); playDaSound(R.raw.hud); }
            else if(index < hhCountPersonal+huCountPersonal+hudCountPersonal+hh2CountPersonal) { gestureRecognized.setText(R.string.currGestHH2); playDaSound(R.raw.hh2); }
            else if (index < hhCountPersonal+huCountPersonal+hudCountPersonal+hh2CountPersonal+hu2CountPersonal) { gestureRecognized.setText(R.string.currGestHU2); playDaSound(R.raw.hu2); }
            else System.out.println("LET'S HOPE I WONT BE PRINTED");
        }
        else{
            //when there's a match update the UI accordingly and play the notification sound
            if(index < hhCount) { gestureRecognized.setText(R.string.currGestHH); playDaSound(R.raw.hh); }
            else if (index < hhCount+huCount) { gestureRecognized.setText(R.string.currGestHU); playDaSound(R.raw.hu); }
            else if (index < hhCount+huCount+hudCount) { gestureRecognized.setText(R.string.currGestHUD); playDaSound(R.raw.hud); }
            else if(index < hhCount+huCount+hudCount+hh2Count) { gestureRecognized.setText(R.string.currGestHH2); playDaSound(R.raw.hh2); }
            else if (index < hhCount+huCount+hudCount+hh2Count+hu2Count) { gestureRecognized.setText(R.string.currGestHU2); playDaSound(R.raw.hu2); }
            else System.out.println("LET'S HOPE I WONT BE PRINTED");
        }

    }

    /** playDaSound method is handling the MediaPlayer and the media file it needs to play */
    public void playDaSound(int rawID){

        if(player == null){ //if there isn't another instance of player active
            player = MediaPlayer.create(this, rawID);   //create a new player with the file that is given in the parameters
            player.setOnCompletionListener(mediaPlayer -> { // when the file has stopped playing
                player.release();   //release the player
                player = null;  //set it to null
            });
        }
        player.start(); //play the sound file
    }

    //------------------------- Methods Used in Gesture Recognition Using SAX -------------------------//

    /** findGestureWithSAXDataEuclidean is a method that finds which gesture is performed by the user
     *  by comparing it's SAX transformed data with the datasets data */
    public void findGestureWithSAXDataEuclidean(int[] sax){

        int[] diff = new int[txtVals[0].length];    //array that stores the difference between two SAX symbols
        //array that stores the difference between two SAX symbols for every recording multiplied by the deviation of each symbol from one another
        double[][] diffDev = new double[txtVals.length][txtVals[0].length];
        double[] diffSum = new double[txtVals.length];  //array that stores the sum of deviations from every recording in the data set
        System.out.println("DiffSum Length -------> \t"+diffSum.length);
        //the distance between SAX symbols (its value is 0.16 because for this example we use 15 symbols for each time series transformation
        // and it can be changed accordingly by consulting the file ca.pfv.spmf.algorithms.timeseries.sax.AlgoSAX)
        double dev = 0.16;
        double min = 100;   //a large number much larger than the possible minimum of this method
        for(int i = 0; i < txtVals.length; i++){    //for all the values in the data set array
            for(int j = 0; j < txtVals[0].length; j++){
                diff[j] = sax[j] - txtVals[i][j];   //find the distance between each symbol
                if(diff[j] < -1 || diff[j] > 1){    //if the absolute distance is greater than 1
                    diffDev[i][j] = (Math.abs(diff[j])-1) * dev;    //then add the absolute distance in the array multiplied by the deviation
                }
                else{
                    diffDev[i][j] = 0;  //else the difference is considered to be zero
                }
            }
        }
        for(int i = 0; i<diffDev.length; i++){  //for all the values in the deviations array
            for( int j = 0; j<diffDev[0].length; j++){
                diffSum[i] += diffDev[i][j];    //add the deviations of each cell
            }
            min = (diffSum[i] < min) ? diffSum[i] : min;    //for every row, if the sum of the deviations is less than the previous one make min the current one
            if(min == 0) { findGestureClass(i, diffSum.length); break; }    //if there is no deviations in a row its a definite match so stop searching and call the classification method with the current index
        }
        Log.e(TAG, "recognitionAlgo: MINIMUM VALUE -->\t" + min);
        int index = findIndex(diffSum,min); //call the method that finds the index where the minimum deviation was detected
        findGestureClass(index, diffSum.length);    //call the class finder with the detected index
    }

    /** findIndex method returns the index that contains the minimum deviation from the incoming gesture for recognition */
    public int findIndex(double[] array, double min){

        for(int i = 0; i<array.length; i++){
            if(array[i] == min){    //if the cell value equals the minimum value
                Log.e(TAG, "findIndex: The index that a match was found is:\t" + i);
                return i;   //return the index it was found in
            }
        }
        Log.e(TAG, "findIndex: \t\t\t IF I FAIL I WILL RETURN -1");
        return -1;
    }

    /** bufferSAX method returns an array of the incoming values that are stored in the ArrayList in String format */
    public String[] bufferSAX(){

        String[] buffData = new String[90];
        for(int i = 0; i<gestureGeneralBuffer.size(); i++){
            buffData[i] = gestureGeneralBuffer.get(i).toString();   //fill each cell with the buffer ArrayList data in String format
        }
        return buffData;
    }

    /** initializeFromSAXtxt method reads the transformed data form the data set that is stored in a txt file inside the mobile storage */
    public void initializeFromSAXtxt(boolean global) throws IOException {

        BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(filePathSAXRead))));    //initialize the reader
        String thisLine, separator =",";    //each value is separated by comma in every line
        int i , j = 0;
        if(global)
            txtVals = new int[globalDataSet.length][constant];
        else
            txtVals = new int[dataSet.length][constant];
        while ((thisLine = myInput.readLine()) != null) {   //while there are lines in the file
            //if the first character in a line is @ ignore it and continue to the next line
            if(thisLine.charAt(0) == '@'){ continue; }  //these lines are there because of the way AlgoConvertTimeSeriesFileToSequenceFileWithSAX writes them in the txt file
            else{
                i=0;
                for(String currLine : thisLine.split(separator)){   //split the line with the given separator
                    txtVals[j][i] = Integer.parseInt(currLine); //add every symbol you find in a row into the array
                    i++;
                    //if you reach the maximum number o symbols in a row break to get the next row (constant is initialized in MainTestConvertTimeSeriesFileToSequenceFileWithSAX)
                    if(i==constant) break;  //this is needed because every line of symbols in the txt file ends with a comma so the method will add an empty cell in the end of every array row
                }
                j++;
            }
        }
        myInput.close();    //close the reader
    }

    /** writeSAXtoTXT method was used during development for exporting raw data to txt for transforming them into a new data set with SAX */
    public void writeSAXtoTXT(float[][] dataSet, File f){

        try {
            BufferedWriter TXTwriter = new BufferedWriter(new FileWriter(f));    //initialize writer
            for(float[] valsRow : dataSet){ //for all the dataset array
                for(float valsCol : valsRow) {
                    TXTwriter.append(Float.toString(valsCol));  //append every value to the text file
                    TXTwriter.append(",");  //separate it with coma
                }
                TXTwriter.newLine();    //change line
            }
            TXTwriter.flush();  //flush the writer
            TXTwriter.close();  //close the writer
        }catch (Exception e){
            e.printStackTrace();    //exception handling
        }

//        try {
//            BufferedWriter TXTwriter;
//            if(f.exists()&&!f.isDirectory()) {    //if the file already exists
//                AlertDialog.Builder builder = new AlertDialog.Builder(this);
//                builder.setTitle("Attention!");
//                builder.setMessage("Make a new Training Set or append to the existing one?");
//                builder.setPositiveButton("Append", (dialogInterface, i) -> {
//                    try{
//                        BufferedWriter TXTwriter1;
//                        FileWriter mFileWriter = new FileWriter(fp, true);  //append to it
//                        TXTwriter1 = new BufferedWriter(mFileWriter);    //initialize the writer
//                        for(float[] valsRow : dataSet){ //for all the dataset array
//                            for(float valsCol : valsRow) {
//                                TXTwriter1.append(Float.toString(valsCol));  //append every value to the text file
//                                TXTwriter1.append(",");  //separate it with coma
//                            }
//                            TXTwriter1.newLine();    //change line
//                        }
//                        TXTwriter1.flush();  //flush the writer
//                        TXTwriter1.close();  //close the writer
//                    }catch (IOException e){
//                        e.printStackTrace();
//                    }
//                    Toast.makeText(MainActivity.this, "Training Set appended", Toast.LENGTH_LONG).show();
//                });
//
//                builder.setNegativeButton("Make New", (dialogInterface, i) -> {
//                    try{
//                        BufferedWriter TXTwriter12;
//                        TXTwriter12 = new BufferedWriter(new FileWriter(fp)); //else initialize a writer to write a new file
//                        for(float[] valsRow : dataSet){ //for all the dataset array
//                            for(float valsCol : valsRow) {
//                                TXTwriter12.append(Float.toString(valsCol));  //append every value to the text file
//                                TXTwriter12.append(",");  //separate it with coma
//                            }
//                            TXTwriter12.newLine();    //change line
//                        }
//                        TXTwriter12.flush();  //flush the writer
//                        TXTwriter12.close();  //close the writer
//                    }catch (IOException e){
//                        e.printStackTrace();
//                    }
//                    Toast.makeText(MainActivity.this, "Training Set saved in an new file", Toast.LENGTH_LONG).show();
//                });
//
//                builder.setNeutralButton("Cancel", (dialogInterface, i) -> Toast.makeText(MainActivity.this, "Operation Canceled", Toast.LENGTH_LONG).show());
//
//                builder.setCancelable(false);
//                builder.show();
//            }
//            else {
//                TXTwriter = new BufferedWriter(new FileWriter(fp)); //else initialize a writer to write a new file
//                for(float[] valsRow : dataSet){ //for all the dataset array
//                    for(float valsCol : valsRow) {
//                        TXTwriter.append(Float.toString(valsCol));  //append every value to the text file
//                        TXTwriter.append(",");  //separate it with coma
//                    }
//                    TXTwriter.newLine();    //change line
//                }
//                TXTwriter.flush();  //flush the writer
//                TXTwriter.close();  //close the writer
//            }
//
//        }catch (Exception e){
//            e.printStackTrace();    //exception handling
//        }
    }

    //-------------------------------------------------------------------------------------------------//

    //------------------------- Methods Used in Gesture Recognition Using FFT -------------------------//

    /** bufferFFT method transforms the user gesture data using FFT, calculates the absolute value and returns the data points in an svm_node object */
    public svm_node[] bufferFFT(){

        int count = 0;  //counter for the data points

        float[] absFFTbufferForFloat = new float[fftSetValuesCount];   //array that stores the absolute values of the complex numbers
        nodes1 = new svm_node[absFFTbufferForFloat.length]; //an svm_node object to store the values and index of every data point
        int r = 0;
        for(int j = 0; j<90; j++){  //for all 90 data points of the incoming user gesture
            //this if-statement is needed to exclude every third data point in order to scale
            // the 90 values of the incoming gesture down to 64 because this implementation of
            // FFT transformation only works with arrays that their length is a power of 2.
            //So every third or sixth element continue except when j equals one of these indices that we want to store
            if(( count == 2 || count == 5) && j != 14 && j != 44 && j != 74 && j != 83 ){
                count=0;
                continue;
            }
            bufferrow[r] = new Complex(gestureGeneralBuffer.get(j),0);  //add every value into the array as the real part of a complex number
            r++;
            count++;
        }
        fft(bufferrow); //call the FFT function to transform the incoming data
        svm_node node;  // declare an svm_node object
        for(int a = 0; a < bufferrow.length; a++){  //for all values in the array
            absFFTbufferForFloat[a] = (float) (bufferrow[a].abs()/fftSetValuesCount);    //add the absolute value of every complex number in the new array
            node = new svm_node();  //make a new svm_node
            node.index = a+1;   //index always starts form 1
            node.value = absFFTbufferForFloat[a];   //add the value
            nodes1[a] = node;   //assign every node in a new cell of the svm_node array
        }

        return nodes1;  //return the svm_node array
    }

    /** datasetFFT method transforms the data set gesture data using FFT, calculates the absolute value
     *  and exports the complex numbers and their absolute values into csv files */
    public void datasetFFT(){

        // its the exact same function as above except it doesn't return svm_node array and it exports the
        // transformation data in csv files
        fftDataset = new Complex[dataSet.length][fftSetValuesCount];
        int count = 0;
        for(int i = 0; i < dataSet.length; i++){
            int r = 0;
            for(int j = 0; j<dataSet[0].length; j++){
                if(( count == 2 || count == 5) && j != 14 && j != 44 && j != 74 && j != 83 ){
                    count=0;
                    continue;
                }
                row[r] = new Complex(dataSet[i][j],0);
                r++;
                count++;
            }
            fft(row);
            for(int k = 0; k<fftSetValuesCount; k++){
                fftDataset[i][k] = row[k];
            }
        }
        exportDataToCSV(new float[]{1},filePathFFT, fFFT, false, "");  //export complex numbers to CSV
        exportDataToCSV(new float[]{},filePathFFTfloat, fFFTfloat, false, ""); //export absolute values of complex numbers to CSV
    }

    /** findGestureWithFFTDataSVM method calls the svm_predict_values method and returns
     *  the decision value average in order to classify the incoming gesture
     *  @param model is the trained model with the given dataSet
     *  @param incoming is the incoming gesture data in svm_node format*/

    public int findGestureWithFFTDataSVM(svm_model model, svm_node[] incoming){
        double[] scores = new double[10];   //array that stores the decision values when svm_predict_values returns them
        double sum = 0; //the sum of all decision values

        System.out.println("First and second elements of the model --->\t"+model.SV[0][0].value+", "+model.SV[0][1].value);
        System.out.println("First and second elements of the INCOMING --->\t"+incoming[0].value+", "+incoming[1].value);
        double result = svm.svm_predict_values(model, incoming, scores);    //call the prediction method (the result value is used only in two class classification)

        System.out.println("The result ---> "+result);
        for(double sc : scores) {
            sum += sc;  //sum up all the decision values
        }
        System.out.println("The SCORES --> "+Arrays.toString(scores));
        sum /= scores.length;   //find the average
        System.out.println("The avg of scores ---> "+sum);

        // this if-statement returns the index number of each gesture so that the findGestureClass method can update the UI accordingly
        //it can be later implemented in the findGestureClass if everything is working flawlessly
        if(sum > -0.18 && sum < -0.14){ // gesture hh
            return 20;
        }
        else if(sum > -0.26 && sum < -0.22){    //gesture hu
            return 40;
        }
        else if(sum > -0.37 && sum < -0.33){    //gesture hud
            return 60;
        }
        else if(sum > -0.22 && sum < -0.18){    //gesture hh2
            return 80;
        }
        else if(sum > -0.33 && sum < -0.26){    //gesture hu2
            return 100;
        }
        //degree = 2 gamma = 0.02

//        if(scores[0] > -1.1 && scores[0] < -1.08){
//            return 20;
//        }
//        else if(scores[0] > -1.07 && scores[0] < -1.03){
//            return 40;
//        }
//        else if(scores[0] > -0.8 && scores[0] < -0.5){
//            return 60;
//        }
//        else if(scores[0] > -1.09 && scores[0] < -1.079){
//            return 80;
//        }
//        else if(scores[0] > -1.0 && scores[0] < -0.8){
//            return 100;
//        }
//        if(result == 1.0){
//            return 20;
//        }
//        else if(result == 2.0){
//            return 40;
//        }
//        else if(result == 3.0){
//            return 60;
//        }
//        else if(result == 4.0){
//            return 80;
//        }
//        else if(result == 5.0){
//            return 100;
//        }

            return 200; //if all fails return an irrelevant number for classification
    }

    /** importSVMmodelDataAndBuild method is building a new svm_model with custom parameters given from the developer
     * to overcome the fact that svm_train was not working
     * @param path1 the file path to the file that contains the coefs array
     * @param path2 the file path to the file that contains the SV array
     * @param path3 the file path to the file that contains the rho array*/

    public svm_model importSVMmodelDataAndBuild(String path1, String path2, String path3) throws IOException{

        model.sv_coef = new double[4][108]; //array that stores the coefs
        model.rho = new double[10]; //array that stores the rhos
        model.SV = new svm_node[108][fftSetValuesCount];   //array that stores the SVs
        String thisLine, separator =",";    //the separators used in the files are commas

        //the next three if-statements all initialize a new reader and add every new value they find in the corresponding array
        if(path1.contains("coefs")){
            BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(path1))));
            int i , j = 0;
            while ((thisLine = myInput.readLine()) != null) {
                i=0;
                for(String currLine : thisLine.split(separator)){
                    model.sv_coef[j][i] = Double.parseDouble(currLine);
                    i++;
                }
                j++;
            }
            myInput.close();
        }
            if(path2.contains("SV")){
            BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(path2))));
            int i , j = 0;
            while ((thisLine = myInput.readLine()) != null) {
                i=0;
                for(String currLine : thisLine.split(separator)){
                    svm_node node1 = new svm_node();
                    node1.index = i+1;
                    node1.value = Double.parseDouble(currLine);
                    model.SV[j][i] = node1;
                    i++;
                }
                j++;
            }
            myInput.close();
        }

        if(path3.contains("rho")){
            BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(path3))));
            int i;
            while ((thisLine = myInput.readLine()) != null) {
                i=0;
                for(String currLine : thisLine.split(separator)){
                    model.rho[i] = Double.parseDouble(currLine);
                    i++;
                }
            }
            myInput.close();
        }
        //INITIALIZING EVERY MODEL FIELD AND PARAMETER
        ///////////////////////////////////////////////////////////
        model.label = new int[5];
        model.nSV = new int[5];
        model.nr_class = 5;
        model.l = 108;
        model.probA = new double[0];
        model.probB = new double[0];
        model.label[0] = 1;
        model.label[1] = 2;
        model.label[2] = 3;
        model.label[3] = 4;
        model.label[4] = 5;
        model.nSV[0] = 16;
        model.nSV[1] = 24;
        model.nSV[2] = 23;
        model.nSV[3] = 23;
        model.nSV[4] = 22;

        model.param = new svm_parameter();
        model.param.svm_type    = svm_parameter.C_SVC;
        model.param.kernel_type = svm_parameter.RBF;
        model.param.degree = 3;
        //model.param.svm_type    = svm_parameter.NU_SVC;
        //model.param.kernel_type = svm_parameter.POLY;
        //model.param.degree = 2;
        model.param.gamma       = 0.015625;//0.02;//0.015513;
        model.param.nu          = 0.5;
        model.param.eps = 0.1;
        model.param.cache_size  = 100;
        ///////////////////////////////////////////////////////////

        return model;
    }

    //NOT SURE ABBOUT THAT YET I MAY GET IT TO WORK
//    public svm_model buildModel(double[][] input){
//
//        svm_parameter param = new svm_parameter();
//        param.svm_type    = svm_parameter.C_SVC;
//        param.kernel_type = svm_parameter.RBF;
//        param.degree = 3;
//        param.gamma       = 0.015625;
//        param.nu          = 0.5;
//        param.cache_size  = 100;
//
//        svm_problem problem = new svm_problem();
//        problem.y = new double[input.length];
//        problem.x = new svm_node[120][64];
//
//        nodes = new svm_node[input.length][input[0].length-1];
//
//        for(int i = 0; i < 120; i++){
//            problem.x[i] = new svm_node[64];
//            for(int j =0 ; j < 64; j++){
//                svm_node node = new svm_node();
//                node.index = j+1;
//                node.value = input[i][j];
//                nodes[i][j] = node;
//                //problem.x[i][j] = node;
//            }
//            problem.y[i] = input[i][64];
//        }
//
//        problem.x = nodes;
//        problem.l = input.length;
//
//        return svm.svm_train(problem, param);
//    }

    //-------------------------------------------------------------------------------------------------//

    //------------------------- Methods Used in Gesture Recognition Using $3 --------------------------//



    //-------------------------------------------------------------------------------------------------//

    //------------------------------------------- CSV Tools -------------------------------------------//

    /** initializeFromCSV method adds all the raw data from the TransposedData.csv file in an array for further use */
    public void initializeFromCSV(boolean global, String filepath){

        try{
            FileReader read = new FileReader(filepath); //initialize file reader
            reader = new CSVReader(read);
            if(!global){
                List<String[]> lines; //lines in file
                changed = false;
                lines = reader.readAll();
                int linesInFile = lines.size()-1;
                dataSet = new float[linesInFile][(lines.get(0).length)-1];
                int x = 0;
                hhCountPersonal = 0;
                huCountPersonal = 0;
                hudCountPersonal = 0;
                hh2CountPersonal = 0;
                hu2CountPersonal = 0;
                for(String[] Lines : lines){
                    if(x > 0){
                        for(int z = 0; z < dataSet[0].length; z++) {
                            dataSet[x-1][z] = Float.parseFloat(Lines[z]);
                            if( z == dataSet[0].length-1){
                                switch (Lines[z+1]){
                                    case "hh":
                                        hhCountPersonal++;
                                        System.out.println("hhCountPersonal ---> \t"+hhCountPersonal);
                                        break;
                                    case "hu":
                                        huCountPersonal++;
                                        System.out.println("huCountPersonal ---> \t"+huCountPersonal);
                                        break;
                                    case "hud":
                                        hudCountPersonal++;
                                        System.out.println("hudCountPersonal ---> \t"+hudCountPersonal);
                                        break;
                                    case "hh2":
                                        hh2CountPersonal++;
                                        System.out.println("hh2CountPersonal ---> \t"+hh2CountPersonal);
                                        break;
                                    case  "hu2":
                                        hu2CountPersonal++;
                                        System.out.println("hu2CountPersonal ---> \t"+hu2CountPersonal);
                                        break;
                                    default:
                                        System.out.println("Something went wrong!");
                                        break;
                                }
                            }
                        }

                    }
                    x++;
                }
//                reader.close();
//                reader = new CSVReader(read);
//                int i = 0,j = 0;
//                String[] linesOut = reader.readNext();  //the first line is the header for X, Y and Z
//                System.out.println("----------------------------------------------------------------------------------------------------------------"+dataSet[i][j]);
//                while((lines = reader.readNext()) != null){
//                    System.out.println("*****************************************************************************************************************");
//                    for(String current : lines){
//                        if(j==90)
//                            break;
//                        dataSet[i][j] = Float.parseFloat(current);  //parse every value from the file into the array
//                        System.out.println(dataSet[i][j]);
//                        j++;
//                    }
//                    j = 0;
//                    i++;
//                }
            }
            else{
                String[] lines;
                changed = true;
                hhCount = 0;
                huCount = 0;
                hudCount = 0;
                hh2Count = 0;
                hu2Count = 0;
                //List<List<Float>> input = new ArrayList<List<Float>>();
                List<float[]> input = new ArrayList<float[]>();
                int j = 0;
                while((lines = reader.readNext()) != null){
                    float[] lineBuffer = new float[91];
                    //List<Float> lineBuffer = new ArrayList<Float>(91);
                    for(String current : lines){
                        if(j==90){
                            switch (current) {
                                case "hh":
                                    //lineBuffer.add(1f);
                                    lineBuffer[j] = 1;
                                    input.add(0, lineBuffer);
//                                    System.out.println("HH");
//                                    System.out.println(Arrays.toString(input.get(0)));
                                    hhCount++;
                                    //System.out.println("hhCount --->\t" + hhCount);
                                    break;
                                case "hu":
                                    //lineBuffer.add(2f);
                                    lineBuffer[j] = 2;
                                    input.add(hhCount,lineBuffer);
//                                    System.out.println("lineBufferIndex --->\t" + hhCount);
//                                    System.out.println("HU");
//                                    System.out.println(Arrays.toString(input.get(hhCount)));
                                    huCount++;
                                    //System.out.println("huCount --->\t" + huCount);
                                    break;
                                case "hud":
                                    //lineBuffer.add(3f);
                                    lineBuffer[j] = 3;
                                    input.add(hhCount+huCount,lineBuffer);
//                                    System.out.println("lineBufferIndex --->\t" + (hhCount+huCount));
//                                    System.out.println("HUD");
//                                    System.out.println(Arrays.toString(input.get(hhCount+huCount)));
                                    hudCount++;
                                    //System.out.println("hudCount --->\t" + hudCount);
                                    break;
                                case "hh2":
                                    //lineBuffer.add(4f);
                                    lineBuffer[j] = 4;
                                    input.add(hhCount+huCount+hudCount,lineBuffer);
//                                    System.out.println("lineBufferIndex --->\t" + (hhCount+huCount+hudCount));
//                                    System.out.println("HH2");
//                                    System.out.println(Arrays.toString(lineBuffer));
                                    hh2Count++;
                                    //System.out.println("hh2Count --->\t" + hh2Count);
                                    break;
                                case "hu2":
                                    //lineBuffer.add(5f);
                                    lineBuffer[j] = 5;
                                    input.add(hhCount+huCount+hudCount+hh2Count,lineBuffer);
//                                    System.out.println("lineBufferIndex --->\t" + (hhCount+huCount+hudCount+hh2Count));
//                                    System.out.println("HU2");
//                                    System.out.println(Arrays.toString(lineBuffer));
                                    hu2Count++;
                                    //System.out.println("hu2Count --->\t" + hu2Count);
                                    break;
                                default:
                                    break;
                            }
                            break;
                        }
                        //globalDataSet[i][j] = Float.parseFloat(current);  //parse every value from the file into the array
                        lineBuffer[j] = Float.parseFloat(current);//lineBuffer.add(Float.parseFloat(current));
                        j++;
                    }
                    j = 0;
                    //System.out.println("Outer Array Size (Recordings)---> \t"+ input.size()+ "\nInner Array Size (Values)---> \t" + input.get(0).length);
                }
//                System.out.println("Cell 1");
//                System.out.println(Arrays.toString(input.get(0).toArray()));
//                System.out.println("Cell 2");
//                System.out.println(Arrays.toString(input.get(1).toArray()));
//                System.out.println("Cell 3");
//                System.out.println(Arrays.toString(input.get(2).toArray()));
//                System.out.println("Cell 4");
//                System.out.println(Arrays.toString(input.get(3).toArray()));
//                System.out.println("Cell 5");
//                System.out.println(Arrays.toString(input.get(4).toArray()));
//                System.out.println("Cell 6");
//                System.out.println(Arrays.toString(input.get(5).toArray()));
//                System.out.println("Cell 7");
//                System.out.println(Arrays.toString(input.get(6).toArray()));

                //globalDataSet = new float[input.size()][input.get(0).size()];
                globalDataSet = new float[input.size()][input.get(0).length];
                for(int i = 0; i < globalDataSet.length; i++){
                    for(int k = 0; k < globalDataSet[0].length; k++){
                        globalDataSet[i][k] = input.get(i)[k];//.get(k);
                    }
                    //System.out.println(Arrays.toString(globalDataSet[i]));
                }
            }
            reader.close();
        }catch(Exception e){
            e.printStackTrace();    //handle exceptions
        }

    }

    /** exportDataToCSV method writes training data, complex data after FFT and absolute values of the FFT into a CSV file
     * @param accData The data to be exported
     * @param fp The path to the file
     * @param FL  The file name*/

    public void exportDataToCSV(float[] accData, String fp, File FL, boolean transposed, String value){

        try{
        if(FL.exists()&&!FL.isDirectory()) {    //if the file already exists
            FileWriter mFileWriter = new FileWriter(fp, true);  //append to it
            writer = new CSVWriter(mFileWriter);    //initialize the writer
        }
        else {
            writer = new CSVWriter(new FileWriter(fp)); //else initialize a writer to write a new file
        }

        if(accData.length == 3) {   //if the method is called with an array of length 3
            if(!transposed){
                String[] val = new String[accData.length];  //array that holds the incoming gesture values in String format
                val[0] = Float.toString(accData[0]);        //add the values to the array
                val[1] = Float.toString(accData[1]);
                val[2] = Float.toString(accData[2]);

                writer.writeNext(val);  //write or append the array in the next line of the file
            }
            else{
                String[] val = new String[gestureGeneralBuffer.size()+1];
                for(int i = 0; i < val.length-1; i++){
                    val[i] = Float.toString(gestureGeneralBuffer.get(i));
                }
                val[gestureGeneralBuffer.size()] = value;
                writer.writeNext(val,false);

            }

        }
        else {  //else if the method is called with a different number of array cells
            String[] val = new String[65];  //array that stores 64 values of the transformed data and the 65th is the class
            fftFloatDataset = new double[fftDataset.length][65];
            int TRAINING_ONCE = 0;
            for(int j = 0; j< fftDataset.length; j++){
                for(int k = 0; k<fftDataset[0].length; k++){
                    //if in training mode and array length is 1 store the string values of the complex numbers
                    if(accData.length == 1 && TRAINING_ONCE == 1) val[k] = fftDataset[j][k].toStringZ();
                    //else if in training mode and array length is 0 store the string values of the absolute values
                    else if(accData.length == 0 && TRAINING_ONCE == 1) val[k] = Float.toString((float)(fftDataset[j][k].abs()/fftSetValuesCount));
                    //else just store the absolute values of the transformed numbers into an array for further use
                    else fftFloatDataset[j][k] = (fftDataset[j][k].abs()/fftSetValuesCount);
                }
                //and in this if-statement, assign to the last cell of each row which class this data row belongs to
                if(j<hhCountPersonal){fftFloatDataset[j][64] = 1; if(TRAINING_ONCE == 1)val[64] = "hh";}       //first 24 records belong to the hh class
                else if(j<hhCountPersonal+huCountPersonal){fftFloatDataset[j][64] = 2; if(TRAINING_ONCE == 1)val[64] = "hu";}  //next 24 records belong to the hu class
                else if(j<hhCountPersonal+huCountPersonal+hudCountPersonal){fftFloatDataset[j][64] = 3; if(TRAINING_ONCE == 1)val[64] = "hud";} //next 24 records belong to the hud class
                else if(j<hhCountPersonal+huCountPersonal+hudCountPersonal+hh2CountPersonal){fftFloatDataset[j][64] = 4; if(TRAINING_ONCE == 1)val[64] = "hh2";} //next 24 records belong to the hh2 class
                else if(j<hhCountPersonal+huCountPersonal+hudCountPersonal+hh2CountPersonal+hu2CountPersonal){fftFloatDataset[j][64] = 5; if(TRAINING_ONCE == 1)val[64] = "hu2";}//last 24 records belong to the hu2 class

                writer.writeNext(val,false);    //write the values in the next line of the file
            }
        }
            writer.close(); //close the writer
        } catch (IOException e) {
            e.printStackTrace();    //handle exceptions
        }
    }

    interface DialogInputInterface {
        // onBuildDialog() is called when the dialog builder is ready to accept a view to insert
        // into the dialog
        View onBuildDialog();
        // onCancel() is called when the user clicks on 'Cancel'
        void onCancel();
        // onResult(View v) is called when the user clicks on 'Ok'
        void onResult(View v);
    }

    void promptForResult(String dlgTitle, String dlgMessage, final DialogInputInterface dlg) {
        // replace "MyClass.this" with a Context object. If inserting into a class extending Activity,
        // using just "this" is perfectly ok.
        AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
        alert.setTitle(dlgTitle);
        alert.setMessage(dlgMessage);
        // build the dialog
        final View v = dlg.onBuildDialog();
        // put the view obtained from the interface into the dialog
        if (v != null) { alert.setView(v);}
        // procedure for when the ok button is clicked.
        alert.setPositiveButton("Ok", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int whichButton) {
                // ** HERE IS WHERE THE MAGIC HAPPENS! **
                dlg.onResult(v);
                dialog.dismiss();
                return;
            }
        });

        alert.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                dlg.onCancel();
                dialog.dismiss();
                return;
            }
        });
        alert.show();
    }

    //-------------------------------------------------------------------------------------------------//
}
