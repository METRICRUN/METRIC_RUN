package com.example.metricrun_new

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.bluetooth.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.metricrun_new.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.util.*
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult


class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val devicesFound = mutableSetOf<BluetoothDevice>()
    private var bluetoothGatt: BluetoothGatt? = null
    private var adcValueA0: Int? = null
    private var adcValueA1: Int? = null

    companion object {
        const val REQUEST_CODE_BT_PERMISSION = 1001
        const val REQUEST_ENABLE_BT = 1002
        val SERVICE_UUID = UUID.fromString("4fafc201-1fb5-459e-8fcc-c5c9c331914b")
        val CHARACTERISTIC_UUID_A0 = UUID.fromString("beb5483e-36e1-4688-b7f5-ea07361b26a8")
        val CHARACTERISTIC_UUID_A1 = UUID.fromString("beb5483f-36e1-4688-b7f5-ea07361b26a9")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView
        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(setOf(R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications))
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)
        if (isFirstRun) {
            showDialog1()
        }
    }

    @SuppressLint("MissingPermission")
    private fun initializeBluetooth(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return false
        }
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        return true
    }

    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        devicesFound.clear()
        bluetoothLeScanner?.startScan(scanCallback)
    }

    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let { device ->
                if (device.name == "METRICRUN" && device !in devicesFound) {
                    devicesFound.add(device)
                    Log.d("BLE_SCAN", "Device found: ${device.name} (${device.address})")
                }
            }
        }
    }
    @SuppressLint("MissingPermission")
    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                Log.i("BluetoothGattCallback", "Successfully connected to ${gatt.device.address}")
                gatt.discoverServices()
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.i("BluetoothGattCallback", "Successfully disconnected from ${gatt.device.address}")
                bluetoothGatt?.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.i("BluetoothGattCallback", "Services discovered")
                val service = gatt.getService(SERVICE_UUID)

                service?.getCharacteristic(CHARACTERISTIC_UUID_A0)?.let { characteristicA0 ->
                    if (characteristicA0.properties.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        gatt.setCharacteristicNotification(characteristicA0, true)
                    }
                    if (characteristicA0.properties.and(BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        gatt.readCharacteristic(characteristicA0)
                    }
                }

                service?.getCharacteristic(CHARACTERISTIC_UUID_A1)?.let { characteristicA1 ->
                    if (characteristicA1.properties.and(BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                        gatt.readCharacteristic(characteristicA1)
                    }
                    if (characteristicA1.properties.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                        gatt.setCharacteristicNotification(characteristicA1, true)
                    }
                }
            } else {
                Log.w("BluetoothGattCallback", "onServicesDiscovered received: $status")
            }
        }


        override fun onDescriptorWrite(gatt: BluetoothGatt, descriptor: BluetoothGattDescriptor, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                if (descriptor.characteristic.uuid == CHARACTERISTIC_UUID_A0) {
                    // Once the descriptor for A0 is written, read or set notification for A1
                    gatt.getService(SERVICE_UUID)?.getCharacteristic(CHARACTERISTIC_UUID_A1)?.let { characteristicA1 ->
                        if (characteristicA1.properties.and(BluetoothGattCharacteristic.PROPERTY_READ) != 0) {
                            gatt.readCharacteristic(characteristicA1)
                        }
                        if (characteristicA1.properties.and(BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
                            gatt.setCharacteristicNotification(characteristicA1, true)
                            characteristicA1.getDescriptor(UUID.fromString("00002902-0000-1000-8000-00805f9b34fb"))?.let { descriptorA1 ->
                                descriptorA1.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                                gatt.writeDescriptor(descriptorA1)
                            }
                        }
                    }
                }
            }
        }


        override fun onCharacteristicRead(gatt: BluetoothGatt, characteristic: BluetoothGattCharacteristic, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                when (characteristic.uuid) {
                    CHARACTERISTIC_UUID_A0 -> {
                        adcValueA0 = convertBytesToInt(characteristic.value)
                        Log.d("ADC_READ", "ADC A0 Value: $adcValueA0")
                    }
                    CHARACTERISTIC_UUID_A1 -> {
                        adcValueA1 = convertBytesToInt(characteristic.value)
                        Log.d("ADC_READ", "ADC A1 Value: $adcValueA1")
                    }
                    else -> {
                        Log.d("ADC_READ", "Unknown characteristic read: ${characteristic.uuid}")
                    }
                }
                // Si ambos valores de ADC están disponibles, muestra el diálogo
                if (adcValueA0 != null && adcValueA1 != null) {
                    runOnUiThread {

                        showDialog2(adcValueA0!!, adcValueA1!!)
                    }
                }
            }
            else {
                Log.e("ADC_READ", "Error reading characteristic: ${characteristic.uuid} Status: $status")
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(selectedDevice: BluetoothDevice) {
        bluetoothGatt = selectedDevice.connectGatt(this, false, gattCallback)
    }

    private fun checkAndRequestPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_BT_PERMISSION)
            return false
        }
        return true
    }

    @SuppressLint("MissingPermission")
    private fun showDialog1() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecciona un dispositivo")

        if (!checkAndRequestPermissions()) {
            return
        }

        if (!initializeBluetooth()) {
            return
        }

        startBleScan()

        Handler(Looper.getMainLooper()).postDelayed({
            stopBleScan()

            val deviceList = devicesFound.toList()
            val deviceNames = deviceList.map { "${it.name} (${it.address})" }.toTypedArray()
            builder.setItems(deviceNames) { _, which ->
                val selectedDevice = deviceList[which]
                Log.d("BLE_SCAN", "Dispositivo seleccionado: ${selectedDevice.address}")
                connectToDevice(selectedDevice)
            }

            builder.setNegativeButton("Cancelar") { _, _ -> }
            builder.show()

        }, 5000)
    }


    private fun showDialog2(adcValueA0: Int, adcValueA1: Int) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Valores de ADC")

        // Configura el contenido del diálogo aquí, por ejemplo, usando un layout personalizado o simplemente un mensaje
        builder.setMessage("ADC A0: $adcValueA0\nADC A1: $adcValueA1")

        builder.setPositiveButton("Siguiente") { _, _ ->
            // Acciones al hacer clic en "Siguiente", por ejemplo, cerrar el diálogo
            showDialog3()
        }

        val dialog = builder.create()
        dialog.show()
    }


    // Función de utilidad para convertir bytes a un entero
    fun convertBytesToInt(bytes: ByteArray): Int {
        // Esto es solo un ejemplo, necesitarás ajustar esto según cómo estén formateados tus datos ADC
        return bytes[0].toInt() and 0xFF or
                (bytes[1].toInt() and 0xFF shl 8)
    }


    private fun showDialog3() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Modal 3")
        builder.setMessage("Contenido del tercer modal.")
        builder.setPositiveButton("Siguiente") { _, _ ->
            // Guardar en SharedPreferences que los modales ya se han mostrado
            val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
            val editor = sharedPreferences.edit()
            editor.putBoolean("isFirstRun", false)
            editor.apply()
        }
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
        dialog.show()
    }
}
