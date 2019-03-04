# BleScanner
適用 API21 和 API18 的簡易藍芽掃描工具類.
A simple bluetooth scanner tool compatible with API21 and API18.

===
Android 在 API 18 使用 BluetoothAdapter 進行藍芽搜尋，而 API 21 使用 BluetoothAdapter 底下的 BluetoothLeScanner 進行藍芽搜尋
如果專案要使用藍芽於 API 21 以下將會有些麻煩，這個 tiny library 由此而生。

因為 API 21 
===

使用簡易的判斷式即可於 running time 進行適配
val bleScanner =
    if (Build.VERSION.SDK_INT >= 21) BleScanner.BleScannerAPI21(this)
    else BleScanner.BleScannerAPI18(this)

並且 listen 取得搜尋到的裝置 (為簡化使用會從 API21 的 ScanResult 取得與 API18 相同的資訊並返回)
bleScanner.onDeviceFoundDefault = { device: BluetoothDevice, rssi: Int, scanRecord: ByteArray? ->
    ... your code
}

---

若需分將 API 18 以及 API 21 的藍芽功能分類，各自覆寫 onDeviceFoundAPI18、onDeviceFoundAPI21 即可
bleScanner.onDeviceFoundAPI18 = { device: BluetoothDevice, rssi: Int, scanRecord: ByteArray? ->
    ... your code
}
bleScanner.onDeviceFoundAPI21 = { device: BleScannerAPI21.Device ->
    ... your code
}

---

此工具也開放 base listener 供覆寫，如果覺得上面很囉唆可以分別進行覆寫，但須確定 Android 版本，否則 ..
API 18 的 onDeviceFound
bleScanner.asAPI18().onDeviceFound = { device: BleScannerAPI18.Device ->
    ... your code
}
API 21 的 onDeviceFound
bleScanner.asAPI21().onDeviceFound = { device: BleScannerAPI21.Device ->
    val scanResult = device.scanResult
    ... your code
}
當然，也可以合併在初始宣告
val bleScanner =
    if (Build.VERSION.SDK_INT < 21) {
      BleScanner.BleScannerAPI18(this).also {
        it.onDeviceFound = { device: BleScannerAPI18.Device ->
          ... your code
        }
      }
    } else {
      BleScanner.BleScannerAPI21(this).also {
        it.onDeviceFound = { device: BleScannerAPI21.Device ->
          ... your code
        }
      }
    }
