package br.com.fiap.veai.descricao;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.springframework.http.HttpStatus;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.multipart.MultipartFile;

import br.com.fiap.veai.comum.ApiException;
import br.com.fiap.veai.ia.ImagemRecebida;

/**
 * Aceita JPEG, PNG e WebP de até 5 MB. O formato é conferido pelos primeiros bytes do arquivo
 * (assinatura), e não só pelo Content-Type informado pelo cliente.
 */
public final class ValidadorImagem {

    public static final long TAMANHO_MAXIMO = 5L * 1024 * 1024;
    private static final MimeType WEBP = MimeType.valueOf("image/webp");

    private ValidadorImagem() {}

    public static ImagemRecebida validar(MultipartFile arquivo) {
        if (arquivo == null || arquivo.isEmpty()) {
            throw ApiException.requisicaoInvalida("Envie a foto no campo imagem.");
        }
        if (arquivo.getSize() > TAMANHO_MAXIMO) {
            throw new ApiException(HttpStatus.CONTENT_TOO_LARGE, "A foto passa de 5 MB. Envie uma foto menor.");
        }
        byte[] bytes;
        try {
            bytes = arquivo.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        MimeType tipo = detectar(bytes);
        if (tipo == null) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Formato de foto não suportado. Use JPEG, PNG ou WebP.");
        }
        return new ImagemRecebida(bytes, tipo);
    }

    static MimeType detectar(byte[] b) {
        if (b.length >= 3 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xD8 && (b[2] & 0xFF) == 0xFF) {
            return MimeTypeUtils.IMAGE_JPEG;
        }
        if (b.length >= 8 && (b[0] & 0xFF) == 0x89 && b[1] == 'P' && b[2] == 'N' && b[3] == 'G'
                && b[4] == 0x0D && b[5] == 0x0A && b[6] == 0x1A && b[7] == 0x0A) {
            return MimeTypeUtils.IMAGE_PNG;
        }
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') {
            return WEBP;
        }
        return null;
    }
}
