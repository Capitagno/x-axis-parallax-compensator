# x-axis-parallax-compensator
# parallax-tracker-android

**Sistema di tracciamento facciale sull'asse x per la compensazione dinamica della parallasse e la prevenzione della visione pseudoscopica su display autostereoscopici (glasses-free).**

Questo modulo implementa un sistema di tracciamento spaziale a bassa latenza su architettura Android. L'algoritmo calcola l'angolo di deviazione orizzontale dell'osservatore rispetto all'asse ottico principale, fornendo il parametro vettoriale necessario per lo shift dinamico dell'interlacciamento e la stabilizzazione del lobo ortostereoscopico.

##  Architettura e Requisiti

Il software è stato sviluppato e ottimizzato per dispositivi di fascia media/industrial, con focus sul mantenimento di un frame rate rigorosamente costante per annullare il jitter temporale durante il ricalcolo delle immagini 3D.

*   **OS Target:** Android 8.0 (API Level 26) o superiore.
*   **Vision Engine:** Google ML Kit (Face Detection) in modalità `FAST_MODE` locale.
*   **Acquisizione Video:** Camera2 API (formato YUV_420_888).
*   **Orientamento Display/Sensore:** Coassiale in Landscape (Sensore montato nativamente sul lato lungo).

---

<details>
<summary><b>Specifiche Hardware di Riferimento (clicca per espandere)</b></summary>
<br>
Il sistema geometrico è stato originariamente calibrato sui seguenti parametri estratti dai metadati hardware (CameraCharacteristics). 
<i>Nota: L'algoritmo è progettato per essere adattato a specifiche differenti semplicemente aggiornando queste costanti nella classe di configurazione.</i>

*   **Risoluzione Sensore:** 5 MP
*   **Lunghezza Focale Equivalente ($f$):** 3.5 mm
*   **Dimensioni Fisiche Sensore:** 4.2896056 mm (larghezza) x 2.5477917 mm (altezza)
*   **Control FPS Range:** Forzato via API sul range fisso [30-30] fps (ignorando i range variabili 5-30, 15-15, 24-24 per garantire costanza d'esposizione).
</details>

<details>
<summary><b>Modello ottico e matematico (clicca per espandere)</b></summary>
<br>
Il calcolo della posizione spaziale si fonda sul modello della fotocamera stenopeica (pinhole camera). Tutte le equazioni sono implementate per ridurre al minimo il carico sulla CPU.

### 1. Angolo di visuale orizzontale (FOV)
L'angolo di campo reale orizzontale $\alpha$ è determinato analiticamente dalle dimensioni fisiche del sensore:

$$\alpha = 2 \cdot \arctan\left(\frac{d}{2f}\right)$$

Inserendo i dati del sensore di riferimento ($d = 4.2896056$ mm e $f = 3.5$ mm), otteniamo un FOV orizzontale $\alpha \approx 63^\circ$.

### 2. Scostamento dal centro ottico ($\Delta x$)
La coordinata spaziale orizzontale del baricentro del volto ($x_{pixel}$) viene sottratta dal centro ottico ideale della matrice del sensore:

$$\Delta x = x_{pixel} - \frac{w_{pixel}}{2}$$

Dove $w_{pixel}$ rappresenta la larghezza reale in pixel del buffer analizzato, ricavata dinamicamente ad ogni fotogramma per escludere artefatti da cropping software del sistema operativo.

### 3. Angolo di deviazione orizzontale ($\theta_x$)
La traslazione sul piano focale viene proiettata geometricamente per ricavare l'angolo di incidenza del volto rispetto alla normale del display:

$$\theta_x = \arctan\left(\frac{2 \cdot \Delta x}{w_{pixel}} \cdot \tan\left(\frac{\alpha}{2}\right)\right)$$

### 4. Stabilizzazione del segnale (Filtro EWMA)
Per neutralizzare il rumore di quantizzazione spaziale e i micro-movimenti (causa primaria di sfarfallio nel rendering 3D), il valore grezzo della coordinata spaziale viene processato mediante un filtro passa-basso esponenziale mobile (EWMA):

$$X_{filtrato} = \gamma \cdot X_{corrente} + (1 - \gamma) \cdot X_{precedente}$$

La costante di reattività $\gamma$ (es. 0.3) è ottimizzata per bilanciare latenza e stabilità del dato inviato al motore di interlacciamento.
</details>

<details>
<summary><b>Struttura del progetto (clicca per espandere)</b></summary>

```text
/face-tracking-autostereoscopico
├── README.md
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── java/com/ottica/tracking/
│   │   │   │   ├── RilevatoreVolto.java       (Integrazione ML Kit)
│   │   │   │   ├── FiltroPassaBasso.java      (Algoritmo EWMA)
│   │   │   │   ├── CalcoloAngolare.java       (Trigonometria e ottica geometrica)
│   │   │   │   └── GestoreTelecamera.java     (Inizializzazione Camera2 API a 30fps costanti)
│   │   │   └── AndroidManifest.xml
│   └── build.gradle
└── .gitignore
```
</details>

<details>
<summary><b>Linee guida per l'integrazione software</b></summary>

Questo modulo espone l'angolo di deviazione spaziale pre-calcolato e stabilizzato. Per iniettare il parametro nel motore di rendering autostereoscopico:

1. Istanziare la classe `GestoreTelecamera` all'interno del ciclo di vita dell'activity principale, garantendo la chiusura asincrona del buffer video tramite il metodo `chiudiTelecamera()` durante gli eventi onPause o onDestroy per liberare le risorse hardware.
2. Raccogliere l'output del metodo `calcolaAngoloDeviazione()` presente nella classe `CalcoloAngolare`. Il valore restituito è un numero decimale espresso in gradi sessagesimali, già depurato dal rumore ad alta frequenza tramite il filtro esponenziale mobile (EWMA).
3. Mappare questa variabile angolare in una funzione di traslazione per determinare lo shift dei sub-pixel. L'angolo definisce lo scostamento orizzontale $\Delta x$ da applicare alla maschera di interlacciamento del display per mantenere i raggi luminosi coincidenti con l'asse visivo dell'osservatore, compensando la parallasse in tempo reale.

</details>

---

## Struttura del Progetto

*   `GestoreTelecamera.java`: Interfaccia a basso livello con Camera2 API per il blocco dell'AE/AF e il pinning a 30 fps.
*   `RilevatoreVolto.java`: Wrapper per ML Kit e gestione asincrona dei buffer YUV.
*   `CalcoloAngolare.java`: Modello matematico per la conversione pixel-to-degree.
*   `FiltroPassaBasso.java`: Stabilizzazione vettoriale del movimento spaziale.

## Licenza VisionApps srl 2026
