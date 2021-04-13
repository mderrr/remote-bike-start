package com.example.remotebikestart

import android.app.*
import android.bluetooth.*
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.lang.NumberFormatException
import java.lang.StringBuilder
import java.util.*

const val SERVICE_NOTIFICATION_ID = 2

class ScanService : Service() {
    private val handler = Handler(Looper.getMainLooper())
    private var bluetoothGatt: BluetoothGatt? = null


    companion object {
        var isServiceRunning = false
        var isConnected = false
        var isScanning = false
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        createServiceNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            when (it.action) {
                getString(R.string.action_service_start) -> { startForegroundService() }
                getString(R.string.action_service_stop) -> { stopForegroundService() }
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    private fun scanForLeDevices() {
        if (isConnected) { return }

        val deviceAddressFilter = ScanFilter.Builder().setDeviceAddress(getString(R.string.device_mac_address)).build()
        val scanSettings = ScanSettings.Builder().setScanMode(2).build()
        val scanFilterList = listOf(deviceAddressFilter)

        BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner.startScan(scanFilterList, scanSettings, scanCallback)
        createServiceNotification(getString(R.string.notification_message_service_scanning))
        isScanning = true
    }

    private fun stopScanForLeDevices() {
        if (isServiceRunning) {
            createServiceNotification(getString(R.string.notification_message_service_scanning_stopped))
        }

        BluetoothAdapter.getDefaultAdapter().bluetoothLeScanner.stopScan(scanCallback)
        isScanning = false
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            createServiceNotification(getString(R.string.notification_message_service_device_found))
            bluetoothGatt = result.device.connectGatt(this@ScanService, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(bluetoothGatt: BluetoothGatt?, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    isConnected = true
                    stopScanForLeDevices()
                    bluetoothGatt?.discoverServices()
                    createServiceNotification(getString(R.string.notification_message_service_device_connected), icon = R.drawable.ic_bluetooth_connected)
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    isConnected = false
                    //bluetoothGatt?.close()

                    if (isServiceRunning) {
                        stopForegroundService()
                        startForegroundService()
                        // TODO send Notification
                    }
                }
            }
        }

        override fun onServicesDiscovered(bluetoothGatt: BluetoothGatt?, status: Int) {
            super.onServicesDiscovered(bluetoothGatt, status)

            val characteristic = getBluetoothGattCharacteristic(bluetoothGatt, getUuid(getString(R.string.device_service_uuid)), getUuid(getString(R.string.device_characteristics_uuid)))

            if (characteristic != null) {
                motorcycleIgnitionStart(characteristic, bluetoothGatt)

            } else {
                createServiceNotification(getString(R.string.notification_message_service_uuid_error), icon = R.drawable.ic_error)
                stopForegroundService()
            }
        }
    }

    private fun getTime(value: String): Long {
        var time = value.toFloat()
        time *= 1000

        return time.toLong()
    }

    private fun sendCommandToBluetoothDevice(characteristic: BluetoothGattCharacteristic, bluetoothGatt: BluetoothGatt?, command: String) {
        characteristic.setValue(command)
        bluetoothGatt?.writeCharacteristic(characteristic)
    }

    private fun motorcycleIgnitionStart(characteristic: BluetoothGattCharacteristic, bluetoothGatt: BluetoothGatt?) {
        val ignitionStartCommand = getString(R.string.device_password) + getString(R.string.command_ignition_start)

        sendCommandToBluetoothDevice(characteristic, bluetoothGatt, ignitionStartCommand)

        handler.postDelayed({
            motorcycleEngineStart(characteristic, bluetoothGatt)
        }, getTime(getString(R.string.engine_start_delay)))
    }

    private fun motorcycleEngineStart(characteristic: BluetoothGattCharacteristic, bluetoothGatt: BluetoothGatt?) {
        val engineStartCommand = getString(R.string.device_password) + getString(R.string.command_engine_start)
        val engineStopCommand = getString(R.string.device_password) + getString(R.string.command_engine_stop)

        sendCommandToBluetoothDevice(characteristic, bluetoothGatt, engineStartCommand)

        handler.postDelayed({
            sendCommandToBluetoothDevice(characteristic, bluetoothGatt, engineStopCommand)
        }, getTime(getString(R.string.engine_start_duration)))
    }

    fun getBluetoothGattCharacteristic(bluetoothGatt: BluetoothGatt?, serviceUuid: String, characteristicUuid: String): BluetoothGattCharacteristic? {
        return try {
            bluetoothGatt?.getService(UUID.fromString(serviceUuid))?.getCharacteristic(
                UUID.fromString(
                characteristicUuid
            ))

        } catch (exception: NumberFormatException) {
            null
        }
    }

    private fun getUuid(specific_uuid: String): String {
        val stringBuilder = StringBuilder()
        val baseUuid = getString(R.string.device_base_uuid)

        for (i in baseUuid.indices) {
            if (i in 4..7) {
                stringBuilder.append(specific_uuid[i - 4])
            } else {
                stringBuilder.append(baseUuid[i])
            }
        }

        return stringBuilder.toString()
    }

    private fun getOpenMainActivityPendingIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).apply {
            this.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun createServiceNotificationChannel() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationChannel = NotificationChannel(getString(R.string.service_notification_channel_id),  getString(R.string.service_notification_channel_name), NotificationManager.IMPORTANCE_LOW)

        notificationChannel.enableVibration(false)
        notificationChannel.setShowBadge(false)
        notificationChannel.lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        notificationManager.createNotificationChannel(notificationChannel)
    }

    private fun createServiceNotification(message: String, icon: Int = R.drawable.ic_scan_line) {
        val pendingIntent = getOpenMainActivityPendingIntent()
        val notificationBuilder = NotificationCompat.Builder(this, getString(R.string.service_notification_channel_id))
            .setAutoCancel(false)
            .setOngoing(true)
            .setSmallIcon(icon)
            .setContentTitle(getString(R.string.notification_title_scan_service))
            .setContentText(message)
            .setContentIntent(pendingIntent)

        startForeground(SERVICE_NOTIFICATION_ID, notificationBuilder.build())
    }

    private fun startForegroundService() {
        createServiceNotification(getString(R.string.notification_message_service_scanning))
        scanForLeDevices()
        isServiceRunning = true
    }

    private fun stopForegroundService() {
        stopScanForLeDevices()
        stopForeground(true)
        //stopSelf()
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
        bluetoothGatt = null
        isServiceRunning = false
    }
}