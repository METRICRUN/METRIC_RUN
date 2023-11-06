package com.example.metricrun_new

import android.content.Context
import android.os.Bundle
import android.app.AlertDialog
import android.content.pm.PackageManager
import com.google.android.material.bottomnavigation.BottomNavigationView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.example.metricrun_new.databinding.ActivityMainBinding
import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast


class MainActivity : AppCompatActivity() {

    private var bluetoothLeScanner: BluetoothLeScanner? = null
    private val devicesFound = mutableSetOf<BluetoothDevice>()
    val deviceList = devicesFound.toList()
    @SuppressLint("MissingPermission")
    val deviceNames = deviceList.map { "${it.name} (${it.address})" }.toTypedArray()




    companion object {
        const val REQUEST_CODE_BT_PERMISSION = 1001
        const val REQUEST_ENABLE_BT = 1002
    }
    @SuppressLint("MissingPermission")
    private fun initializeBluetooth(): Boolean {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled) {
            // Solicitar al usuario que habilite el Bluetooth si no está activado
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            return false
        }
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        return true
    }
    @SuppressLint("MissingPermission")
    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val device = result?.device
            if (device?.name == "METRICRUN" && device !in devicesFound) {
                devicesFound.add(device)
                Log.d("BLE_SCAN", "Device found: ${device.name} (${device.address})")
            }
        }
    }


    private fun checkAndRequestPermissions(): Boolean {
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED ||
            ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_ADMIN, Manifest.permission.ACCESS_FINE_LOCATION), REQUEST_CODE_BT_PERMISSION)
            return false
        }
        return true
    }

    private lateinit var binding: ActivityMainBinding
    @SuppressLint("MissingPermission")
    private fun startBleScan() {
        devicesFound.clear()
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val bluetoothAdapter = bluetoothManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
        bluetoothLeScanner?.startScan(scanCallback)
    }
    @SuppressLint("MissingPermission")
    private fun stopBleScan() {
        bluetoothLeScanner?.stopScan(scanCallback)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_ENABLE_BT -> {
                if (resultCode == Activity.RESULT_OK) {
                    // El usuario activó el Bluetooth, puedes continuar con el escaneo
                    showDialog1()
                } else {
                    // El usuario rechazó activar el Bluetooth. Puedes mostrar un mensaje o manejarlo de otra manera.
                    Toast.makeText(this, "Bluetooth necesario para escanear dispositivos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_CODE_BT_PERMISSION -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    // Permiso concedido, puedes continuar con el escaneo
                    showDialog1()
                } else {
                    // Permiso denegado. Puedes mostrar un mensaje o manejarlo de otra manera.
                    Toast.makeText(this, "Permisos necesarios para escanear dispositivos", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navView: BottomNavigationView = binding.navView

        val navController = findNavController(R.id.nav_host_fragment_activity_main)
        val appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.navigation_home, R.id.navigation_dashboard, R.id.navigation_notifications
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
        navView.setupWithNavController(navController)

        // Verificar si es el primer inicio usando SharedPreferences
        val sharedPreferences = getSharedPreferences("AppPreferences", Context.MODE_PRIVATE)
        val isFirstRun = sharedPreferences.getBoolean("isFirstRun", true)

        if (isFirstRun) {
            // Mostrar el primer modal
            showDialog1()
        }
    }
    @SuppressLint("MissingPermission")
    private fun showDialog1() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Selecciona un dispositivo")

        // Verifica permisos
        if (!checkAndRequestPermissions()) {
            return
        }

        // Inicializa Bluetooth
        if (!initializeBluetooth()) {
            return
        }

        // Iniciar el escaneo de BLE
        startBleScan()

        // Espera y luego detiene el escaneo
        Handler(Looper.getMainLooper()).postDelayed({
            stopBleScan()

            // Muestra dispositivos en el diálogo
            val deviceList = devicesFound.toList()
            val deviceNames = deviceList.map { "${it.name} (${it.address})" }.toTypedArray()
            builder.setItems(deviceNames) { _, which ->
                val selectedDevice = deviceList[which]
                Log.d("BLE_SCAN", "Dispositivo seleccionado: ${selectedDevice.address}")
                showDialog2()
            }

            builder.setNegativeButton("Cancelar") { _, _ -> }
            builder.show()

        }, 5000) // Espera 5 segundos para detener el escaneo y mostrar los dispositivos
    }



    private fun showDialog2() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Modal 2")
        builder.setMessage("Contenido del segundo modal.")
        builder.setPositiveButton("Siguiente") { _, _ ->
            showDialog3()
        }
        val dialog = builder.create()
        dialog.window?.setBackgroundDrawableResource(R.drawable.rounded_dialog)
        dialog.show()
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
