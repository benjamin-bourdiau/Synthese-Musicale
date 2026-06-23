package com.example.synthese_musical

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.graphics.Color
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Bundle
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import android.widget.ViewFlipper
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar

// ─── Modèle de données pour l'enregistrement ────────────────────────────────
data class RecordedEvent(val hexCode: Int, val deltaMs: Long)

class PianoFragment : Fragment() {

    // ─── Mode apprentissage ───
    private val keyMap = HashMap<Int, Button>()
    private var isLearningMode = false
    private var currentSongSequence = listOf<Int>()
    private var currentNoteIndex = 0
    private lateinit var toneGen: ToneGenerator

    // ─── Système d'enregistrement ───
    private var isRecording = false
    private var isPlaying = false

    private val allRecordings = mutableMapOf<String, List<RecordedEvent>>()
    private val currentRecording = mutableListOf<RecordedEvent>()
    private var currentRecordingName = ""

    private var lastEventTimeMs = 0L
    private var playbackThread: Thread? = null

    private lateinit var spinnerRecordings: Spinner
    private lateinit var recordingsAdapter: ArrayAdapter<String>

    // ─── Thread de lecture des partitions ───
    private var musicThread: Thread? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        val view = inflater.inflate(R.layout.fragment_piano, container, false)
        toneGen = ToneGenerator(AudioManager.STREAM_ALARM, 100)

        // ─── Octave 2 ────────────────────────────────────────────────────────
        setupKey(view.findViewById(R.id.keyC2),  0x21, 0x41)
        setupKey(view.findViewById(R.id.keyCh2), 0x22, 0x42)
        setupKey(view.findViewById(R.id.keyD2),  0x23, 0x43)
        setupKey(view.findViewById(R.id.keyDh2), 0x24, 0x44)
        setupKey(view.findViewById(R.id.keyE2),  0x25, 0x45)
        setupKey(view.findViewById(R.id.keyF2),  0x26, 0x46)
        setupKey(view.findViewById(R.id.keyFh2), 0x27, 0x47)
        setupKey(view.findViewById(R.id.keyG2),  0x28, 0x48)
        setupKey(view.findViewById(R.id.keyGh2), 0x29, 0x49)
        setupKey(view.findViewById(R.id.keyA2),  0x2A, 0x4A)
        setupKey(view.findViewById(R.id.keyAh2), 0x2B, 0x4B)
        setupKey(view.findViewById(R.id.keyB2),  0x2C, 0x4C)

        // ─── Octave 3 ────────────────────────────────────────────────────────
        setupKey(view.findViewById(R.id.keyC3),  0x31, 0x51)
        setupKey(view.findViewById(R.id.keyCh3), 0x32, 0x52)
        setupKey(view.findViewById(R.id.keyD3),  0x33, 0x53)
        setupKey(view.findViewById(R.id.keyDh3), 0x34, 0x54)
        setupKey(view.findViewById(R.id.keyE3),  0x35, 0x55)
        setupKey(view.findViewById(R.id.keyF3),  0x36, 0x56)
        setupKey(view.findViewById(R.id.keyFh3), 0x37, 0x57)
        setupKey(view.findViewById(R.id.keyG3),  0x38, 0x58)
        setupKey(view.findViewById(R.id.keyGh3), 0x39, 0x59)
        setupKey(view.findViewById(R.id.keyA3),  0x3A, 0x5A)
        setupKey(view.findViewById(R.id.keyAh3), 0x3B, 0x5B)
        setupKey(view.findViewById(R.id.keyB3),  0x3C, 0x5C)

        // ─── MENU (ViewFlipper) ──────────────────────────────────────────────
        val flipper = view.findViewById<ViewFlipper>(R.id.menuFlipper)

        view.findViewById<Button>(R.id.btnNavSons)?.setOnClickListener { flipper.displayedChild = 1 }
        view.findViewById<Button>(R.id.btnNavApprentissage)?.setOnClickListener { flipper.displayedChild = 2 }
        view.findViewById<Button>(R.id.btnNavEnregistrement)?.setOnClickListener { flipper.displayedChild = 3 }

        val btnBackListener = View.OnClickListener {
            resetApprentissage()
            flipper.displayedChild = 0
        }
        view.findViewById<Button>(R.id.btnBackSons)?.setOnClickListener(btnBackListener)
        view.findViewById<Button>(R.id.btnBackApprentissage)?.setOnClickListener(btnBackListener)
        view.findViewById<Button>(R.id.btnBackEnregistrement)?.setOnClickListener(btnBackListener)

