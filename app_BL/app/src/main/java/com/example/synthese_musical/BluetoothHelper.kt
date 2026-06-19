package com.example.synthese_musical

import android.bluetooth.BluetoothSocket
import java.io.OutputStream
import java.util.concurrent.Executors

object BluetoothHelper {
    var socket: BluetoothSocket? = null
    var outputStream: OutputStream? = null

    private val executor = Executors.newSingleThreadExecutor()

    fun estConnecte(): Boolean = socket?.isConnected == true && outputStream != null

    fun envoyerHexa(hexData: ByteArray) {
        val stream = outputStream ?: return
        executor.execute {
            try {
                stream.write(hexData)
                stream.flush()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun deconnecter() {
        try {
            outputStream?.close()
            socket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            outputStream = null
            socket = null
        }
    }
}
