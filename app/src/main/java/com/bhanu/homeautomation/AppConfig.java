package com.bhanu.homeautomation;

public class AppConfig {
    // your MQTT borker name here, example: tcp://183.87.92.160:1883
    public static final String MQTT_BROKER_URI = "";

    // MQTT topic subscribed by the switch board, this app publishes the messages to this topic
    public static final String SWITCH_BOARD_TOPIC = "";

    // MQTT topic to which this app subscribes
    public static final String SELF_TOPIC  = "";
}
