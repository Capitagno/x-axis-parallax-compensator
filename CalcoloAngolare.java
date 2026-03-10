package com.ottica.tracking;

public class CalcoloAngolare {

    // costanti fisiche dell'hardware (espresse in millimetri)
    private static final double LUNGHEZZA_FOCALE = 3.5;
    private static final double LARGHEZZA_SENSORE = 4.2896056;

    // pre-calcolo della costante geometrica per ottimizzare le performance:
    // tan(FOV/2) = d / (2 * f)
    private static final double TAN_MEZZO_FOV = LARGHEZZA_SENSORE / (2.0 * LUNGHEZZA_FOCALE);

    /**
     * Calcola l'angolo di deviazione orizzontale del volto rispetto all'asse ottico.
     *
     * @param centroXPixel La coordinata orizzontale del volto (già filtrata)
     * @param larghezzaBufferPixel La larghezza reale del fotogramma YUV in quel momento
     * @return L'angolo di deviazione in gradi (positivo verso destra, negativo verso sinistra)
     */
    public double calcolaAngoloDeviazione(float centroXPixel, int larghezzaBufferPixel) {
        
        // 1. calcolo dello scostamento (Delta x) dal centro ottico teorico
        double centroOttico = larghezzaBufferPixel / 2.0;
        double scostamentoX = centroXPixel - centroOttico;

        // 2. applicazione della formula di proiezione ottica:
        // theta_x = arctan( (2 * Delta x / w_pixel) * tan(FOV/2) )
        double frazioneSpaziale = (2.0 * scostamentoX) / larghezzaBufferPixel;
        double angoloDeviazioneRadianti = Math.atan(frazioneSpaziale * TAN_MEZZO_FOV);

        // 3. conversione del risultato per il motore di rendering del display
        return Math.toDegrees(angoloDeviazioneRadianti);
    }
}
