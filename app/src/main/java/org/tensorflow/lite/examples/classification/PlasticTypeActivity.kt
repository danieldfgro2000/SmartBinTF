package org.tensorflow.lite.examples.classification

import android.os.Bundle
import org.tensorflow.lite.examples.classification.R
import org.tensorflow.lite.examples.classification.PlasticTypeActivity
import org.tensorflow.lite.examples.classification.PlasticTypeActivity.Background_get
import android.widget.TextView
import android.os.AsyncTask
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import java.io.BufferedReader
import java.io.IOException
import java.io.InputStreamReader
import java.lang.StringBuilder
import java.net.HttpURLConnection
import java.net.URL
import java.util.*

class PlasticTypeActivity : AppCompatActivity() {
    private var mArduinoTemp = "1"
    private var mArduinoSetTempBasedOnpType = "1"
    private var mArduinoPType = "NOP"
    private var mArduinoStatus = "STATUS"
    private var mArduinoFillLevel = "Fill Level"
    private var mArduinoStatusLid = "Lid"
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_plastic_type)
        val bundle = intent.extras
        val plasticType: String?
        if (savedInstanceState == null) {
            val extras = intent.extras
            if (extras == null) {
                plasticType = null
                Log.e(TAG, "plastic type null")
            } else {
                plasticType = extras["PLASTIC_TYPE"] as String?
                Log.e(TAG, plasticType!!)
                Background_get().execute("plasticType=$plasticType")
            }
        } else {
            plasticType = savedInstanceState.getSerializable("PLASTIC_TYPE") as String?
            Log.e(TAG, "plastic type serializable")
        }
        plasticType?.let { setImage(it) }
        val dismiss = findViewById<Button>(R.id.btn_dismiss)
        dismiss.setOnClickListener { v: View? -> dismissActivity() }
        val start = findViewById<Button>(R.id.btn_accept)
        start.setOnClickListener { v: View? -> Background_get().execute("plasticType=$plasticType") }

//        if (mArduinoPType.equals("NOP")) {
//            Log.e(TAG, "mArduinoPType =" + mArduinoPType);
//            Log.e(TAG, "plasticType =" + plasticType);
//            start.setText(R.string.start);
//            start.setOnClickListener(v
//                    -> new Background_get().execute("plasticType=" + plasticType));
//        } else {
//            start.setText(R.string.stop);
//            Log.e(TAG, "mArduinoPType =" + mArduinoPType);
//            Log.e(TAG, "plasticType =" + plasticType);
//            start.setOnClickListener(v
//                    -> new Background_get().execute("plasticType=" + "nop"));
//        }
        val refresh = findViewById<ImageView>(R.id.iv_refresh)
        refresh.setOnClickListener { v: View? -> Background_get().execute("GET") }
        refreshData()
    }

    private fun dismissActivity() {
        Background_get().execute("plasticType=nop")
        super@PlasticTypeActivity.onBackPressed()
    }

    private fun refreshData() {
        Timer().schedule(object : TimerTask() {
            override fun run() {
                data
                Log.e(TAG, "Getting data from Arduino")
            }
        }, 1000, 5000)
    }

    private val data: Unit
        private get() {
            Background_get().execute("GET")
        }

    private fun setImage(plasticTypes: String) {
        val imageView = findViewById<ImageView>(R.id.iv_plastic_type)
        if (plasticTypes == "pp") {
            imageView.setImageResource(R.drawable.pp)
        }
        if (plasticTypes == "pet") {
            imageView.setImageResource(R.drawable.pet)
        }
        if (plasticTypes == "hdpe") {
            imageView.setImageResource(R.drawable.hdpe)
        }
        if (plasticTypes == "ldpe") {
            imageView.setImageResource(R.drawable.ldpe)
        }
        if (plasticTypes == "other") {
            imageView.setImageResource(R.drawable.other)
        }
        if (plasticTypes == "ps") {
            imageView.setImageResource(R.drawable.ps)
        }
        if (plasticTypes == "pvc") {
            imageView.setImageResource(R.drawable.pcv)
        }
    }

    private fun setTextView(
        temp: String,
        pType: String,
        status: String,
        tempPTypeArd: String,
        fillLevelArd: String,
        statusLidArd: String
    ) {
        val tempArd = findViewById<TextView>(R.id.tv_arduino_temp_read)
        tempArd.text = temp
        val pTypeArd = findViewById<TextView>(R.id.tv_pType_Arduino)
        pTypeArd.text = pType
        val statusArd = findViewById<TextView>(R.id.tv_arduino_status)
        statusArd.text = status
        val tempPType = findViewById<TextView>(R.id.tv_temp_based_on_pType)
        tempPType.text = tempPTypeArd
        val fillLevel = findViewById<TextView>(R.id.tv_fill_level)
        fillLevel.text = fillLevelArd
        val statusLid = findViewById<TextView>(R.id.tv_lid_status)
        statusLid.text = statusLidArd
    }

    private open inner class Background_get : AsyncTask<String?, Void?, String?>() {
        override fun doInBackground(vararg params: String?): String? {
            try {
                /* Change the IP to the IP you set in the arduino sketch */
                val url = URL("http://192.168.100.177/?" + params[0])
                val connection = url.openConnection() as HttpURLConnection
                val `in` = BufferedReader(InputStreamReader(connection.inputStream))
                val result = StringBuilder()
                var inputLine: String
                while (`in`.readLine().also { inputLine = it } != null) {
                    result.append(inputLine).append("\n")
                    Log.e(TAG, inputLine)
                    val data = inputLine.split(" ".toRegex()).toTypedArray()
                    Log.e(TAG, "data= " + data[0])
                    if (data.size == 6) {
                        mArduinoTemp = data[0] + " \u2103"
                        mArduinoPType = data[1]
                        mArduinoStatus = data[2]
                        mArduinoSetTempBasedOnpType = data[3] + " \u2103"
                        mArduinoFillLevel = data[4] + " %"
                        mArduinoStatusLid = data[5]
                    } else {
                        Log.e(TAG, "data = \$data")
                    }
                    runOnUiThread {
                        setTextView(
                            mArduinoTemp,
                            mArduinoPType,
                            mArduinoStatus,
                            mArduinoSetTempBasedOnpType,
                            mArduinoFillLevel,
                            mArduinoStatusLid
                        )
                    }
                }
                `in`.close()
                connection.disconnect()
                return result.toString()
            } catch (e: IOException) {
                e.printStackTrace()
            }
            return null
        }


    }

    companion object {
        private const val TAG = "Plastic Type Activity"
    }
}