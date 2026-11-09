package br.com.fiap.veai.descricao;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import javax.imageio.ImageIO;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.util.MimeTypeUtils;

import br.com.fiap.veai.config.VeAiProperties;
import br.com.fiap.veai.descricao.RegrasDescricaoTest.RelogioAjustavel;
import br.com.fiap.veai.ia.ImagemRecebida;
import br.com.fiap.veai.relato.TipoRelato;

/** Regras do modo automático que não precisam de banco: sugestão repetida e redução da foto. */
class ModoAutomaticoTest {

    @Test
    @DisplayName("modo automático não repete a mesma sugestão por 2 minutos; o manual sempre sugere")
    void sugestaoRepetida() {
        var relogio = new RelogioAjustavel(Instant.parse("2026-11-08T12:00:00Z"));
        var props = new VeAiProperties(null, null,
                new VeAiProperties.Continuo(Duration.ofMinutes(2), Duration.ofMinutes(2)), null, null, null, null);
        var memoria = new MemoriaSugestoes(props, relogio);

        assertThat(memoria.deveSugerir(1L, TipoRelato.OBSTRUCAO_PERMANENTE, true)).isTrue();
        relogio.avancar(Duration.ofSeconds(10));
        assertThat(memoria.deveSugerir(1L, TipoRelato.OBSTRUCAO_PERMANENTE, true)).isFalse();
        assertThat(memoria.deveSugerir(1L, TipoRelato.CALCADA_DANIFICADA, true)).as("outro tipo").isTrue();
        assertThat(memoria.deveSugerir(2L, TipoRelato.OBSTRUCAO_PERMANENTE, true)).as("outra pessoa").isTrue();
        assertThat(memoria.deveSugerir(1L, TipoRelato.OBSTRUCAO_PERMANENTE, false)).as("modo manual").isTrue();

        relogio.avancar(Duration.ofSeconds(119)); // a manual de 10 s atrás reiniciou a janela
        assertThat(memoria.deveSugerir(1L, TipoRelato.OBSTRUCAO_PERMANENTE, true)).isFalse();
        relogio.avancar(Duration.ofSeconds(2));
        assertThat(memoria.deveSugerir(1L, TipoRelato.OBSTRUCAO_PERMANENTE, true)).isTrue();
    }

    @Test
    @DisplayName("a descrição anterior só vale no modo AMBIENTE e por até 2 minutos")
    void validadeDoContexto() {
        Instant falada = Instant.parse("2026-11-08T12:00:00Z");
        Duration validade = Duration.ofMinutes(2);
        var anterior = new Descricao(null, ModoDescricao.AMBIENTE, "Poste.", "Poste bem à sua frente.", List.of(), List.of(),
                false, null, 0, falada);

        var contexto = DescricaoService.contexto(anterior, ModoDescricao.AMBIENTE, falada.plusSeconds(6), validade);
        assertThat(contexto.descricaoCompleta()).isEqualTo("Poste bem à sua frente.");
        assertThat(contexto.segundosAtras()).isEqualTo(6);

        assertThat(DescricaoService.contexto(anterior, ModoDescricao.AMBIENTE, falada.plusSeconds(121), validade)).isNull();
        assertThat(DescricaoService.contexto(anterior, ModoDescricao.TEXTO, falada.plusSeconds(6), validade)).isNull();
        assertThat(DescricaoService.contexto(null, ModoDescricao.AMBIENTE, falada, validade)).isNull();
        var texto = new Descricao(null, ModoDescricao.TEXTO, "Placa.", "Farmácia.", List.of(), List.of(), false, null, 0, falada);
        assertThat(DescricaoService.contexto(texto, ModoDescricao.AMBIENTE, falada.plusSeconds(6), validade)).isNull();
    }

