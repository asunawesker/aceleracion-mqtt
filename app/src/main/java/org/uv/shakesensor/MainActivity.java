package org.uv.shakesensor;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import android.content.DialogInterface;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.snackbar.Snackbar;
import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.Viewport;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.LineGraphSeries;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.DisconnectedBufferOptions;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttMessageListener;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallbackExtended;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private SensorManager mSensorManager;
    private Sensor mAccelerometer;
    private double prevAcceletarionValue;
    private int pointsPlotted = 0;
    Viewport viewport;

    LineGraphSeries<DataPoint> series = new LineGraphSeries<>(new DataPoint[]{});

    TextView txt_currentAccel, txt_prevAccel, txt_acceleration;
    ProgressBar progressShakeMeter;
    Button buttonPublish;
    Button buttonUnsubscribe;

    MqttAndroidClient mqttAndroidClient;

    final String serverUri = "ws://34.125.103.25:8083/mqtt";

    String clientId = android.os.Build.MODEL;
    ScheduledExecutorService execService;
    ScheduledFuture<?> scheduledFuture;
    final String subscriptionTopic = "cliente/respuesta";
    final String topicAcceleration = "emulador/"+clientId+"/aceleracion";
    final String topicSteps = "emulador/"+clientId+"/pasos";
    final String topics = "emulador/"+clientId+"/#";
    boolean subscription = true;
    String accelerationJson;
    String stepsJson;
    double acceleration;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mqttConnection();

        progressShakeMeter = findViewById(R.id.progShakeMeter);
        txt_acceleration = findViewById(R.id.txtAccel);
        txt_currentAccel = findViewById(R.id.txtCurrAccel);
        txt_prevAccel = findViewById(R.id.txtPrevAccel);
        buttonPublish = findViewById(R.id.buttonPublish);
        buttonUnsubscribe = findViewById(R.id.buttonUnsubscribe);

        mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE);
        mAccelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        GraphView graph = (GraphView) findViewById(R.id.graph);
        viewport = graph.getViewport();
        viewport.setScrollable(true);
        viewport.setXAxisBoundsManual(true);
        graph.addSeries(series);

        buttonPublish.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                execService = Executors.newScheduledThreadPool(2);

                Runnable runnable = new Runnable() {
                    @Override
                    public void run() {
                        publishMessage(accelerationJson, topicAcceleration);
                        publishMessage(stepsJson, topicSteps);
                    }
                };

                scheduledFuture =
                        execService.scheduleAtFixedRate(runnable, 0L, 5L, TimeUnit.SECONDS);
            }
        });

        buttonUnsubscribe.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                unsubscribeToTopic(topics);
                subscription = false;
            }
        });
    }

    private void mqttConnection(){
        mqttAndroidClient = new MqttAndroidClient(getApplicationContext(), serverUri, clientId);
        mqttAndroidClient.setCallback(new MqttCallbackExtended() {
            @Override
            public void connectComplete(boolean reconnect, String serverURI) {

                if (reconnect) {
                    System.out.println("LOG: Reconnected to : " + serverURI);
                    //subscribeToTopic();
                } else {
                    System.out.println("Connected to: " + serverURI);
                }
            }

            @Override
            public void connectionLost(Throwable cause) {
                System.out.println("LOG: The Connection was lost.");
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                System.out.println("LOG Incoming message: " + new String(message.getPayload()));
                alert(new String(message.getPayload()));
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });

        MqttConnectOptions mqttConnectOptions = new MqttConnectOptions();
        mqttConnectOptions.setAutomaticReconnect(true);
        mqttConnectOptions.setCleanSession(false);

        try {
            mqttAndroidClient.connect(mqttConnectOptions, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    DisconnectedBufferOptions disconnectedBufferOptions = new DisconnectedBufferOptions();
                    disconnectedBufferOptions.setBufferEnabled(true);
                    disconnectedBufferOptions.setBufferSize(100);
                    disconnectedBufferOptions.setPersistBuffer(false);
                    disconnectedBufferOptions.setDeleteOldestMessages(false);
                    mqttAndroidClient.setBufferOpts(disconnectedBufferOptions);
                    //subscribeToTopic();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("LOG: Failed to connect to: " + serverUri);
                }
            });


        } catch (MqttException ex){
            ex.printStackTrace();
        }
    }

    public void alert(String message){
        AlertDialog alertDialog = new AlertDialog.Builder(MainActivity.this).create();
        alertDialog.setTitle("AGUARDA");
        alertDialog.setMessage(message);
        alertDialog.setButton(AlertDialog.BUTTON_NEUTRAL, "OK",
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                });
        alertDialog.show();
    }

    protected void onResume() {
        super.onResume();
        mSensorManager.registerListener(this, mAccelerometer, SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected void onPause() {
        super.onPause();
        mSensorManager.unregisterListener(this);
    }

    @Override
    public void onSensorChanged(SensorEvent sensorEvent) {
        float x = sensorEvent.values[0];
        float y = sensorEvent.values[1];
        float z = sensorEvent.values[2];

        double currentAccelerationValue = Math.sqrt((x * x + y * y + z * z));
        double changeInAccelleration = Math.abs(currentAccelerationValue - prevAcceletarionValue);
        prevAcceletarionValue = currentAccelerationValue;

        txt_currentAccel.setText("Actual: "+(int)currentAccelerationValue);
        txt_prevAccel.setText("Previa: "+(int)prevAcceletarionValue);
        txt_acceleration.setText("Cambio de aceleración: "+changeInAccelleration);

        acceleration = changeInAccelleration;

        progressShakeMeter.setProgress((int) changeInAccelleration);

        pointsPlotted++;
        series.appendData(new DataPoint(pointsPlotted, changeInAccelleration), true, pointsPlotted);
        viewport.setMaxX(pointsPlotted);
        viewport.setMinX(pointsPlotted-200);

        accelerationJson = "{\"aceleracion\":\""+changeInAccelleration+"\"}";
        stepsJson = "{\"pointsPlotted\":\""+pointsPlotted+"\"}";

        if(acceleration>=25){
            System.out.println("CHOQUE");
            publishMessage(accelerationJson, topicAcceleration);
            publishMessage(stepsJson, topicSteps);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {}

    public void subscribeToTopic(){
        try {
            mqttAndroidClient.subscribe(subscriptionTopic, 0, null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    System.out.println("LOG: Subscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("LOG: Failed to subscribe!");
                }
            });

        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void unsubscribeToTopic(String topic){
        try {
            mqttAndroidClient.unsubscribe("emulador/M2102J20SG/aceleracion", null, new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    scheduledFuture.cancel(false);
                    System.out.println("LOG: Unsubscribed!");
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("LOG: Failed to Unsubscribe!");
                }
            });
        } catch (MqttException ex){
            System.err.println("Exception whilst subscribing");
            ex.printStackTrace();
        }
    }

    public void publishMessage(String messageToPublish, String publishTopic){
        try {
            MqttMessage message = new MqttMessage();
            message.setPayload(messageToPublish.getBytes());
            mqttAndroidClient.publish(publishTopic, message);
            System.out.println("LOG: Message Published, {Topic: " + publishTopic + " Message: " + acceleration + "}");
            if(!mqttAndroidClient.isConnected()){
                System.out.println("LOG: " + mqttAndroidClient.getBufferedMessageCount() + " messages in buffer.");
            }
        } catch (MqttException e) {
            System.err.println("LOG: Error Publishing: " + e.getMessage());
            e.printStackTrace();
        }
    }
}