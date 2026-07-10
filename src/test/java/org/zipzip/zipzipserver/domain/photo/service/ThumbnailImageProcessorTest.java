package org.zipzip.zipzipserver.domain.photo.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ThumbnailImageProcessorTest {

    private final ThumbnailImageProcessor processor = new ThumbnailImageProcessor();

    @Test
    void 긴_변이_목표_크기보다_크면_비율을_유지한_채_축소한다() throws Exception {
        byte[] original = jpegOf(1600, 800);

        byte[] thumbnail = processor.createThumbnail(original, 400);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(Math.max(decoded.getWidth(), decoded.getHeight())).isEqualTo(400);
        assertThat(decoded.getWidth()).isEqualTo(400);
        assertThat(decoded.getHeight()).isEqualTo(200);
    }

    @Test
    void 이미_목표_크기보다_작으면_원본_크기를_유지한다() throws Exception {
        byte[] original = jpegOf(100, 80);

        byte[] thumbnail = processor.createThumbnail(original, 400);

        BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(thumbnail));
        assertThat(decoded.getWidth()).isEqualTo(100);
        assertThat(decoded.getHeight()).isEqualTo(80);
    }

    @Test
    void 결과물은_유효한_JPEG로_디코딩된다() throws Exception {
        byte[] original = jpegOf(640, 480);

        byte[] thumbnail = processor.createThumbnail(original);

        assertThat(ImageIO.read(new ByteArrayInputStream(thumbnail))).isNotNull();
    }

    private byte[] jpegOf(int width, int height) throws Exception {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setColor(Color.BLUE);
        graphics.fillRect(0, 0, width, height);
        graphics.dispose();

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ImageIO.write(image, "jpg", output);
        return output.toByteArray();
    }
}
