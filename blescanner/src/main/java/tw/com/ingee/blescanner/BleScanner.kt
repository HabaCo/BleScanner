package tw.com.ingee.blescanner

import android.Manifest
import android.annotation.SuppressLint
import android.annotation.TargetApi
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.*
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.support.v4.app.ActivityCompat
import android.support.v4.content.ContextCompat
import android.util.Log
import tw.com.ingee.blescanner.BleScanner.BleScannerAPI18
import tw.com.ingee.blescanner.BleScanner.BleScannerAPI21
import java.util.*

/**
 * Created by Haba on 2019/2/27.
 * © Ingee.com.tw All Rights Reserved
 */

/**
 * 此 project 不預先加入 permission 以方便使用管理
 *
 * 建議加入以下權限以確保使用藍芽
 *
 * BLUETOOTH、BLUETOOTH_ADMIN、ACCESS_FINE_LOCATION or ACCESS_COARSE_LOCATION
 *
 * 使用 [BleScannerAPI18] 目標為 API 18 ~ API 21 之間裝置
 *
 * 使用 [BleScannerAPI21] 目標為 API 21 以上裝置，並且須對使用者要求定位權限，以使用 [ScanCallback] 得到更詳細的藍芽資訊
 */
sealed class BleScanner {

    protected val TAG = javaClass.simpleName

    companion object {
        const val PermissionRequestCode = 1
    }

    open class Device

    /**
     * 裝置搜尋 callback
     */
    var onDeviceFound: (device: BluetoothDevice, rssi: Int, scanRecord: ByteArray?) -> Unit = {_, _, _ -> }

    /**
     * 是否開啟 debug 輸出
     */
    var debug = true

    /**
     * 搜尋中與否
     */
    var scanning = false

    /**
     * 搜尋清單
     */
    protected var devices: HashMap<String, Any> = HashMap()

    /**
     * 開始搜尋
     */
    abstract fun startScan()

    /**
     * 僅作用於 [BleScannerAPI21], 否則效果同 [startScan]
     *  @param filters 搜尋篩選條件，例如指定搜尋裝置名稱
     *  @param settings 搜尋屬性設定，例如搜尋頻率、功耗
     *  @throws LocationPermissionNotGrantedException 因為 API 21 需要定位權限才能使用 [ScanCallback]，如果沒有提供定位權限將會丟出此例外
     */
    abstract fun startScan(filters: List<ScanFilter>, settings: ScanSettings)

    /**
     * 停止搜尋
     */
    abstract fun stopScan()

    /**
     * 取得全部裝置搜尋紀錄
     *
     * 如果使用 [BleScannerAPI21], 型別使用 [BleScannerAPI21.Device] 轉換
     *
     * 如果使用 [BleScannerAPI18], 型別使用 [BleScannerAPI18.Device] 轉換
     */
    @Suppress("UNCHECKED_CAST")
    fun <T: Device> allFoundDevices() : Iterable<T> = devices.values.map { device -> device as T }

