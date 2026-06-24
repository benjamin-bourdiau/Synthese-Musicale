#include <Arduino.h> 
#include "notes.h" 

#define DAC_PIN     25 
#define RXD2        16 
#define TXD2        17 

#define MAX_NOTES   8   // Maximum 8 notes simultanées.
#define NUM_SAMPLES 64  // Taille de la table d'onde.

uint8_t sineWave[NUM_SAMPLES]; // Tableau pour stocker l'onde de base.

volatile uint32_t phaseAcc[MAX_NOTES]; // Position actuelle dans le sinus pour chaque slot.
volatile uint32_t phaseInc[MAX_NOTES]; // Vitesse de lecture (fréquence) pour chaque slot.

struct Voice { 
  byte  code;
  float freq;
};

Voice activeNotes[MAX_NOTES]; // État des notes dans chaque slot.

// Fréquence d'échantillonnage audio
const uint32_t SAMPLING_FREQ = 44100;

hw_timer_t * timer    = NULL; 
portMUX_TYPE timerMux = portMUX_INITIALIZER_UNLOCKED; 

// ─── INTERRUPTION HORLOGE AUDIO (ISR) ────────────────────────────────────────
void IRAM_ATTR onTimer() {
  int32_t mix = 0; 
  uint8_t activeVoices = 0;

  // On parcourt les 8 slots fixes de manière ultra-rapide
  for (uint8_t i = 0; i < MAX_NOTES; i++) { 
    if (phaseInc[i] > 0) { 
      phaseAcc[i] += phaseInc[i];              // Avancement de la phase
      uint32_t idx = (phaseAcc[i] >> 16) & 63; // Extraction de l'index (0-63)
      mix += (int32_t)sineWave[idx] - 128;     // Somme des signaux recentrés autour de 0
      activeVoices++;
    }
  }

  // Calcul de la moyenne pour éviter la saturation numérique
  if (activeVoices > 0) {
    mix /= activeVoices; 
  }
  mix += 128; // Recentrage pour le DAC (0-255)

  // Écrêtage de sécurité (anti-débordement)
  if (mix > 255) mix = 255; 
  else if (mix < 0) mix = 0; 

  // Envoi direct au DAC selon la version du cœur ESP32
  #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
    analogWrite(DAC_PIN, (uint8_t)mix); 
  #else
    dacWrite(DAC_PIN, (uint8_t)mix); 
  #endif
}

// ─── NOTE ON (Touche enfoncée) ────────────────────────────────────────────────
void noteOn(byte code, float freq) {
  // 1. Évite les doublons si la touche est déjà active
  for (uint8_t i = 0; i < MAX_NOTES; i++) {
    if (activeNotes[i].code == code) return; 
  }

  // 2. Cherche un emplacement (slot) libre
  for (uint8_t i = 0; i < MAX_NOTES; i++) {
    if (activeNotes[i].code == 0x00) { 
      
      // --- DEBUT ZONE CRITIQUE ---
      portENTER_CRITICAL(&timerMux); 
      
      // On configure le nouveau slot
      activeNotes[i] = {code, freq};
      phaseInc[i] = (uint32_t)((freq * NUM_SAMPLES * 65536.0f) / SAMPLING_FREQ); 
      
      // HARD SYNC : On force TOUTES les notes actives à recommencer leur cycle à 0
      for (uint8_t k = 0; k < MAX_NOTES; k++) {
        if (phaseInc[k] > 0) { 
          phaseAcc[k] = 0; 
        }
      }
      
      portEXIT_CRITICAL(&timerMux); 
      // --- FIN ZONE CRITIQUE ---
      
      Serial.print("NOTE ON  0x"); 
      Serial.print(code, HEX);
      Serial.printf(" (%1.f Hz) -> Slots resynchronisés.\n", freq);
      return;
    }
  }
  Serial.println("Polyphonie max atteinte (8 voix)."); 
}

// ─── NOTE OFF (Touche relâchée) ───────────────────────────────────────────────
void noteOff(byte onCode) {
  // On cherche le slot qui possède ce code pour l'éteindre directement
  for (uint8_t i = 0; i < MAX_NOTES; i++) {
    if (activeNotes[i].code == onCode) { 
      
      // --- DEBUT ZONE CRITIQUE ---
      portENTER_CRITICAL(&timerMux);
      activeNotes[i] = {0x00, 0.0f}; 
      phaseInc[i] = 0; // Coupe le traitement audio de ce slot
      phaseAcc[i] = 0; 
      portEXIT_CRITICAL(&timerMux);
      // --- FIN ZONE CRITIQUE ---

      Serial.printf("NOTE OFF 0x%02X libérée du slot %d\n", onCode, i); 
      return;
    }
  }
}

