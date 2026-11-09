package br.com.fiap.veai.descricao;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

import org.springframework.util.MimeTypeUtils;

import br.com.fiap.veai.ia.ImagemRecebida;

/**
 * Reduz a foto antes de enviá-la ao modelo: uma foto de celular (4000 px) vira ~1024 px no lado maior.
 * Medido com o gpt-6-luna: ~5x menos tokens de entrada e resposta mais rápida, sem perder o que importa
 * para descrever o caminho. Tudo em memória: a foto continua sem ser gravada.
 *
 * <p>Celulares costumam gravar a foto "deitada" e indicar a rotação no EXIF (tag Orientation).
 * Como a imagem reduzida sai sem EXIF, a rotação é aplicada aqui, senão o modelo veria a cena de lado.
 *
 * <p>Se não der para decodificar (WebP, JPEG CMYK, arquivo estranho) ou a foto já for pequena,
 * a original segue sem mudança.
 */
public final class RedutorImagem {

    private static final float QUALIDADE_JPEG = 0.85f;

    private RedutorImagem() {}

    public static ImagemRecebida reduzir(ImagemRecebida imagem, int ladoMaximo) {
        boolean jpeg = MimeTypeUtils.IMAGE_JPEG.equals(imagem.tipo());
        if (ladoMaximo <= 0 || !(jpeg || MimeTypeUtils.IMAGE_PNG.equals(imagem.tipo()))) {
            return imagem;
        }
        try {
            BufferedImage original = ImageIO.read(new ByteArrayInputStream(imagem.bytes()));
            if (original == null) {
                return imagem;
            }
            int orientacao = jpeg ? orientacaoExif(imagem.bytes()) : 1;
            int lado = Math.max(original.getWidth(), original.getHeight());
            if (lado <= ladoMaximo && orientacao == 1) {
                return imagem;
            }
            BufferedImage reduzida = desenhar(original, Math.min(1d, (double) ladoMaximo / lado), orientacao);
            byte[] bytes = jpeg(reduzida);
            return bytes.length < imagem.bytes().length || orientacao != 1
                    ? new ImagemRecebida(bytes, MimeTypeUtils.IMAGE_JPEG) : imagem;
        } catch (IOException | RuntimeException e) {
            return imagem;
        }
    }

    /** Redimensiona e aplica a rotação/espelhamento do EXIF (valores 1 a 8). */
    private static BufferedImage desenhar(BufferedImage origem, double escala, int orientacao) {
        int w = Math.max(1, (int) Math.round(origem.getWidth() * escala));
        int h = Math.max(1, (int) Math.round(origem.getHeight() * escala));
        boolean deitada = orientacao >= 5 && orientacao <= 8;
        BufferedImage destino = new BufferedImage(deitada ? h : w, deitada ? w : h, BufferedImage.TYPE_INT_RGB);

        AffineTransform t = new AffineTransform();
        switch (orientacao) {
            case 2 -> { t.translate(w, 0); t.scale(-1, 1); }
            case 3 -> { t.translate(w, h); t.rotate(Math.PI); }
            case 4 -> { t.translate(0, h); t.scale(1, -1); }
            case 5 -> { t.rotate(Math.PI / 2); t.scale(1, -1); }
            case 6 -> { t.translate(h, 0); t.rotate(Math.PI / 2); }
            case 7 -> { t.scale(-1, 1); t.translate(-h, 0); t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            case 8 -> { t.translate(0, w); t.rotate(3 * Math.PI / 2); }
            default -> { }
        }
        t.scale(escala, escala);

        Graphics2D g = destino.createGraphics();
        try {
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, destino.getWidth(), destino.getHeight()); // fundo para PNG com transparência
            g.drawImage(origem, t, null);
        } finally {
            g.dispose();
        }
        return destino;
    }

    private static byte[] jpeg(BufferedImage imagem) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpeg").next();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        try (ImageOutputStream out = ImageIO.createImageOutputStream(saida)) {
            writer.setOutput(out);
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(QUALIDADE_JPEG);
            writer.write(null, new IIOImage(imagem, null, null), param);
        } finally {
            writer.dispose();
        }
        return saida.toByteArray();
    }

    /**
     * Lê a tag Orientation (0x0112) do bloco EXIF (segmento APP1) de um JPEG. Devolve 1 (normal)
     * quando não há EXIF ou ele não é legível.
     */
    static int orientacaoExif(byte[] b) {
        int i = 2; // depois do SOI (FF D8)
        while (i + 4 <= b.length && (b[i] & 0xFF) == 0xFF) {
            int marcador = b[i + 1] & 0xFF;
            int tamanho = ((b[i + 2] & 0xFF) << 8) | (b[i + 3] & 0xFF);
            if (marcador == 0xDA || tamanho < 2) {
                return 1; // início dos dados da imagem: não há EXIF antes
            }
            if (marcador == 0xE1 && i + 10 <= b.length
                    && b[i + 4] == 'E' && b[i + 5] == 'x' && b[i + 6] == 'i' && b[i + 7] == 'f') {
                return orientacaoTiff(b, i + 10, Math.min(b.length, i + 2 + tamanho));
            }
            i += 2 + tamanho;
        }
        return 1;
    }

    private static int orientacaoTiff(byte[] b, int tiff, int fim) {
        if (tiff + 8 > fim) {
            return 1;
        }
        boolean intel = b[tiff] == 'I' && b[tiff + 1] == 'I';
        int ifd = tiff + (int) ler(b, tiff + 4, 4, intel);
        if (ifd + 2 > fim || ifd < tiff) {
            return 1;
        }
        int entradas = (int) ler(b, ifd, 2, intel);
        for (int e = 0; e < entradas; e++) {
            int pos = ifd + 2 + e * 12;
            if (pos + 12 > fim) {
                return 1;
            }
            if (ler(b, pos, 2, intel) == 0x0112) {
                int valor = (int) ler(b, pos + 8, 2, intel);
                return valor >= 1 && valor <= 8 ? valor : 1;
            }
        }
        return 1;
    }

    private static long ler(byte[] b, int pos, int bytes, boolean intel) {
        long v = 0;
        for (int k = 0; k < bytes; k++) {
            int byteAtual = b[intel ? pos + bytes - 1 - k : pos + k] & 0xFF;
            v = (v << 8) | byteAtual;
        }
        return v;
    }
}
