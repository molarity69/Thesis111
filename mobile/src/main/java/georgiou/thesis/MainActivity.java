package georgiou.thesis;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.content.pm.PackageManager;
import android.media.MediaPlayer;
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
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Random;

import static ca.pfv.spmf.algorithms.timeseries.sax.MainTestSAX_SingleTimeSeries.constant;
import static georgiou.thesis.FFT.fft;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestConvertTimeSeriesFiletoSequenceFileWithSAX;
import ca.pfv.spmf.algorithms.timeseries.sax.MainTestSAX_SingleTimeSeries;

import de.daslaboratorium.machinelearning.classifier.Classifier;
import de.daslaboratorium.machinelearning.classifier.bayes.BayesClassifier;
import edu.berkeley.compbio.jlibsvm.binary.BinaryModel;
import edu.berkeley.compbio.jlibsvm.binary.C_SVC;
import edu.berkeley.compbio.jlibsvm.binary.MutableBinaryClassificationProblemImpl;
import edu.berkeley.compbio.jlibsvm.kernel.GaussianRBFKernel;
import edu.berkeley.compbio.jlibsvm.labelinverter.StringLabelInverter;
import edu.berkeley.compbio.jlibsvm.multi.MultiClassModel;
import edu.berkeley.compbio.jlibsvm.multi.MultiClassProblemImpl;
import edu.berkeley.compbio.jlibsvm.multi.MultiClassificationSVM;
import edu.berkeley.compbio.jlibsvm.multi.MutableMultiClassProblemImpl;
import edu.berkeley.compbio.jlibsvm.scaler.ScalingModel;
import edu.berkeley.compbio.jlibsvm.util.SparseVector;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import edu.berkeley.compbio.jlibsvm.*;

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
    private String chosenAlgorithm = "0";  //checking which button is pressed

    public BluetoothAdapter mBluetoothAdapter; //bluetooth initializer
    public String datapath = "/data_path"; //path for bluetooth communication

    private float[][] dataSet = new float[120][90]; //array that holds the imported data set from CSV file
    private Complex[][] fftDataset = new Complex[120][64];  //array that holds the imported data set in Complex type
    int[][] txtVals = new int[120][constant];
    double[][] fftFloatDataset = new double[120][65];
    public static svm_node[][] nodes;
    public static svm_node[] nodes1;

    private Complex[] row = new Complex[64];    //array that helps with copying each imported data row to the data set after transformation
    private Complex[] bufferrow = new Complex[64];  //same as above but for the buffer array that holds data for recognition

    public static String baseDir = Environment.getExternalStoragePublicDirectory("/DCIM").getAbsolutePath();    //path to phone storage folder
    public svm_model model = new svm_model();

    String[] absFFTbuffer = new String[64]; //array that keeps the absolute values of the data after being transformed

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
    String modelName = "svm.model";
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

    Classifier<String, String>bayes = new BayesClassifier<String, String>();

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

        initializeFromCSV(); //method for loading raw data from csv for transforming them into a new data set (used during development)
        datasetFFT();
        writeSAXtoTXT();
        try{
            MainTestConvertTimeSeriesFiletoSequenceFileWithSAX.main(null);
            initializeFromSAXtxt();
            importSVMmodelDataAndBuild(filePathCoefsRead, filePathSVRead, filePathRhoRead);
        }catch (IOException e){
            e.printStackTrace();
        }


        //model = buildModel(fftFloatDataset);
