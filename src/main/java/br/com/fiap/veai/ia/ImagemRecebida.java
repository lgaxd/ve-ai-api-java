package br.com.fiap.veai.ia;

import org.springframework.util.MimeType;

/**
 * Foto recebida do app. Vive só na memória durante a chamada: nunca é gravada em banco, disco ou log
 * (por isso o {@code toString} não expõe os bytes).
 */
public record ImagemRecebida(byte[] bytes, MimeType tipo) {

    @Override
    public String toString() {
        return "ImagemRecebida[" + tipo + ", " + bytes.length + " bytes]";
    }
}