// ─── INITIALISATION ───────────────────────────────────────────────────────────
void setup() {
  Serial.begin(115200); 
  Serial2.begin(115200, SERIAL_8N1, RXD2, TXD2); 

  // Remplissage de la table d'onde (Sinus pur échantillonné sur 8 bits)
  for (int i = 0; i < NUM_SAMPLES; i++) {
    sineWave[i] = (uint8_t)(127.5f * sinf(2.0f * PI * i / NUM_SAMPLES) + 127.5f); 
  }

  // Nettoyage initial des mémoires de voix
  for (uint8_t i = 0; i < MAX_NOTES; i++) { 
    activeNotes[i] = {0x00, 0.0f};
    phaseAcc[i]    = 0;
    phaseInc[i]    = 0;
  }

  // Configuration précise du Timer de l'ESP32
  #if ESP_ARDUINO_VERSION >= ESP_ARDUINO_VERSION_VAL(3, 0, 0)
    timer = timerBegin(1000000); 
    timerAttachInterrupt(timer, &onTimer);
    timerAlarm(timer, 1000000 / SAMPLING_FREQ, true, 0);
  #else
    timer = timerBegin(0, 80, true); 
    timerAttachInterrupt(timer, &onTimer, true);
    timerAlarmWrite(timer, 1000000 / SAMPLING_FREQ, true);
    timerAlarmEnable(timer);
  #endif

  Serial.println("ESP32 Prêt — Mode Hard Sync Polyphonique activé."); 
}

// ─── BOUCLE PRINCIPALE ────────────────────────────────────────────────────────
void loop() {
  if (Serial2.available() > 0) { 
    byte data = Serial2.read(); 
    Serial.print("Code reçu en Hexa : 0x");
    Serial.println(data, HEX);

    switch (data) {
      // NOTE ON octave 2
      case 0x21: noteOn(0x21, NOTE_DO_2);    break;
      case 0x22: noteOn(0x22, NOTE_DO_D_2);  break;
      case 0x23: noteOn(0x23, NOTE_RE_2);    break;
      case 0x24: noteOn(0x24, NOTE_RE_D_2);  break;
      case 0x25: noteOn(0x25, NOTE_MI_2);    break;
      case 0x26: noteOn(0x26, NOTE_FA_2);    break;
      case 0x27: noteOn(0x27, NOTE_FA_D_2);  break;
      case 0x28: noteOn(0x28, NOTE_SOL_2);   break;
      case 0x29: noteOn(0x29, NOTE_SOL_D_2); break;
      case 0x2A: noteOn(0x2A, NOTE_LA_2);    break;
      case 0x2B: noteOn(0x2B, NOTE_LA_D_2);  break;
      case 0x2C: noteOn(0x2C, NOTE_SI_2);    break;
      
      // NOTE ON octave 3
      case 0x31: noteOn(0x31, NOTE_DO_3);    break;
      case 0x32: noteOn(0x32, NOTE_DO_D_3);  break;
      case 0x33: noteOn(0x33, NOTE_RE_3);    break;
      case 0x34: noteOn(0x34, NOTE_RE_D_3);  break;
      case 0x35: noteOn(0x35, NOTE_MI_3);    break;
      case 0x36: noteOn(0x36, NOTE_FA_3);    break;
      case 0x37: noteOn(0x37, NOTE_FA_D_3);  break;
      case 0x38: noteOn(0x38, NOTE_SOL_3);   break;
      case 0x39: noteOn(0x39, NOTE_SOL_D_3); break;
      case 0x3A: noteOn(0x3A, NOTE_LA_3);    break;
      case 0x3B: noteOn(0x3B, NOTE_LA_D_3);  break;
      case 0x3C: noteOn(0x3C, NOTE_SI_3);    break;
      
      // NOTE OFF octave 2
      case 0x41: noteOff(0x21); break;
      case 0x42: noteOff(0x22); break;
      case 0x43: noteOff(0x23); break;
      case 0x44: noteOff(0x24); break;
      case 0x45: noteOff(0x25); break;
      case 0x46: noteOff(0x26); break;
      case 0x47: noteOff(0x27); break;
      case 0x48: noteOff(0x28); break;
      case 0x49: noteOff(0x29); break;
      case 0x4A: noteOff(0x2A); break;
      case 0x4B: noteOff(0x2B); break;
      case 0x4C: noteOff(0x2C); break;
      
      // NOTE OFF octave 3
      case 0x51: noteOff(0x31); break;
      case 0x52: noteOff(0x32); break;
      case 0x53: noteOff(0x33); break;
      case 0x54: noteOff(0x34); break;
      case 0x55: noteOff(0x35); break;
      case 0x56: noteOff(0x36); break;
      case 0x57: noteOff(0x37); break;
      case 0x58: noteOff(0x38); break;
      case 0x59: noteOff(0x39); break;
      case 0x5A: noteOff(0x3A); break;
      case 0x5B: noteOff(0x3B); break;
      case 0x5C: noteOff(0x3C); break;

      // Bouton Panique / Silence d'urgence
      case 0x00: 
        Serial.println("PANIC — Arrêt immédiat");
        portENTER_CRITICAL(&timerMux);
        for (uint8_t i = 0; i < MAX_NOTES; i++) {
          activeNotes[i] = {0x00, 0.0f};
          phaseInc[i] = 0;
          phaseAcc[i] = 0;
        }
        portEXIT_CRITICAL(&timerMux);
        break;

      default:
        Serial.printf("Code inconnu 0x%02X, ignoré.\n", data);
        break;
    }
  }

  // Relais inverse (Console -> Équipement esclave)
  if (Serial.available() > 0) {
    Serial2.write(Serial.read());
  }
}