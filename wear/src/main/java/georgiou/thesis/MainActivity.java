package georgiou.thesis;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;
import android.widget.TextView;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;


public class MainActivity extends WearableActivity implements SensorEventListener {

    private TextView mTextView;
    public String status;
    String datapath = "/data_path";
    int i = 0;

    private SensorManager sensorManager;
    Sensor accelerometer;
    //private float[] senVal = new float[3];

    //private volatile boolean stopThread = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);
        sensorManager.registerListener(MainActivity.this, accelerometer, 300000);

        //startThread();
        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i){}

    @Override
    public void onSensorChanged(SensorEvent sensorEvent){

        //senVal[0] = sensorEvent.values[0];
        //senVal[1] = sensorEvent.values[1];
        //senVal[2] = sensorEvent.values[2];

        sendData(sensorEvent.values, System.currentTimeMillis());
        //sensorEvent.timestamp

    }

    /*
    public void startThread() {
        stopThread = false;
        SensorsRunnable runnable = new SensorsRunnable();
        new Thread(runnable).start();

    }

    public void stopThread() {
        stopThread = true;
    }


    class SensorsRunnable implements Runnable {

        @Override
        public void run() {

            while(!stopThread) {
                sendData(senVal);
            }
        }
    }
*/

    @Override
    public void onResume() {
        //startThread();
        sensorManager.registerListener(MainActivity.this, accelerometer, 200000);
        setAmbientEnabled();
        super.onResume();
    }


    //remove listener
    @Override
    public void onPause() {
        super.onPause();
    }

    @Override
    public void onStop(){
        //stopThread();
        sensorManager.unregisterListener(MainActivity.this, accelerometer);
        super.onStop();
    }

    @Override
    public void onDestroy(){
        //stopThread();
        sensorManager.unregisterListener(MainActivity.this, accelerometer);
        super.onDestroy();
    }

    public void logthis(){

        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setText(status);
            }
        });    }

    private void sendData(float[] senVal, long time){

        PutDataMapRequest dataMap = PutDataMapRequest.create(datapath);

        dataMap.getDataMap().putFloatArray("SensorValues", senVal);
        dataMap.getDataMap().putLong("ValueTimestamp", time);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();

        Task<DataItem> dataItemTask = Wearable.getDataClient(this).putDataItem(request);

        dataItemTask
                .addOnSuccessListener(new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        if(i == 0){
                            status = "Success";
                            logthis();
                            i++;
                        }
                        else if(i > 10){
                            i = 1;
                        }
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        if(i > 0) {
                            status = "Failure";
                            logthis();
                            i = 0;
                        }
                    }
                })
        ;
    }
}
