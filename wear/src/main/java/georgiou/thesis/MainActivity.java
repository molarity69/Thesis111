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
    private SensorManager sensorManager;
    Sensor accelerometer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mTextView = (TextView) findViewById(R.id.text);

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        sensorManager.registerListener(MainActivity.this, accelerometer, 200000);

        // Enables Always-on
        setAmbientEnabled();
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent){

        sendData(sensorEvent.values);
    }
    public void logthis(){ runOnUiThread(new Runnable() {
        @Override
        public void run() {
            mTextView.setText(status);
        }
    });    }

    private void sendData(float[] senVal){

        PutDataMapRequest dataMap = PutDataMapRequest.create(datapath);
        dataMap.getDataMap().putFloatArray("SensorValues", senVal);
        PutDataRequest request = dataMap.asPutDataRequest();
        request.setUrgent();
        Task<DataItem> dataItemTask = Wearable.getDataClient(this).putDataItem(request);
        dataItemTask
                .addOnSuccessListener(new OnSuccessListener<DataItem>() {
                    @Override
                    public void onSuccess(DataItem dataItem) {
                        status = "Success";
                        logthis();
                    }
                })
                .addOnFailureListener(new OnFailureListener() {
                    @Override
                    public void onFailure(Exception e) {
                        status = "Failure";
                        logthis();
                    }
                });
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i){}

    @Override
    public void onResume() {
        //startThread();
        sensorManager.registerListener(MainActivity.this, accelerometer, 200000);
        setAmbientEnabled();
        super.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
    }

    //remove listener
    @Override
    public void onStop(){
        sensorManager.unregisterListener(MainActivity.this, accelerometer);
        super.onStop();
    }

    //remove listener
    @Override
    public void onDestroy(){
        sensorManager.unregisterListener(MainActivity.this, accelerometer);
        super.onDestroy();
    }
}
