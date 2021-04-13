package com.example.remotebikestart


import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.icu.util.Calendar
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.remotebikestart.databinding.ActivityMainBinding
import java.lang.Integer.getInteger


const val FINE_LOCATION_PERMISSION = android.Manifest.permission.ACCESS_FINE_LOCATION
const val BACKGROUND_LOCATION_PERMISSION = android.Manifest.permission.ACCESS_BACKGROUND_LOCATION

const val FINE_LOCATION_REQUEST_CODE = 100
const val BACKGROUND_LOCATION_REQUEST_CODE = 101
const val ENABLE_BLUETOOTH_REQUEST_CODE = 102

class MainActivity : AppCompatActivity() {
    private var backPressedTime = 0L
    private lateinit var binding: ActivityMainBinding



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val currentYear = " ${Calendar.getInstance().get(Calendar.YEAR)} "
        val copyrightFooter = getString(R.string.copyright_footer_beginning) + currentYear + getString(R.string.copyright_footer_end)

        val bluetoothManager = getSystemService(BluetoothManager::class.java)
        val bluetoothAdapter: BluetoothAdapter? = bluetoothManager?.adapter

        binding.toggleButton.tooltipText = getString(R.string.toggle_button_tooltip_message)
        binding.footerTextView.text = copyrightFooter

        checkLocationPermissions()

        binding.toggleButton.setOnClickListener {
            if (checkBluetoothEnabled(bluetoothAdapter)) {
                Toast.makeText(applicationContext, "sssss", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == ENABLE_BLUETOOTH_REQUEST_CODE) {
            if (resultCode == RESULT_CANCELED) {
                showErrorDialog(getString(R.string.bluetooth_rationale_message), requestEnableBluetoothLambda)
            }
        }
    }

    private val requestEnableBluetoothLambda = { _: DialogInterface, _: Int ->
        startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH_REQUEST_CODE)
    }

    private fun checkBluetoothEnabled(bluetoothAdapter: BluetoothAdapter?): Boolean {
        if (bluetoothAdapter != null && !bluetoothAdapter.isEnabled) {
            startActivityForResult(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE), ENABLE_BLUETOOTH_REQUEST_CODE)
            return false
        }

        return true
    }

    private fun showErrorDialog(message: String, function: (dialog: DialogInterface, which: Int) -> Unit = { _: DialogInterface, _: Int -> }, isCritical: Boolean = true) {
        val builder = AlertDialog.Builder(this)
        var negativeButtonText = getString(R.string.exit)
        val positiveButtonAction: (DialogInterface, Int) -> Unit = function
        var negativeButtonAction: (DialogInterface, Int) -> Unit = {_: DialogInterface, _: Int -> finishAffinity()}

        if (!isCritical) {
            negativeButtonAction = {_: DialogInterface, _: Int -> }
            negativeButtonText = getString(R.string.cancel)
        }

        builder.setTitle(getString(R.string.error_dialog_title))
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setPositiveButton(getString(R.string.ok), positiveButtonAction)
        builder.setNegativeButton(negativeButtonText, negativeButtonAction)
        builder.show()
    }

    private fun showRationaleDialog(permission: String, message: String, requestCode: Int, isCritical: Boolean = true) {
        val builder = AlertDialog.Builder(this)
        var negativeButtonText = getString(R.string.cancel)
        val positiveButtonAction: (DialogInterface, Int) -> Unit = { _, _ -> ActivityCompat.requestPermissions(this@MainActivity, arrayOf(permission), requestCode) }
        var negativeButtonAction: (DialogInterface, Int) -> Unit = {_: DialogInterface, _: Int -> }

        if (isCritical) {
            negativeButtonAction = {_: DialogInterface, _: Int -> finishAffinity()}
            negativeButtonText = getString(R.string.exit)
            builder.setCancelable(false)
        }

        builder.setTitle(getString(R.string.permission_rationale_dialog_title))
        builder.setMessage(message)
        builder.setCancelable(false)
        builder.setPositiveButton(getString(R.string.ok), positiveButtonAction)
        builder.setNegativeButton(negativeButtonText, negativeButtonAction)
        builder.show()
    }

    private fun checkPermission(permission: String, rationaleMessage: String, requestCode: Int, isCritical: Boolean = true): Boolean {
        when {
            ContextCompat.checkSelfPermission(applicationContext, permission) == PackageManager.PERMISSION_GRANTED -> {
                return true
            }

            shouldShowRequestPermissionRationale(permission) -> showRationaleDialog(permission, rationaleMessage, requestCode, isCritical)

            else -> ActivityCompat.requestPermissions(this, arrayOf(permission, BACKGROUND_LOCATION_PERMISSION), requestCode)
        }

        return false
    }

    private fun checkLocationPermissions() {
        if (checkPermission(FINE_LOCATION_PERMISSION, getString(R.string.fine_location_rationale_message), FINE_LOCATION_REQUEST_CODE)) {
            checkPermission(BACKGROUND_LOCATION_PERMISSION, getString(R.string.background_location_rationale_message), BACKGROUND_LOCATION_REQUEST_CODE, false)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        fun verifyPermission(rationaleMessage: String): Boolean {
            return if (grantResults.isEmpty() || grantResults[0] != PackageManager.PERMISSION_GRANTED) {
                showRationaleDialog(permissions[0], rationaleMessage, requestCode)
                false
            } else {
                true
            }
        }

        when (requestCode) {
            FINE_LOCATION_REQUEST_CODE -> {
                if(verifyPermission(getString(R.string.fine_location_rationale_message))) {
                    checkPermission(BACKGROUND_LOCATION_PERMISSION, getString(R.string.background_location_rationale_message), BACKGROUND_LOCATION_REQUEST_CODE, false)
                }
            }
            BACKGROUND_LOCATION_REQUEST_CODE -> verifyPermission(getString(R.string.background_location_rationale_message))
        }
    }

    override fun onBackPressed() {
        if (backPressedTime + 2000 > System.currentTimeMillis()) {
            super.onBackPressed()
        } else {
            Toast.makeText(applicationContext, getString(R.string.back_to_exit_message), Toast.LENGTH_SHORT).show()
        }

        backPressedTime = System.currentTimeMillis()
    }
}