package com.ottica.tracking;

import android.annotation.SuppressLint;
import android.graphics.Rect;
import android.media.Image;
import android.media.ImageReader;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

public class RilevatoreVolto implements ImageReader.OnImageAvailableListener {

    private final FaceDetector inizializzatoreRete;
    private final int rotazioneSensoreGradi;
    private boolean elaborazioneInCorso = false;

    // riferimenti ai moduli matematici che scriveremo a breve
    private final FiltroPassaBasso filtroSpaziale;
    private final CalcoloAngolare calcoloOttico;

    public RilevatoreVolto(int rotazioneSensoreGradi) {
        this.rotazioneSensoreGradi = rotazioneSensoreGradi;
        
        // configurazione della rete neurale per bassa latenza
        // la classificazione semantica (sorriso, occhi aperti) e i landmark anatomici
        // vengono disabilitati per non saturare i cicli della cpu
        FaceDetectorOptions opzioniRete = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                .enableTracking()
                .build();

        this.inizializzatoreRete = FaceDetection.getClient(opzioniRete);
        
        // instanziazione dei modelli geometrici
        this.filtroSpaziale = new FiltroPassaBasso();
        this.calcoloOttico = new CalcoloAngolare();
    }

    @Override
    public void onImageAvailable(ImageReader lettore) {
        // estrazione dell'ultimo buffer disponibile direttamente dall'hardware
        Image fotogrammaHardware = lettore.acquireLatestImage();
        if (fotogrammaHardware == null) return;

        // se il processore tensoriale sta ancora elaborando la matrice precedente,
        // scartiamo questo fotogramma per non accumulare latenza (drop frame)
        if (elaborazioneInCorso) {
            fotogrammaHardware.close();
            return;
        }
        elaborazioneInCorso = true;

        int larghezzaBuffer = fotogrammaHardware.getWidth();
        int altezzaBuffer = fotogrammaHardware.getHeight();
        int larghezzaRealeInPixel;

        // compensazione vettoriale dell'orientamento hardware del sensore
        if (rotazioneSensoreGradi == 90 || rotazioneSensoreGradi == 270) {
            larghezzaRealeInPixel = altezzaBuffer;
        } else {
            larghezzaRealeInPixel = larghezzaBuffer;
        }

        @SuppressLint("UnsafeOptInUsageError")
        InputImage matriceVisione = InputImage.fromMediaImage(fotogrammaHardware, rotazioneSensoreGradi);

        inizializzatoreRete.process(matriceVisione)
                .addOnSuccessListener(volti -> {
                    for (Face volto : volti) {
                        // estrazione spaziale del baricentro del cranio
                        Rect limitiSpaziali = volto.getBoundingBox();
                        float centroXCorrente = limitiSpaziali.exactCenterX();
                        
                        // 1. stabilizzazione del segnale tramite modulo EWMA
                        float centroXFiltrato = filtroSpaziale.applicaFiltroEwma(centroXCorrente);
                        
                        // 2. proiezione ottica per ricavare l'angolo theta_x
                        double angoloDeviazioneGradi = calcoloOttico.calcolaAngoloDeviazione(centroXFiltrato, larghezzaRealeInPixel);
                        
                        // qui andrà il delegate verso il motore di interlacciamento del tablet
                        // per compensare dinamicamente la barriera di parallasse
                    }
                })
                .addOnFailureListener(e -> {
                    // registrazione dell'errore tensoriale nei log diagnostici
                })
                .addOnCompleteListener(task -> {
                    // chiusura rigorosa del buffer di memoria per prevenire memory leak
                    fotogrammaHardware.close();
                    elaborazioneInCorso = false;
                });
    }
}
