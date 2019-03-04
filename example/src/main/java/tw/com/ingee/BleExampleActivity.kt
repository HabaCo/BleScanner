package tw.com.ingee

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import android.support.v7.widget.LinearLayoutManager
import android.support.v7.widget.RecyclerView
import android.util.Log
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ProgressBar
import android.widget.TextView
import tw.com.ingee.blescanner.BleScanner
import java.util.*
import kotlin.Comparator

class BleExampleActivity : AppCompatActivity() {

    private val tag = javaClass.simpleName

    private lateinit var buttonScan: Button
    private lateinit var buttonClear: Button
    private lateinit var listDevices: RecyclerView
    private lateinit var progress: ProgressBar

    private lateinit var bleScanner: BleScanner

    private val devices = TreeMap<String, String>(
        Comparator<String> { o1, o2 -> o1.compareTo(o2) }
    )

    private var scanning = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ble_example)

        buttonScan = findViewById(R.id.buttonScan)
        buttonClear = findViewById(R.id.buttonClear)
        listDevices = findViewById(R.id.listDevices)
        progress = findViewById(R.id.progress)

        // 判斷 Android 版本並初始化 bleScanner
        bleScanner = if (Build.VERSION.SDK_INT >= 21) BleScanner.BleScannerAPI21(this)
        else BleScanner.BleScannerAPI18(this)

        // 裝置搜尋到的 callback
        bleScanner.onDeviceFoundDefault = { device, rssi, scanRecord ->
            val address = device.address
            val name = device.name
            Log.i(tag, "device: $address  name:$name  rssi: $rssi")

            devices[address] = "Device: ${name ?: "N/A"}\n  -- MAC: $address  -- rssi: $rssi"
            listDevices.adapter?.notifyDataSetChanged()
        }

        buttonScan.setOnClickListener {
            toggleScan()
        }

        buttonClear.setOnClickListener {
            devices.clear()
            listDevices.adapter?.notifyDataSetChanged()
        }

        listDevices.layoutManager = LinearLayoutManager(this)
        listDevices.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun getItemCount(): Int = devices.size

            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
                object : RecyclerView.ViewHolder(TextView(parent.context)) {}

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val deviceDesc = devices.values.toTypedArray()[position]

                val textView = holder.itemView as TextView
                textView.text = deviceDesc
            }
        }

    }

    private fun toggleScan() {
        try {
            if (!scanning) {
                bleScanner.startScan()
                progress.visibility = View.VISIBLE
                buttonScan.text = "Stop"
            } else {
                bleScanner.stopScan()
                progress.visibility = View.INVISIBLE
                buttonScan.text = "Scan"
            }

            scanning = !scanning
        } catch (ex: BleScanner.LocationPermissionNotGrantedException) {
            bleScanner.requestLocationPermission(this)
        }
    }

    override fun onDestroy() {
        super.onDestroy()

        bleScanner.stopScan()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == BleScanner.PermissionRequestCode){
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                toggleScan()
            }
        }
    }
}