    fun requestLocationPermission(requestActivity: Activity, requestCode: Int = PermissionRequestCode){
        ActivityCompat.requestPermissions(requestActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
    }

    /**
     * BleScannerAPI21 used for API 21+
     */
    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    class BleScannerAPI21(private val context: Context) : BleScanner(){

        data class Device(val callbackType: Int, val scanResult: ScanResult): BleScanner.Device()

        private var mBluetoothAdapter: BluetoothAdapter? = null
        private var mLeScanner: BluetoothLeScanner? = null
        private var bleScanCallback: ScanCallback? = null

        init {
            mBluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter
            mLeScanner = mBluetoothAdapter?.bluetoothLeScanner

            bleScanCallback = object: ScanCallback() {

                override fun onScanResult(callbackType: Int, scanResult: ScanResult?) {
                    super.onScanResult(callbackType, scanResult)

                    scanResult?.let { result ->
                        val device = result.device
                        devices[device.address] = Device(callbackType, scanResult)

                        onDeviceFound(device, result.rssi, result.scanRecord?.bytes)
                    }
                }
            }
        }

        /**
         *  @throws LocationPermissionNotGrantedException 因為 API 21 需要定位權限才能使用 [ScanCallback]，如果沒有提供定位權限將會丟出此例外
         */
        @SuppressLint("MissingPermission")
        @Throws(LocationPermissionNotGrantedException::class)
        override fun startScan() {
            if (!isLocationServiceEnabled() || !requestedLocationPermission()){
                throw LocationPermissionNotGrantedException("at least one of location providers need to be enable so we can use ScanCallback")
            } else if (!scanning) {
                scanning = true

                mLeScanner?.startScan(bleScanCallback)

                if (debug){
                    Log.d(TAG, "startScan")
                }
            }
        }

        @SuppressLint("MissingPermission")
        @Throws(LocationPermissionNotGrantedException::class)
        override fun startScan(filters: List<ScanFilter>, settings: ScanSettings) {
            if (!isLocationServiceEnabled() || !requestedLocationPermission()){
                throw LocationPermissionNotGrantedException("at least one of location providers need to be enable so we can use ScanCallback")
            } else if (!scanning) {
                scanning = true

                mLeScanner?.startScan(filters, settings, bleScanCallback)

                if (debug){
                    Log.d(TAG, "startScan")
                }
            }
        }

        /**
         * API 21 需要定位權限才能使用 [ScanCallback]，只要對定位權限 Group 要求任一權限即可，例如
         *
         * [android.Manifest.permission.ACCESS_FINE_LOCATION] 或
         * [android.Manifest.permission.ACCESS_COARSE_LOCATION]
         *
         * 這裡我們使用 [android.Manifest.permission.ACCESS_FINE_LOCATION]
         */
        private fun requestedLocationPermission(): Boolean
                = ContextCompat.checkSelfPermission(context, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED

        /**
         * API 21 需要開啟任一種定位方式 (GPS or WIFI or Passive 等等)
         */
        private fun isLocationServiceEnabled(): Boolean {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

                locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) or
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) or
                locationManager.isProviderEnabled(LocationManager.PASSIVE_PROVIDER)
            } else {
                true
            }
        }

        @SuppressLint("MissingPermission")
        override fun stopScan() {
            if (scanning){
                scanning = false

                mLeScanner?.stopScan(bleScanCallback)

                if (debug){
                    Log.d(TAG, "stopScan")
                }
            }
        }
    }

    /**
     * BleScannerAPI18 used for API 18-21 compatible
     */
    @Suppress("DEPRECATION")
    class BleScannerAPI18(context: Context) : BleScanner(){

        data class Device(val device: BluetoothDevice, val rssi: Int, val scanRecord: ByteArray?): BleScanner.Device() {
            override fun equals(other: Any?): Boolean {
                return other is Device && other.device.address == device.address
            }

            override fun hashCode(): Int {
                return device.hashCode()
            }
        }

        private var mBluetoothAdapter: BluetoothAdapter? = null
        private var bleScanCallback: BluetoothAdapter.LeScanCallback? = null

        init {
            mBluetoothAdapter = (context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager).adapter

            bleScanCallback = BluetoothAdapter.LeScanCallback { device, rssi, scanRecord ->
                devices[device.address] = Device(device, rssi, scanRecord)
                onDeviceFound(device, rssi, scanRecord)
            }
        }

        @SuppressLint("MissingPermission")
        override fun startScan() {
            if (!scanning){
                scanning = true

                mBluetoothAdapter?.startLeScan(bleScanCallback)

                if (debug){
                    Log.d(TAG, "startScan")
                }
            }
        }

        /**
         * 此 method 僅提供給 API21，將忽略所有參數，呼叫等同 [startScan]
         */
        override fun startScan(filters: List<ScanFilter>, settings: ScanSettings) {
            startScan()
        }

        @SuppressLint("MissingPermission")
        override fun stopScan() {
            if (scanning){
                scanning = false

                mBluetoothAdapter?.stopLeScan(bleScanCallback)

                if (debug){
                    Log.d(TAG, "stopScan")
                }
            }
        }
    }

    class LocationPermissionNotGrantedException(message: String): Exception(message)
}