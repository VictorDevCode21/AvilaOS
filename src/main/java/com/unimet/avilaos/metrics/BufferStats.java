package com.unimet.avilaos.metrics;

/**
 * Contadores del productor-consumidor de UN buffer:
 * cuántos elementos se han producido y cuántos se han consumido.
 *
 * No es thread-safe por sí sola: SystemMetrics la protege con su mutex.
 *
 * @author NicollPinzon
 */
public class BufferStats {

    private final String bufferName;
    private long produced;
    private long consumed;

    public BufferStats(String bufferName) {
        if (bufferName == null || bufferName.isBlank()) {
            throw new IllegalArgumentException("El buffer debe tener nombre");
        }
        this.bufferName = bufferName.trim();
    }

    void addProduced() {
        produced++;
    }

    void addConsumed() {
        consumed++;
    }

    /** Copia con los valores actuales, para entregarla a la GUI sin exponer el original. */
    BufferStats copy() {
        BufferStats result = new BufferStats(bufferName);
        result.produced = produced;
        result.consumed = consumed;
        return result;
    }

    public String getBufferName() {
        return bufferName;
    }

    public long getProduced() {
        return produced;
    }

    public long getConsumed() {
        return consumed;
    }
}
