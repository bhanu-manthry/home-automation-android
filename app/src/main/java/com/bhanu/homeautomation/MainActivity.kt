package com.bhanu.homeautomation

import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.ToggleButton
import com.google.gson.JsonObject
import com.google.gson.JsonParser

class MainActivity : AppCompatActivity(), View.OnClickListener {

    private lateinit var mqttWorker : MqttWorker

    private val switchMap = HashMap<String, Int>()

    lateinit var lightBtn: ToggleButton
    lateinit var fanBtn: ToggleButton
    lateinit var sock3PinBtn: ToggleButton
    lateinit var sock2PinBtn: ToggleButton
    lateinit var allDevicesBtn: ToggleButton

    lateinit var gridLayout: GridLayout
    lateinit var linearLayout: LinearLayout

    companion object {
        val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        mqttWorker = MqttWorker.getInstance(applicationContext)

        switchMap["light"] = 2
        switchMap["fan"] = 4
        switchMap["sock_3_pin"] = 1
        switchMap["sock_2_pin"] = 3

        lightBtn= findViewById(R.id.lightBtn)
        fanBtn= findViewById(R.id.fanBtn)
        sock3PinBtn= findViewById(R.id.sock3PinBtn)
        sock2PinBtn= findViewById(R.id.sock2PinBtn)
        allDevicesBtn= findViewById(R.id.allDevicesBtn)

        gridLayout = findViewById(R.id.gridLayout)
        linearLayout = findViewById(R.id.loadingView)

        lightBtn.setOnClickListener(this)
        fanBtn.setOnClickListener(this)
        sock3PinBtn.setOnClickListener(this)
        sock2PinBtn.setOnClickListener(this)
        allDevicesBtn.setOnClickListener(this)

        val buttonOfSwitchIndex = HashMap<Int, ToggleButton>()
        buttonOfSwitchIndex[2] = lightBtn
        buttonOfSwitchIndex[4] = fanBtn
        buttonOfSwitchIndex[1] = sock3PinBtn
        buttonOfSwitchIndex[3] = sock2PinBtn

        mqttWorker.onMessageArrived {
            if (it.startsWith("STATUS")) {
                Log.d(TAG, "status message received")
                val statusStr = it.split("~")[1].trim()
                val parser = JsonParser()
                val jsonObj = parser.parse(statusStr) as JsonObject

                val entries = jsonObj.entrySet()//will return members of your object
                for (entry in entries) {
                    val switchIndex = Integer.parseInt(entry.key)
                    println("$switchIndex val=" + entry.value)
                    buttonOfSwitchIndex[switchIndex]?.isChecked = entry.value.asBoolean
                }

                gridLayout.visibility = View.VISIBLE
                linearLayout.visibility = View.GONE
            }

            else if (it.startsWith("ACK_SET")) {
                Log.d(TAG, "ACK received")
                val statusStr = it.split("~")[1].trim()
                val parser = JsonParser()
                val jsonObj = parser.parse(statusStr) as JsonObject

                val switchIndex = jsonObj.get("key").asInt
                val isOn = jsonObj.get("val").asBoolean
                Log.d(TAG, "key $switchIndex, val=$isOn")
                buttonOfSwitchIndex[switchIndex]?.isChecked = isOn
            }
        }
    }

    override fun onResume() {
        super.onResume()
        linearLayout.visibility = View.VISIBLE
        gridLayout.visibility = View.GONE
        mqttWorker.init(applicationContext) {
            mqttWorker.publish(AppConfig.SWITCH_BOARD_TOPIC, "GET_STATUS")
            linearLayout.visibility = View.GONE
            gridLayout.visibility = View.VISIBLE
        }
    }

    override fun onClick(v: View?) {
        when (v?.id) {
            R.id.lightBtn -> {
                val key = switchMap["light"]

                val jsonObject = JsonObject()
                jsonObject.addProperty("key", key)
                jsonObject.addProperty("val", lightBtn.isChecked)

                val jsonString = jsonObject.toString()
                mqttWorker.publish(AppConfig.SWITCH_BOARD_TOPIC, "SET ~ " + jsonString)
            }

            R.id.fanBtn -> {
                val key = switchMap["fan"]

                val jsonObject = JsonObject()
                jsonObject.addProperty("key", key)
                jsonObject.addProperty("val", fanBtn.isChecked)

                val jsonString = jsonObject.toString()
                mqttWorker.publish(AppConfig.SWITCH_BOARD_TOPIC, "SET ~ " + jsonString)
            }

            R.id.sock3PinBtn -> {
                val key = switchMap["sock_3_pin"]

                val jsonObject = JsonObject()
                jsonObject.addProperty("key", key)
                jsonObject.addProperty("val", sock3PinBtn.isChecked)

                val jsonString = jsonObject.toString()
                mqttWorker.publish(AppConfig.SWITCH_BOARD_TOPIC, "SET ~ " + jsonString)
            }

            R.id.sock2PinBtn -> {
                val key = switchMap["sock_2_pin"]

                val jsonObject = JsonObject()
                jsonObject.addProperty("key", key)
                jsonObject.addProperty("val", sock2PinBtn.isChecked)

                val jsonString = jsonObject.toString()
                mqttWorker.publish(AppConfig.SWITCH_BOARD_TOPIC, "SET ~ " + jsonString)
            }

            R.id.allDevicesBtn -> {
                switchMap.forEach { pair ->
                    val key = pair.value
                    val jsonObject = JsonObject()
                    jsonObject.addProperty("key", key)
                    jsonObject.addProperty("val", allDevicesBtn.isChecked)

                    val jsonString = jsonObject.toString()
                    mqttWorker.publish(AppConfig.SWITCH_BOARD_TOPIC, "SET ~ " + jsonString)
                }
            }

            else -> {
                Log.d(TAG, "Mqtt message is not defined for this View")
            }
        }
    }
}