        // ─── Partitions (Écoute automatique) ───
        view.findViewById<Button>(R.id.btnElise)?.setOnClickListener { lancerMusique { jouerLettreAElise() } }
        view.findViewById<Button>(R.id.btnMarche)?.setOnClickListener { lancerMusique { jouerMarcheImperiale() } }
        view.findViewById<Button>(R.id.btnTetris)?.setOnClickListener { lancerMusique { jouerTetris() } }
        view.findViewById<Button>(R.id.btnAnniv)?.setOnClickListener { lancerMusique { jouerJoyeuxAnniversaire() } }
        view.findViewById<Button>(R.id.btnPirate)?.setOnClickListener { lancerMusique { jouerPiratesCaraibes() } }
        view.findViewById<Button>(R.id.btnCrazy)?.setOnClickListener { lancerMusique { jouerCrazyFrog() } }

        // ─── Mode Apprentissage (Séquences complètes) ───
        view.findViewById<Button>(R.id.btnLearnElise)?.setOnClickListener {
            demarrerApprentissage(listOf(
                0x35, 0x34, 0x35, 0x34, 0x35, 0x2C, 0x33, 0x31, 0x2A,
                0x21, 0x25, 0x2A, 0x2C, 0x25, 0x29, 0x2C, 0x31
            ))
        }
        view.findViewById<Button>(R.id.btnLearnTetris)?.setOnClickListener {
            demarrerApprentissage(listOf(
                0x35, 0x2C, 0x31, 0x33, 0x31, 0x2C, 0x2A, 0x2A, 0x31, 0x35, 0x33, 0x31, 0x2C, 0x2C, 0x31, 0x33, 0x35, 0x31, 0x2A, 0x2A,
                0x33, 0x36, 0x3A, 0x38, 0x36, 0x35, 0x31, 0x35, 0x33, 0x31, 0x2C, 0x2C, 0x31, 0x33, 0x35, 0x31, 0x2A, 0x2A
            ))
        }
        view.findViewById<Button>(R.id.btnLearnMarche)?.setOnClickListener {
            demarrerApprentissage(listOf(
                0x2A, 0x2A, 0x2A, 0x26, 0x31, 0x2A, 0x26, 0x31, 0x2A
            ))
        }
        view.findViewById<Button>(R.id.btnLearnAnniv)?.setOnClickListener {
            demarrerApprentissage(listOf(
                0x28, 0x28, 0x2A, 0x28, 0x31, 0x2C, 0x28, 0x28, 0x2A, 0x28, 0x33, 0x31
            ))
        }
        view.findViewById<Button>(R.id.btnLearnPirate)?.setOnClickListener {
            demarrerApprentissage(listOf(
                0x2A, 0x31, 0x33, 0x33, 0x33, 0x35, 0x36, 0x36, 0x36, 0x38, 0x35, 0x35, 0x33, 0x31, 0x33
            ))
        }
        view.findViewById<Button>(R.id.btnLearnCrazy)?.setOnClickListener {
            demarrerApprentissage(listOf(
                0x33, 0x36, 0x33, 0x33, 0x38, 0x33, 0x31, // Première ligne (D, F, D, D, G, D, C)
                0x33, 0x3A, 0x33, 0x33, 0x3B, 0x3A, 0x36, // Deuxième ligne (D, A, D, D, Bb, A, F)
                0x33, 0x3A                                // Fin (D, A)
            ))
        }

        // ─── Page 3 : Enregistrement ─────────────────────────────────────────
        setupEnregistrement(view)

        return view
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // ENREGISTREMENT
    // ═══════════════════════════════════════════════════════════════════════════

    private fun setupEnregistrement(view: View) {
        val btnRecord  = view.findViewById<Button>(R.id.btnRecord)
        val btnStop    = view.findViewById<Button>(R.id.btnStopRecord)
        val btnPlay    = view.findViewById<Button>(R.id.btnPlayRecord)
        val btnDelete  = view.findViewById<Button>(R.id.btnDeleteRecord)
        val tvRecStatus = view.findViewById<TextView>(R.id.tvRecordStatus)
        spinnerRecordings = view.findViewById(R.id.spinnerRecordings)

        recordingsAdapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, mutableListOf())
        spinnerRecordings.adapter = recordingsAdapter

        updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)

        // ── ENREGISTRER ──
        btnRecord?.setOnClickListener {
            if (!BluetoothHelper.estConnecte()) {
                Toast.makeText(requireContext(), "Bluetooth non connecté", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isPlaying) {
                Toast.makeText(requireContext(), "Lecture en cours, arrêtez d'abord.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val builder = AlertDialog.Builder(requireContext())
            builder.setTitle("Nouvel Enregistrement")
            builder.setMessage("Donnez un nom à votre création :")

            val input = EditText(requireContext())
            builder.setView(input)

            builder.setPositiveButton("Démarrer") { _, _ ->
                val nom = input.text.toString().trim()
                if (nom.isNotEmpty()) {
                    currentRecordingName = nom
                    currentRecording.clear()
                    isRecording = true
                    lastEventTimeMs = System.currentTimeMillis()
                    updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)
                    Snackbar.make(requireView(), "⏺ Enregistrement '$nom' démarré…", Snackbar.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(requireContext(), "Le nom ne peut pas être vide.", Toast.LENGTH_SHORT).show()
                }
            }
            builder.setNegativeButton("Annuler", null)
            builder.show()
        }

        // ── ARRÊTER ──
        btnStop?.setOnClickListener {
            if (isRecording) {
                isRecording = false

                allRecordings[currentRecordingName] = currentRecording.toList()
                mettreAJourListeDeroulante()

                val position = recordingsAdapter.getPosition(currentRecordingName)
                spinnerRecordings.setSelection(position)

                val count = currentRecording.count { it.hexCode < 0x40 }
                Snackbar.make(requireView(), "⏹ '$currentRecordingName' sauvegardé ($count notes)", Snackbar.LENGTH_LONG).show()

                updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)

            } else if (isPlaying) {
                stopPlayback()
                updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)
            }
        }

        // ── ÉCOUTER LA SÉLECTION ──
        btnPlay?.setOnClickListener {
            val selectedName = spinnerRecordings.selectedItem as? String
            if (selectedName == null || !allRecordings.containsKey(selectedName)) {
                Toast.makeText(requireContext(), "Veuillez sélectionner un enregistrement", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (!BluetoothHelper.estConnecte()) {
                Toast.makeText(requireContext(), "Bluetooth non connecté", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            if (isPlaying) return@setOnClickListener

            val trackToPlay = allRecordings[selectedName] ?: return@setOnClickListener

            isPlaying = true
            updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)

            playbackThread = Thread {
                try {
                    for (event in trackToPlay) {
                        if (!isPlaying) break

                        if (event.deltaMs > 0) Thread.sleep(event.deltaMs)

                        BluetoothHelper.envoyerHexa(byteArrayOf(event.hexCode.toByte()))

                        activity?.runOnUiThread {
                            val isNoteOn = event.hexCode < 0x40
                            val noteOnCode = if (isNoteOn) event.hexCode else event.hexCode - 0x20

                            val buttonToAnimate = keyMap[noteOnCode]

                            if (isNoteOn) {
                                buttonToAnimate?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#802196F3"))
                                buttonToAnimate?.animate()?.translationY(10f)?.setDuration(50)?.start()
                            } else {
                                buttonToAnimate?.backgroundTintList = null
                                buttonToAnimate?.animate()?.translationY(0f)?.setDuration(50)?.start()
                            }
                        }
                    }
                } catch (_: InterruptedException) {
                } finally {
                    activity?.runOnUiThread {
                        isPlaying = false
                        updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)

                        keyMap.values.forEach {
                            it.backgroundTintList = null
                            it.animate().translationY(0f).setDuration(50).start()
                        }

                        if (isAdded) Toast.makeText(requireContext(), "Lecture terminée", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            playbackThread?.start()
        }

        // ── SUPPRIMER LA SÉLECTION ──
        btnDelete?.setOnClickListener {
            val selectedName = spinnerRecordings.selectedItem as? String
            if (selectedName != null && allRecordings.containsKey(selectedName)) {
                stopPlayback()
                allRecordings.remove(selectedName)
                mettreAJourListeDeroulante()
                updateRecordingUI(btnRecord, btnStop, btnPlay, btnDelete, tvRecStatus)
                Snackbar.make(requireView(), "Piste '$selectedName' supprimée.", Snackbar.LENGTH_SHORT).show()
            }
        }
    }

    private fun mettreAJourListeDeroulante() {
        recordingsAdapter.clear()
        recordingsAdapter.addAll(allRecordings.keys)
        recordingsAdapter.notifyDataSetChanged()
    }

    private fun enregistrerEvenement(hexCode: Int) {
        if (!isRecording) return
        val now = System.currentTimeMillis()
        val delta = now - lastEventTimeMs
        lastEventTimeMs = now
        currentRecording.add(RecordedEvent(hexCode, delta))
    }

    private fun stopPlayback() {
        isPlaying = false
        playbackThread?.interrupt()
        playbackThread = null
    }

    private fun updateRecordingUI(btnRecord: Button?, btnStop: Button?, btnPlay: Button?, btnDelete: Button?, tvStatus: TextView?) {
        val hasSelectedRecording = spinnerRecordings.selectedItem != null
        when {
            isRecording -> {
                tvStatus?.text = "⏺  Enregistrement '$currentRecordingName' en cours…"
                tvStatus?.setTextColor(Color.parseColor("#F44336"))
                btnRecord?.isEnabled  = false; btnStop?.isEnabled    = true
                btnPlay?.isEnabled    = false; btnDelete?.isEnabled  = false
                spinnerRecordings.isEnabled = false
            }
            isPlaying -> {
                val selected = spinnerRecordings.selectedItem as? String
                tvStatus?.text = "▶  Lecture de '$selected' en cours…"
                tvStatus?.setTextColor(Color.parseColor("#2196F3"))
                btnRecord?.isEnabled  = false; btnStop?.isEnabled    = true
                btnPlay?.isEnabled    = false; btnDelete?.isEnabled  = false
                spinnerRecordings.isEnabled = false
            }
            hasSelectedRecording -> {
                val selected = spinnerRecordings.selectedItem as? String
                val noteCount = allRecordings[selected]?.count { it.hexCode < 0x40 } ?: 0
                tvStatus?.text = "✔  Piste '$selected' prête ($noteCount notes)"
                tvStatus?.setTextColor(Color.parseColor("#4CAF50"))
                btnRecord?.isEnabled  = true; btnStop?.isEnabled    = false
                btnPlay?.isEnabled    = true; btnDelete?.isEnabled  = true
                spinnerRecordings.isEnabled = true
            }
            else -> {
                tvStatus?.text = "Aucun enregistrement en mémoire"
                tvStatus?.setTextColor(Color.parseColor("#888888"))
                btnRecord?.isEnabled  = true; btnStop?.isEnabled    = false
                btnPlay?.isEnabled    = false; btnDelete?.isEnabled  = false
                spinnerRecordings.isEnabled = true
            }
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // MODE APPRENTISSAGE
    // ═══════════════════════════════════════════════════════════════════════════

    private fun demarrerApprentissage(sequence: List<Int>) {
        resetApprentissage()
        isLearningMode = true
        currentSongSequence = sequence
        currentNoteIndex = 0
        Snackbar.make(requireView(), "Mode Apprentissage Activé ! À toi de jouer.", Snackbar.LENGTH_SHORT).show()
        surlignerNoteActuelle()
    }

    private fun resetApprentissage() {
        isLearningMode = false
        keyMap.values.forEach { it.backgroundTintList = null }
    }

    private fun surlignerNoteActuelle() {
        if (currentNoteIndex < currentSongSequence.size) {
            val expectedNote = currentSongSequence[currentNoteIndex]
            keyMap[expectedNote]?.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#802196F3"))
        } else {
            isLearningMode = false
            Snackbar.make(requireView(), "Bravo ! Morceau terminé 🎵", Snackbar.LENGTH_LONG).show()
        }
    }

    private fun verifierNoteApprentissage(hexCodeDown: Int) {
        if (currentNoteIndex >= currentSongSequence.size) return
        val expectedNote = currentSongSequence[currentNoteIndex]
        if (hexCodeDown == expectedNote) {
            sendByte(hexCodeDown)
            keyMap[expectedNote]?.backgroundTintList = null
            currentNoteIndex++
            surlignerNoteActuelle()
        } else {
            toneGen.startTone(ToneGenerator.TONE_CDMA_ABBR_INTERCEPT, 150)
            Snackbar.make(requireView(), "Faux ! Essaie encore.", Snackbar.LENGTH_SHORT).show()
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // BLUETOOTH & TOUCHES
    // ═══════════════════════════════════════════════════════════════════════════

    private fun sendByte(code: Int) {
        if (!BluetoothHelper.estConnecte()) {
            Toast.makeText(requireContext(), "Bluetooth non connecté", Toast.LENGTH_SHORT).show()
            return
        }
        BluetoothHelper.envoyerHexa(byteArrayOf(code.toByte()))
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupKey(button: Button, hexCodeDown: Int, hexCodeUp: Int) {
        keyMap[hexCodeDown] = button

        button.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    if (isLearningMode) {
                        verifierNoteApprentissage(hexCodeDown)
                    } else {
                        sendByte(hexCodeDown)
                        enregistrerEvenement(hexCodeDown)
                    }
                    button.animate().translationY(10f).alpha(0.8f).setDuration(50).start()
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    sendByte(hexCodeUp)
                    if (!isLearningMode) {
                        enregistrerEvenement(hexCodeUp)
                    }
                    button.animate().translationY(0f).alpha(1.0f).setDuration(50).start()
                }
            }
            true
        }
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // LECTURE DES PARTITIONS
    // ═══════════════════════════════════════════════════════════════════════════

    private fun jouerNote(noteOn: Int, noteOff: Int, dureeMs: Long) {
        BluetoothHelper.envoyerHexa(byteArrayOf(noteOn.toByte()))
        Thread.sleep(dureeMs)
        BluetoothHelper.envoyerHexa(byteArrayOf(noteOff.toByte()))
        Thread.sleep(50)
    }

    private fun lancerMusique(musique: () -> Unit) {
        if (!BluetoothHelper.estConnecte()) {
            Toast.makeText(requireContext(), "Bluetooth non connecté", Toast.LENGTH_SHORT).show()
            return
        }
        musicThread?.interrupt()
        musicThread = Thread {
            try {
                musique()
            } catch (_: InterruptedException) {
            } finally {
                activity?.runOnUiThread {
                    if (isAdded) Toast.makeText(requireContext(), "Terminé", Toast.LENGTH_SHORT).show()
                }
            }
        }
        musicThread?.start()
    }

    private fun jouerLettreAElise() {
        val rapide = 250L; val lent = 500L
        jouerNote(0x35, 0x55, rapide); jouerNote(0x34, 0x54, rapide)
        jouerNote(0x35, 0x55, rapide); jouerNote(0x34, 0x54, rapide)
        jouerNote(0x35, 0x55, rapide); jouerNote(0x2C, 0x4C, rapide)
        jouerNote(0x33, 0x53, rapide); jouerNote(0x31, 0x51, rapide)
        jouerNote(0x2A, 0x4A, lent);   Thread.sleep(200)
        jouerNote(0x21, 0x41, rapide); jouerNote(0x25, 0x45, rapide)
        jouerNote(0x2A, 0x4A, rapide); jouerNote(0x2C, 0x4C, lent);  Thread.sleep(200)
        jouerNote(0x25, 0x45, rapide); jouerNote(0x29, 0x49, rapide)
        jouerNote(0x2C, 0x4C, rapide); jouerNote(0x31, 0x51, lent)
    }

    private fun jouerMarcheImperiale() {
        val noir = 500L; val crochePoint = 350L; val doubleCroche = 150L
        jouerNote(0x2A, 0x4A, noir);         jouerNote(0x2A, 0x4A, noir)
        jouerNote(0x2A, 0x4A, noir);         jouerNote(0x26, 0x46, crochePoint)
        jouerNote(0x31, 0x51, doubleCroche); jouerNote(0x2A, 0x4A, noir)
        jouerNote(0x26, 0x46, crochePoint);  jouerNote(0x31, 0x51, doubleCroche)
        jouerNote(0x2A, 0x4A, 1000L)
    }

    private fun jouerTetris() {
        val noir = 400L; val croche = 200L; val blanche = 800L
        jouerNote(0x35, 0x55, noir);   jouerNote(0x2C, 0x4C, croche)
        jouerNote(0x31, 0x51, croche); jouerNote(0x33, 0x53, noir)
        jouerNote(0x31, 0x51, croche); jouerNote(0x2C, 0x4C, croche)
        jouerNote(0x2A, 0x4A, noir);   jouerNote(0x2A, 0x4A, croche)
        jouerNote(0x31, 0x51, croche); jouerNote(0x35, 0x55, noir)
        jouerNote(0x33, 0x53, croche); jouerNote(0x31, 0x51, croche)
        jouerNote(0x2C, 0x4C, noir);   jouerNote(0x2C, 0x4C, croche)
        jouerNote(0x31, 0x51, croche); jouerNote(0x33, 0x53, noir)
        jouerNote(0x35, 0x55, noir);   jouerNote(0x31, 0x51, noir)
        jouerNote(0x2A, 0x4A, noir);   jouerNote(0x2A, 0x4A, blanche)
        Thread.sleep(100)
        jouerNote(0x33, 0x53, noir + croche); jouerNote(0x36, 0x56, croche)
        jouerNote(0x3A, 0x5A, noir);          jouerNote(0x38, 0x58, croche)
        jouerNote(0x36, 0x56, croche);        jouerNote(0x35, 0x55, noir + croche)
        jouerNote(0x31, 0x51, croche);        jouerNote(0x35, 0x55, noir)
        jouerNote(0x33, 0x53, croche);        jouerNote(0x31, 0x51, croche)
        jouerNote(0x2C, 0x4C, noir);          jouerNote(0x2C, 0x4C, croche)
        jouerNote(0x31, 0x51, croche);        jouerNote(0x33, 0x53, noir)
        jouerNote(0x35, 0x55, noir);          jouerNote(0x31, 0x51, noir)
        jouerNote(0x2A, 0x4A, noir);          jouerNote(0x2A, 0x4A, blanche)
    }

    private fun jouerJoyeuxAnniversaire() {
        val croche = 250L; val noir = 500L; val blanche = 1000L
        jouerNote(0x28, 0x48, croche);  jouerNote(0x28, 0x48, croche)
        jouerNote(0x2A, 0x4A, noir);    jouerNote(0x28, 0x48, noir)
        jouerNote(0x31, 0x51, noir);    jouerNote(0x2C, 0x4C, blanche)
        Thread.sleep(200)
        jouerNote(0x28, 0x48, croche);  jouerNote(0x28, 0x48, croche)
        jouerNote(0x2A, 0x4A, noir);    jouerNote(0x28, 0x48, noir)
        jouerNote(0x33, 0x53, noir);    jouerNote(0x31, 0x51, blanche)
    }

    private fun jouerPiratesCaraibes() {
        val rapide = 150L; val moyen = 300L; val long = 600L
        jouerNote(0x2A, 0x4A, rapide); jouerNote(0x31, 0x51, rapide)
        jouerNote(0x33, 0x53, moyen);  jouerNote(0x33, 0x53, moyen)
        jouerNote(0x33, 0x53, rapide); jouerNote(0x35, 0x55, rapide)
        jouerNote(0x36, 0x56, moyen);  jouerNote(0x36, 0x56, moyen)
        jouerNote(0x36, 0x56, rapide); jouerNote(0x38, 0x58, rapide)
        jouerNote(0x35, 0x55, moyen);  jouerNote(0x35, 0x55, moyen)
        jouerNote(0x33, 0x53, rapide); jouerNote(0x31, 0x51, rapide)
        jouerNote(0x33, 0x53, long)
    }

    private fun jouerCrazyFrog() {
        val normal = 400L
        val court = 200L
        val tresCourt = 100L

        // Première partie
        jouerNote(0x33, 0x53, normal)    // Ré3 (D)
        jouerNote(0x36, 0x56, court)    // Fa3 (F)
        jouerNote(0x33, 0x53, court)     // Ré3 (D)
        jouerNote(0x33, 0x53, tresCourt) // Ré3 (D) rapide
        jouerNote(0x38, 0x58, court)     // Sol3 (G)
        jouerNote(0x33, 0x53, court)     // Ré3 (D)
        jouerNote(0x31, 0x51, court)    // Do3 (C)

        Thread.sleep(100) // Petite pause pour le rythme

        // Deuxième partie
        jouerNote(0x33, 0x53, normal)    // Ré3 (D)
        jouerNote(0x3A, 0x5A, normal)    // La3 (A)
        jouerNote(0x33, 0x53, court)     // Ré3 (D)
        jouerNote(0x33, 0x53, tresCourt) // Ré3 (D) rapide
        jouerNote(0x3B, 0x5B, court)     // La#3 / Sib3 (Bb)
        jouerNote(0x3A, 0x5A, court)     // La3 (A)
        jouerNote(0x36, 0x56, normal)    // Fa3 (F)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        stopPlayback()
        musicThread?.interrupt()
        musicThread = null
        isRecording = false
        toneGen.release()
    }
}