    @Test
    @DisplayName("foto grande vira JPEG de até 1024 px; foto pequena e arquivo não decodificável seguem iguais")
    void reduzFoto() throws IOException {
        var grande = new ImagemRecebida(jpeg(3000, 2000, null), MimeTypeUtils.IMAGE_JPEG);
        ImagemRecebida reduzida = RedutorImagem.reduzir(grande, 1024);
        BufferedImage img = ler(reduzida);
        assertThat(img.getWidth()).isEqualTo(1024);
        assertThat(img.getHeight()).isEqualTo(683);
        assertThat(reduzida.bytes().length).isLessThan(grande.bytes().length);
        assertThat(reduzida.tipo()).isEqualTo(MimeTypeUtils.IMAGE_JPEG);

        var pequena = new ImagemRecebida(jpeg(800, 600, null), MimeTypeUtils.IMAGE_JPEG);
        assertThat(RedutorImagem.reduzir(pequena, 1024)).isSameAs(pequena);

        var invalida = new ImagemRecebida(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 1, 2, 3}, MimeTypeUtils.IMAGE_JPEG);
        assertThat(RedutorImagem.reduzir(invalida, 1024)).isSameAs(invalida);
    }

    @Test
    @DisplayName("foto de celular em pé (EXIF Orientation = 6) é girada antes de ir para a IA")
    void aplicaOrientacaoExif() throws IOException {
        // Sensor grava deitado (2000 x 1000): metade esquerda vermelha, direita azul. Orientation 6 = girar 90° horário.
        byte[] bytes = jpeg(2000, 1000, 6);
        assertThat(RedutorImagem.orientacaoExif(bytes)).isEqualTo(6);

        BufferedImage img = ler(RedutorImagem.reduzir(new ImagemRecebida(bytes, MimeTypeUtils.IMAGE_JPEG), 1024));
        assertThat(img.getWidth()).isEqualTo(512);
        assertThat(img.getHeight()).isEqualTo(1024);
        // Girando 90° no sentido horário, o lado esquerdo vira o topo
        assertThat(vermelho(img.getRGB(256, 100))).isTrue();
        assertThat(vermelho(img.getRGB(256, 900))).isFalse();

        assertThat(RedutorImagem.orientacaoExif(jpeg(10, 10, null))).isEqualTo(1);
    }

    private static boolean vermelho(int rgb) {
        Color c = new Color(rgb);
        return c.getRed() > 200 && c.getBlue() < 60;
    }

    private static BufferedImage ler(ImagemRecebida imagem) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(imagem.bytes()));
    }

    /** JPEG com metade esquerda vermelha e direita azul; com orientacao != null, inclui o bloco EXIF. */
    private static byte[] jpeg(int w, int h, Integer orientacao) throws IOException {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setColor(Color.RED);
        g.fillRect(0, 0, w / 2, h);
        g.setColor(Color.BLUE);
        g.fillRect(w / 2, 0, w - w / 2, h);
        if (orientacao == null) {
            // Ruído para o JPEG ter tamanho realista (e a versão reduzida ficar menor)
            java.util.Random r = new java.util.Random(42);
            for (int i = 0; i < w * h / 20; i++) {
                img.setRGB(r.nextInt(w), r.nextInt(h), r.nextInt());
            }
        }
        g.dispose();
        ByteArrayOutputStream saida = new ByteArrayOutputStream();
        ImageIO.write(img, "jpeg", saida);
        byte[] b = saida.toByteArray();
        if (orientacao == null) {
            return b;
        }
        // APP1 "Exif\0\0" + TIFF big-endian com uma entrada: Orientation (0x0112), SHORT, 1 valor
        byte[] tiff = {'M', 'M', 0, 42, 0, 0, 0, 8, 0, 1, 0x01, 0x12, 0, 3, 0, 0, 0, 1, 0, orientacao.byteValue(), 0, 0,
                0, 0, 0, 0};
        byte[] exif = new byte[6 + tiff.length];
        System.arraycopy("Exif\0\0".getBytes(), 0, exif, 0, 6);
        System.arraycopy(tiff, 0, exif, 6, tiff.length);
        int tamanho = exif.length + 2;
        ByteArrayOutputStream com = new ByteArrayOutputStream();
        com.write(b, 0, 2); // SOI
        com.write(new byte[]{(byte) 0xFF, (byte) 0xE1, (byte) (tamanho >> 8), (byte) tamanho});
        com.write(exif);
        com.write(b, 2, b.length - 2);
        return com.toByteArray();
    }
}
