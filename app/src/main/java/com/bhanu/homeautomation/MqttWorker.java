package com.bhanu.homeautomation;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.util.Log;

import org.eclipse.paho.android.service.MqttAndroidClient;
import org.eclipse.paho.client.mqttv3.IMqttActionListener;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.IMqttToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.Queue;

public class MqttWorker {

    private static final String TAG = MqttWorker.class.getSimpleName();
    private static MqttWorker instance = null;
    private static final long DELAY = 1500;

    private MyConsumer<String> onMessageCallback;

    private MyConsumer<String> onConnectionStateChangedCallback;

    private MqttAndroidClient mqttClient;

    private final Queue<String> messageQ;
    private String connectionError = null;


    private MqttWorker(Context context) {
        messageQ =  new LinkedList<>();
    }

    public static MqttWorker getInstance(Context context) {
        if (instance == null) {
            instance = new MqttWorker(context);
        }

        return instance;
    }

    public void init(Context context, Callback cb) {
        String clientId = MqttClient.generateClientId();

        mqttClient = new MqttAndroidClient(context, AppConfig.MQTT_BROKER_URI, clientId);

        try {
            MqttConnectOptions auth = new MqttConnectOptions();
            auth.setKeepAliveInterval(10);
            IMqttToken token = mqttClient.connect(auth);

            token.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // We are connected

                    if (onConnectionStateChangedCallback != null) {
                        onConnectionStateChangedCallback.accept("CONNECTED");
                    }


                    Log.d(TAG, "Connected to server");

                    subscribe(AppConfig.SELF_TOPIC, () -> {
                        Log.d(TAG, "subscribed to topic: " + AppConfig.SELF_TOPIC);
                    });

                    if (cb != null) {
                        cb.callbackAction();
                    }
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    // Something went wrong e.g. connection timeout or firewall problems

                    String connectionErrorString = exception.toString();

                    if (connectionErrorString.contains("ENETUNREACH")) {
                        if (onConnectionStateChangedCallback != null) {
                            onConnectionStateChangedCallback.accept("ENETUNREACH");
                        }
                    }

                    else if (connectionErrorString.contains("ECONNREFUSED")) {
                        if (onConnectionStateChangedCallback != null) {
                            onConnectionStateChangedCallback.accept("ECONNREFUSED");
                        }
                    }

                    else {
                        if (onConnectionStateChangedCallback != null) {
                            onConnectionStateChangedCallback.accept("UNKNOWN_FAILURE");
                        }
                    }

                    Log.d(TAG, connectionErrorString);
                    init(context, null);
                }
            });

        } catch (MqttException e) {
            if (onConnectionStateChangedCallback != null) {
                onConnectionStateChangedCallback.accept("MQTT_EXCEPTION");
            }
            e.printStackTrace();
            init(context, null);
        }

        mqttClient.setCallback(new MqttCallback() {
            @Override
            public void connectionLost(Throwable cause) {
                mqttClient = null;
                init(context, null);
            }

            @Override
            public void messageArrived(String topic, MqttMessage message) throws Exception {
                String receivedMsgString = new String(message.getPayload());
                Log.d(TAG, "receivedMqttMessage: " + receivedMsgString);

                if (onMessageCallback != null) {
                    onMessageCallback.accept(receivedMsgString);
                }
            }

            @Override
            public void deliveryComplete(IMqttDeliveryToken token) {

            }
        });
    }

    public void onMessageArrived(MyConsumer<String> onMessageCallback) {
        this.onMessageCallback = onMessageCallback;
    }

    public void onConnectionStateChanged(MyConsumer<String> onConnectionStateChangeCallback) {
        this.onConnectionStateChangedCallback = onConnectionStateChangeCallback;
    }

    public void publish(String topic, String payload) {
        new Thread(() -> {
            synchronized (messageQ) {
                messageQ.add(payload);

                // this publish() method stuck's in the loop until there is a connection to server
                while (connectionError != null || mqttClient == null) {
                    Log.e(TAG, "connertionError: " + connectionError);
                    Log.d(TAG, "Waiting for connection...");
                    try {
                        Thread.sleep(DELAY);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }


                byte[] encodedPayload;
                String messageString;
                try {
                    while ((messageString = messageQ.peek()) != null) {
                        encodedPayload = messageString.getBytes("UTF-8");
                        MqttMessage message = new MqttMessage(encodedPayload);
                        Log.d(TAG, "publishing message: " +  payload);
                        mqttClient.publish(topic, message);
                        messageQ.poll();
                    }
                } catch (UnsupportedEncodingException | MqttException e) {
                    e.printStackTrace();
                } catch (NullPointerException e) {
                    e.printStackTrace();
                    Log.e(TAG, e.getMessage());
                }
            }
        }).start();
    }

    public void subscribe(final String topic, Callback callback) {
        int qos = 1;
        try {
            IMqttToken subToken = mqttClient.subscribe(topic, qos);
            subToken.setActionCallback(new IMqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    callback.callbackAction();
                }

                @Override
                public void onFailure(IMqttToken asyncActionToken,
                                      Throwable exception) {
                    // The subscription could not be performed, maybe the user was not
                    // authorized to subscribe on the specified topic e.g. using wildcards
                    Log.e(TAG, "subscription could not be performed due to unknown error, topic: " + topic);
                    if (exception != null) {
                        Log.e(TAG, exception.getMessage());
                    }
                }
            });
        } catch (MqttException e) {
            Log.d(TAG, "subscription failed (topic: " + topic + ")");
            e.printStackTrace();
        } catch (NullPointerException e) {
            Log.d(TAG, "subscription failed (topic: " + topic + ") ... NullPointerException");
            e.printStackTrace();
        }
    }

    private boolean isNetworkAvailable(Context context) {
        ConnectivityManager connectivityManager = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
        assert connectivityManager != null : "in MqttWorker: connectivityManager variable is null";
        NetworkInfo activeNetworkInfo = connectivityManager.getActiveNetworkInfo();
        return activeNetworkInfo != null && activeNetworkInfo.isConnected();
    }
}