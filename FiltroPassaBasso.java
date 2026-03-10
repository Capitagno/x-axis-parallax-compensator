package com.ottica.tracking;

public class FiltroPassaBasso {

    // la costante di reattività (alpha) governa la frequenza di taglio del filtro.
    // un valore prossimo a 0.0 congela il segnale (inerzia infinita).
    // un valore di 1.0 annulla il filtro (massimo rumore).
    // il valore 0.3 è il punto di lavoro ottimale per un campionamento vincolato a 30 fps.
    private static final float FATTORE_SMORZAMENTO = 0.3f;
    
    // variabile di stato per memorizzare il valore calcolato al tempo (t-1)
    private float statoPrecedente = -1.0f;

    public float applicaFiltroEwma(float valoreCorrente) {
        // condizione iniziale: se il filtro è vuoto, inizializziamo lo stato
        if (statoPrecedente < 0.0f) {
            statoPrecedente = valoreCorrente;
            return valoreCorrente;
        }

        // applicazione dell'equazione differenziale alle differenze finite:
        // X_filtrato = alpha * X_corrente + (1 - alpha) * X_precedente
        float valoreFiltrato = (FATTORE_SMORZAMENTO * valoreCorrente) + ((1.0f - FATTORE_SMORZAMENTO) * statoPrecedente);
        
        // aggiornamento della memoria per il ciclo di clock successivo
        statoPrecedente = valoreFiltrato;
        
        return valoreFiltrato;
    }

    public void resettaFiltro() {
        // da invocare tassativamente quando l'algoritmo perde il tracciamento
        // (es. l'osservatore esce dal campo visivo) per evitare che il nuovo ingresso
        // venga mediato con coordinate spaziali ormai obsolete.
        statoPrecedente = -1.0f;
    }
}