//        for(int i = 0; i<model.SV.length;i++){
//            for(int j=0;j<model.SV[0].length;j++){
//                System.out.println("NODES IN MODEL --->\t"+model.SV[i][j].value);
//            }
//            if(i<4){
//                System.out.println("NODES IN SV_COEF --->\t"+Arrays.toString(model.sv_coef[i]));
//            }
//            if(i<10){
//                System.out.println("NODES IN RHO --->\t"+model.rho[i]);
//            }
//        }
//
//
//        System.out.println("Model.l --->\t"+model.l);
//        System.out.println("NUMBER OF CLASSES --->\t"+model.nr_class);
//        System.out.println("LABELS --->\t"+Arrays.toString(model.label));
//        System.out.println("C --->\t"+model.param.C);
//        System.out.println("Cache size --->\t"+model.param.cache_size);
//        System.out.println("Degree --->\t"+model.param.degree);
//        System.out.println("eps --->\t"+model.param.eps);
//        System.out.println("gamma --->\t"+model.param.gamma);
//        System.out.println("kernel type --->\t"+model.param.kernel_type);
//        System.out.println("nr weight --->\t"+model.param.nr_weight);
//        System.out.println("nu --->\t"+model.param.nu);
//        System.out.println("p --->\t"+model.param.p);
//        System.out.println("probability --->\t"+model.param.probability);
//        System.out.println("shrinking --->\t"+model.param.shrinking);
//        System.out.println("svm type --->\t"+model.param.svm_type);
//        System.out.println("weight --->\t"+Arrays.toString(model.param.weight));
//        System.out.println("weight label --->\t"+Arrays.toString(model.param.weight_label));
//        System.out.println("Coef0 --->\t"+model.param.coef0);
        //TrainNaiveBayes();
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
                    if(chosenAlgorithm.equals("train")) {
                        exportDataToCSV(new float[]{30.00f, 30.00f, 30.00f}, filePath, f);  //calling method with 3 float cells having the value of 30 to distinguish where every new gesture starts (used during development)
                    }
                    start = true;
                    recordedCount++;
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
                    //findGestureClass(findGestureWithFFTDataSVM(model, bufferFFT()), "");
                    findGestureClass(findGestureWithFFTDataSVM(model,bufferFFT()));
                    int[] label = new int[5];
                    svm.svm_get_labels(model, label);
                    System.err.println("SHO ME DA LABLE ---> "+Arrays.toString(label));
                    //bayes.setMemoryCapacity(5000);
                    //System.out.println(((BayesClassifier<String, String>) bayes).classifyDetailed(Arrays.asList(bufferFFT())));
                    //findGestureClass(200, bayes.classify(Arrays.asList(bufferFFT())).getCategory());
                    gestureGeneralBuffer.clear();
                }
                else{
                    Toast.makeText(this, "Record something to process!",Toast.LENGTH_LONG).show();
                }

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
                        findGestureWithSAXDataEuclidean(liveSAX);

                        for (int sax : liveSAX) {
                            Log.e(TAG, "USER INPUT -----> \t" + sax);
                        }

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
            coords.setText("Recorded Gestures: " + gesturesComplete + "\nBuffer: " + gestureGeneralBuffer.size());
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
                            if(chosenAlgorithm.equals("train")){
                                exportDataToCSV(new float[]{-30.00f, -30.00f, -30.00f}, filePath, f);
                                Toast.makeText(this, "Recording Exported as Training Data!", Toast.LENGTH_LONG).show();
                            }
                            else {
                                Toast.makeText(this, "Recording completed", Toast.LENGTH_LONG).show();
                            }
                            start = false;
                            recordedCount++;
                            gesturesComplete++;
                        }
                        else {
                            dataBuffer();
                            logthis();
                            if(chosenAlgorithm.equals("train")){
                                exportDataToCSV(values,filePath,f);
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

        recordedCount++;

        if(gestureValuesBufferX.size() == 30 && gestureValuesBufferY.size() == 30 && gestureValuesBufferZ.size() == 30) {

            if(gestureGeneralBuffer.size() >= 90){
                gestureGeneralBuffer.clear();
            }

            gestureGeneralBuffer.addAll(0, gestureValuesBufferX);
            gestureGeneralBuffer.addAll(30, gestureValuesBufferY);
            gestureGeneralBuffer.addAll(60, gestureValuesBufferZ);
            gestureValuesBufferX.clear();
            gestureValuesBufferY.clear();
            gestureValuesBufferZ.clear();
        }
    }

    public void initializeFromSAXtxt() throws IOException {

        BufferedReader myInput = new BufferedReader(new InputStreamReader( new FileInputStream(new File(filePathSAXRead))));
        String thisLine, separator =",";
        int i , j = 0;
        while ((thisLine = myInput.readLine()) != null) {
            if(thisLine.charAt(0) == '@'){ continue; }
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
        myInput.close();
    }

    public void findGestureClass(int index){

        if(index < 24 /*|| class1.equals("hh")*/) { gestureRecognized.setText(R.string.currGestHH); playDaSound(R.raw.hh); }
        else if (index < 48/* || class1.equals("hu")*/) { gestureRecognized.setText(R.string.currGestHU); playDaSound(R.raw.hu); }
        else if (index < 72 /*|| class1.equals("hud")*/) { gestureRecognized.setText(R.string.currGestHUD); playDaSound(R.raw.hud); }
        else if(index < 96 /*|| class1.equals("hh2")*/) { gestureRecognized.setText(R.string.currGestHH2); playDaSound(R.raw.hh2); }
        else if (index < 120 /*|| class1.equals("hu2")*/) { gestureRecognized.setText(R.string.currGestHU2); playDaSound(R.raw.hu2); }
        else System.out.println("LET'S HOPE I WONT BE PRINTED");
    }

    /////////////////////////////////////////////////////////////////////////////////////////////////////////////////////// SAX

    public void findGestureWithSAXDataEuclidean(int[] sax){

        int[] diff = new int[txtVals[0].length];
        double[][] diffDev = new double[txtVals.length][txtVals[0].length];
        double[] diffSum = new double[txtVals.length];
        double dev = 0.16;
        double min = 100;
        for(int i = 0; i < txtVals.length; i++){
            for(int j = 0; j < txtVals[0].length; j++){
                diff[j] = sax[j] - txtVals[i][j];
                if(diff[j] < -1 || diff[j] > 1){
                    diffDev[i][j] = (Math.abs(diff[j])-1) * dev;
                }
                else{
                    diffDev[i][j] = 0;
                }
            }
        }
        for(int i = 0; i<diffDev.length; i++){
            for( int j = 0; j<diffDev[0].length; j++){
                diffSum[i] += diffDev[i][j];
            }
            min = (diffSum[i] < min) ? diffSum[i] : min;
            if(min == 0) { findGestureClass(i); break; }
        }
        Log.e(TAG, "recognitionAlgo: MINIMUM VALUE -->\t" + min);
        int index = findIndex(diffSum,min);
        findGestureClass(index);
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

    public String[] bufferSAX(){

        String[] buffData = new String[90];
        for(int i = 0; i<gestureGeneralBuffer.size(); i++){
            buffData[i] = gestureGeneralBuffer.get(i).toString();
        }
        return buffData;
    }

    public void writeSAXtoTXT(){

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

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////////////////// FFT TOOLS AND SVM

    public svm_node[]/* String[] */ bufferFFT(){

        int count = 0;

        float[] absFFTbufferForFloat = new float[64];
        nodes1 = new svm_node[absFFTbufferForFloat.length];
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
        svm_node node;
        for(int a = 0; a < bufferrow.length; a++){
            absFFTbufferForFloat[a] = (float) (bufferrow[a].abs()/64.0);
            node = new svm_node();
            node.index = a+1;
            node.value = absFFTbufferForFloat[a];
            nodes1[a] = node;
            //absFFTbuffer[a] = String.valueOf(absFFTbufferForFloat[a]);
            //absFFTbuffer[a] = String.valueOf(absFFTbufferForFloat);
//            nodes1[a] = new svm_node();
//            nodes1[a].value = absFFTbufferForFloat[a];
//            nodes1[a].index = a+1;
            //Log.d((a+1)+" --> ",  "USER INPUT ---> " + absFFTbufferForFloat[a] + System.lineSeparator() +"INCOMING DATA INDEX --->\t" + nodes[a].index);
        }

        return nodes1;//absFFTbuffer;
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

    public int findGestureWithFFTDataSVM(svm_model model, svm_node[] incoming){
        double[] scores = new double[10];

        System.out.println("First and second elements of the model --->\t"+model.SV[0][0].value+", "+model.SV[0][1].value);
        System.out.println("First and second elements of the INCOMING --->\t"+incoming[0].value+", "+incoming[1].value);
        double result = svm.svm_predict_values(model, incoming, scores);

        System.out.println("The result ---> "+result);
        for(double sc : scores)
            System.out.println("The SCORES --> "+sc);

        if(result == 1.0){
            return 20;
        }
        else if(result == 2.0){
            return 40;
        }
        else if(result == 3.0){
            return 60;
        }
        else if(result == 4.0){
            return 80;
        }
        else if(result == 5.0){
            return 100;
        }

            return 200;
    }

    public double[][] scrambleData(double[][] input){

        // Creating a object for Random class
        Random r = new Random();
        // Start from the last element and swap one by one. We don't
        // need to run for the first element that's why i > 0
        for (int i = input.length-1; i > 0; i--) {
            // Pick a random index from 0 to i
            int j = r.nextInt(i+1);
            double[] temp = new double[input[0].length];
            for(int k = 0; k< input[0].length; k++){
                // Swap arr[i] with the element at random index
                temp[k] = input[i][k];
                input[i][k] = input[j][k];
                input[j][k] = temp[k];
            }
        }
        return input;
    }

    public svm_model importSVMmodelDataAndBuild(String path1, String path2, String path3) throws IOException{

        model.sv_coef = new double[4][108];
        model.rho = new double[10];
        model.SV = new svm_node[108][64];
        svm_node[][] node = new svm_node[108][64];
        String thisLine, separator =",";
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
                    //node[j][i] = node1;
                    //System.out.println("Node j: "+j+" Node: i: "+i+"\tContains: "+node[j][i].value);
                    //System.out.println("Node j: "+j+" Node: i: "+i+"\tContains: "+model.SV[j][i].value);
//                    model.SV[i][j] = new svm_node();
//                    model.SV[j][i].index = i+1;
//                    model.SV[j][i].value = Double.parseDouble(currLine);
                    //System.out.println("INDEX OF NODE --->\t"+model.SV[j][i].index+"\tVALUE OF NODE --->\t" + model.SV[j][i].value + "\tINCOMING VALUE FROM FILE --->\t" + Double.parseDouble(currLine));
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
        //model.SV = node;

//        for(int i=0;i<model.SV.length;i++){
//            for(int j=0;j<model.SV[0].length;j++){
//                System.out.println("NODES IN METHOD --->\t"+model.SV[i][j].value);
//            }
//        }



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
        model.param.svm_type    = svm_parameter.NU_SVC;
        model.param.kernel_type = svm_parameter.LINEAR;
        model.param.degree = 3;
        model.param.gamma       = 0.015625;
        model.param.nu          = 0.5;
        model.param.eps = 0.1;
        model.param.cache_size  = 100;

        return model;
    }

    public svm_model buildModel(double[][] input){

        svm_parameter param = new svm_parameter();
        param.svm_type    = svm_parameter.C_SVC;
        param.kernel_type = svm_parameter.RBF;
        param.gamma       = 0.015625;
        param.nu          = 0.5;
        param.cache_size  = 100;

        svm_problem problem = new svm_problem();
        problem.y = new double[input.length];
        problem.x = new svm_node[120][64];

        nodes = new svm_node[input.length][input[0].length-1];

        for(int i = 0; i < 120; i++){
            problem.x[i] = new svm_node[64];
            for(int j =0 ; j < 64; j++){
                svm_node node = new svm_node();
                node.index = j+1;
                node.value = input[i][j];
                nodes[i][j] = node;
                //problem.x[i][j] = node;
            }
            problem.y[i] = input[i][64];
        }


        problem.x = nodes;
        problem.l = input.length;

//        try {
//            svm.svm_load_model(filePathModelName);
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

//        for(int i=0;i<120;i++){
//            for(int j=0;j<64;j++){
//                System.out.println("inputArray dataset --->\t" + input[i][j]);
//                System.out.println("Array NODES to X --->\t" + nodes[i][j].value);
//                System.out.println("Array problem.x --->\t" + problem.x[i][j].value);
//            }
//        }


        return svm.svm_train(problem, param);
    }

    public void TrainNaiveBayes(){
        String[] hh = new String[64], hu = new String[64], hud = new String[64], hh2 = new String[64], hu2 = new String[64];
        for(int i = 0; i < 120; i++){
            for(int j = 0; j < 64; j++){
                if(i < 24){
                    hh[j] = String.valueOf(fftFloatDataset[i][j]);
                    System.out.println("hh[j] ---> \t"+hh[j] + "\ti --->\t" + i + "\tj --->\t" + j);
                }
                else if(i < 48){
                    hu[j] = String.valueOf(fftFloatDataset[i][j]);
                    System.out.println("hu[j] ---> \t"+hu[j] + "\ti --->\t" + i + "\tj --->\t" + j);
                }
                else if(i < 72){
                    hud[j] = String.valueOf(fftFloatDataset[i][j]);
                    System.out.println("hud[j] ---> \t"+hud[j] + "\ti --->\t" + i + "\tj --->\t" + j);
                }
                else if(i < 96){
                    hh2[j] = String.valueOf(fftFloatDataset[i][j]);
                    System.out.println("hh2[j] ---> \t"+hh2[j] + "\ti --->\t" + i + "\tj --->\t" + j);
                }
                else {
                    hu2[j] = String.valueOf(fftFloatDataset[i][j]);
                    System.out.println("hu2[j] ---> \t"+hu2[j] + "\ti --->\t" + i + "\tj --->\t" + j);
                }
            }
            if(i < 24){
                bayes.learn("hh", Arrays.asList(hh));
            }
            else if(i < 48){
                bayes.learn("hu", Arrays.asList(hu));
            }
            else if(i < 72){
                bayes.learn("hud", Arrays.asList(hud));
            }
            else if(i < 96){
                bayes.learn("hh2", Arrays.asList(hh2));
            }
            else {
                bayes.learn("hu2", Arrays.asList(hu2));
            }
        }
    }


    ///////////////////////////////////////////////////////////////////////////////////////////////////////////

    /////////////////////////////////////////////////////////////////////////////////////////////////////////// CSV TOOLS

    public void initializeFromCSV(){

        try{
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
        if(FL.exists()&&!FL.isDirectory()) {
            FileWriter mFileWriter = new FileWriter(fp, true);
            writer = new CSVWriter(mFileWriter);
        }
        else {
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
            String[] val = new String[65];
            for(int j = 0; j< fftDataset.length; j++){
                for(int k = 0; k<fftDataset[0].length; k++){
                    if(accData.length == 1) val[k] = fftDataset[j][k].toStringZ();
                    else val[k] = Float.toString((float)(fftDataset[j][k].abs()/64.0));
                    fftFloatDataset[j][k] = (fftDataset[j][k].abs()/64.0);
                }
                if(j<24){fftFloatDataset[j][64] = 1; val[64] = "hh";}
                else if(j<48){fftFloatDataset[j][64] = 2; val[64] = "hu";}
                else if(j<72){fftFloatDataset[j][64] = 3; val[64] = "hud";}
                else if(j<96){fftFloatDataset[j][64] = 4; val[64] = "hh2";}
                else if(j<120){fftFloatDataset[j][64] = 5; val[64] = "hu2";}

                writer.writeNext(val,false);
            }
        }
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    ///////////////////////////////////////////////////////////////////////////////////////////////////////////
}
