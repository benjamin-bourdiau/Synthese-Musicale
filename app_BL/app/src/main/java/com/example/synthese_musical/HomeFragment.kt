package com.example.synthese_musical

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import java.util.UUID

class HomeFragment : Fragment() {

    private val sppUuid: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

    private lateinit var bluetoothAdapter: BluetoothAdapter
    private lateinit var btnConnect: Button
    private lateinit var tvStatus: TextView

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        if (permissions.values.all { it }) {
            startConnectionFlow()
        } else {
            setStatus("Permissions Bluetooth refusées")
        }
    }

    private val enableBluetoothLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        if (bluetoothAdapter.isEnabled) {
            connectToPreferredDevice()
        } else {
            setStatus("Bluetooth non activé")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_home, container, false)

        btnConnect = view.findViewById(R.id.btnConnect)
        tvStatus = view.findViewById(R.id.tvStatus)

        val manager = requireContext().getSystemService(BluetoothManager::class.java)
        bluetoothAdapter = manager.adapter

        btnConnect.setOnClickListener { checkPermissionsThenConnect() }

        return view
    }

    private fun checkPermissionsThenConnect() {
        val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            emptyArray()
        }

        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(requireContext(), it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isEmpty()) {
            startConnectionFlow()
        } else {
            permissionLauncher.launch(missing.toTypedArray())
        }
    }

    private fun startConnectionFlow() {
        if (!bluetoothAdapter.isEnabled) {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            return
        }
        connectToPreferredDevice()
    }

    @SuppressLint("MissingPermission")
    private fun connectToPreferredDevice() {
        if (!hasBluetoothPermission()) return

        setStatus("Recherche de l'instrument...")

        // Recherche d'un appareil jumelé correspondant
        val preferred = bluetoothAdapter.bondedDevices.firstOrNull { device ->
            val name = device.name?.uppercase().orEmpty()
            name.contains("HC-05") || name.contains("HC05") || name.contains("IT2R07") || name.contains("PIANOBT")
        }

        if (preferred == null) {
            setStatus("Appareil introuvable. Veuillez le jumeler dans les paramètres Android.")
            return
        }

        connectToDevice(preferred)
    }

    @SuppressLint("MissingPermission")
    private fun connectToDevice(device: BluetoothDevice) {
        setStatus("Connexion à ${device.name ?: "l'instrument"}...")
        btnConnect.isEnabled = false
        btnConnect.alpha = 0.5f // Donne un aspect visuel désactivé

        Thread {
            try {
                val socket = device.createRfcommSocketToServiceRecord(sppUuid)
                socket.connect()
                BluetoothHelper.socket = socket
                BluetoothHelper.outputStream = socket.outputStream

                requireActivity().runOnUiThread {
                    btnConnect.isEnabled = true
                    btnConnect.alpha = 1.0f
                    setStatus("CONNECTÉ")
                    Toast.makeText(requireContext(), "Instrument connecté", Toast.LENGTH_SHORT).show()
                    (requireActivity() as MainActivity).showPiano()
                }
            } catch (e: Exception) {
                requireActivity().runOnUiThread {
                    btnConnect.isEnabled = true
                    btnConnect.alpha = 1.0f
                    setStatus("Échec : ${e.message ?: "Erreur réseau"}")
                }
            }
        }.start()
    }

    private fun hasBluetoothPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        return ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun setStatus(message: String) {
        tvStatus.text = message
    }
}