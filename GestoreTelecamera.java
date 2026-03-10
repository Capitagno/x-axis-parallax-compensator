package com.ottica.tracking;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.ImageFormat;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.media.ImageReader;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Range;
import android.util.Size;
import android.view.Surface;
import java.util.Arrays;

public class GestoreTelecamera {

    private CameraDevice telecameraHardware;
    private CameraCaptureSession sessioneCattura;
    private ImageReader lettoreFotogrammi;
    private HandlerThread threadBackground;
    private Handler handlerBackground;
    
    // costanti operative
    private static final int LARGHEZZA_ELABORAZIONE = 640;
    private static final int ALTEZZA_ELABORAZIONE = 480;
    private static final int FPS_TARGET = 30;

    public void avviaTelecamera(Context contesto, String idTelecamera, ImageReader.OnImageAvailableListener listenerElaborazione) {
        avviaThreadBackground();

        // il formato yuv_420_888 separa luminanza e crominanza, ottimizzando i cicli cpu
        lettoreFotogrammi = ImageReader.newInstance(
                LARGHEZZA_ELABORAZIONE, 
                ALTEZZA_ELABORAZIONE, 
                ImageFormat.YUV_420_888, 
                2 // buffer circolare minimo per azzerare la latenza
        );
        lettoreFotogrammi.setOnImageAvailableListener(listenerElaborazione, handlerBackground);

        CameraManager manager = (CameraManager) contesto.getSystemService(Context.CAMERA_SERVICE);
        try {
            apriSensoreOttico(manager, idTelecamera);
        } catch (SecurityException | CameraAccessException e) {
            e.printStackTrace(); // qui andrà inserita la gestione diagnostica dell'errore
        }
    }

    @SuppressLint("MissingPermission")
    private void apriSensoreOttico(CameraManager manager, String id) throws CameraAccessException {
        manager.openCamera(id, new CameraDevice.StateCallback() {
            @Override
            public void onOpened(CameraDevice camera) {
                telecameraHardware = camera;
                inizializzaFlussoVideoCostante();
            }

            @Override
            public void onDisconnected(CameraDevice camera) {
                camera.close();
                telecameraHardware = null;
            }

            @Override
            public void onError(CameraDevice camera, int error) {
                camera.close();
                telecameraHardware = null;
            }
        }, handlerBackground);
    }

    private void inizializzaFlussoVideoCostante() {
        try {
            Surface superficieAcquisizione = lettoreFotogrammi.getSurface();
            
            // configurazione per massimizzare la velocità di estrazione dei frame
            final CaptureRequest.Builder costruttoreRichiesta = telecameraHardware.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            costruttoreRichiesta.addTarget(superficieAcquisizione);

            // vincolo hardware: blocco rigoroso della frequenza di campionamento a 30 fps
            Range<Integer> rangeFpsCostante = new Range<>(FPS_TARGET, FPS_TARGET);
            costruttoreRichiesta.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, rangeFpsCostante);

            // disabilitazione della stabilizzazione ottica/digitale per evitare alterazioni vettoriali
            costruttoreRichiesta.set(CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE, CaptureRequest.LENS_OPTICAL_STABILIZATION_MODE_OFF);
            costruttoreRichiesta.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE_OFF);

            telecameraHardware.createCaptureSession(Arrays.asList(superficieAcquisizione), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession session) {
                    sessioneCattura = session;
                    try {
                        // richiesta continua dei fotogrammi
                        sessioneCattura.setRepeatingRequest(costruttoreRichiesta.build(), null, handlerBackground);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    // fallimento configurazione hardware
                }
            }, handlerBackground);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void avviaThreadBackground() {
        threadBackground = new HandlerThread("CampionamentoOttico");
        threadBackground.start();
        handlerBackground = new Handler(threadBackground.getLooper());
    }

    public void chiudiTelecamera() {
        if (sessioneCattura != null) {
            sessioneCattura.close();
            sessioneCattura = null;
        }
        if (telecameraHardware != null) {
            telecameraHardware.close();
            telecameraHardware = null;
        }
        if (lettoreFotogrammi != null) {
            lettoreFotogrammi.close();
            lettoreFotogrammi = null;
        }
        if (threadBackground != null) {
            threadBackground.quitSafely();
            try {
                threadBackground.join();
                threadBackground = null;
                handlerBackground = null;
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
