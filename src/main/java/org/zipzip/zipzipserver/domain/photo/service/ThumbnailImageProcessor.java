package org.zipzip.zipzipserver.domain.photo.service;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.AffineTransformOp;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import org.springframework.stereotype.Component;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/** 원본 JPEG 바이트를 EXIF orientation 반영해 디코딩하고, 지정한 긴 변 길이로 리사이즈해 JPEG로 재인코딩한다. */
@Component
public class ThumbnailImageProcessor {

    private static final int DEFAULT_THUMBNAIL_LONG_EDGE = 480;
    private static final float JPEG_QUALITY = 0.85f;

    public byte[] createThumbnail(byte[] originalBytes) throws IOException {
        return createThumbnail(originalBytes, DEFAULT_THUMBNAIL_LONG_EDGE);
    }

    public byte[] createThumbnail(byte[] originalBytes, int longEdge) throws IOException {
        BufferedImage decoded = decode(originalBytes);
        int orientation = readOrientation(originalBytes);
        BufferedImage oriented = applyOrientation(decoded, orientation);
        BufferedImage resized = resize(oriented, longEdge);
        return encodeJpeg(resized);
    }

    private BufferedImage decode(byte[] bytes) throws IOException {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IOException("이미지를 디코딩할 수 없습니다.");
        }
        return image;
    }

    private int readOrientation(byte[] bytes) {
        try (ImageInputStream input =
                ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) {
                return 1;
            }
            var reader = readers.next();
            reader.setInput(input);
            IIOMetadata metadata = reader.getImageMetadata(0);
            if (metadata == null) {
                return 1;
            }
            return extractOrientation(metadata);
        } catch (Exception exception) {
            return 1;
        }
    }

    private int extractOrientation(IIOMetadata metadata) {
        for (String formatName : metadata.getMetadataFormatNames()) {
            Node root = metadata.getAsTree(formatName);
            Integer orientation = findOrientationTag(root);
            if (orientation != null) {
                return orientation;
            }
        }
        return 1;
    }

    private Integer findOrientationTag(Node node) {
        if (node.getNodeName().equalsIgnoreCase("unknown")
                && node.getAttributes() != null
                && node.getAttributes().getNamedItem("MarkerTag") != null
                && "274".equals(node.getAttributes().getNamedItem("MarkerTag").getNodeValue())) {
            Node value = node.getAttributes().getNamedItem("value");
            if (value != null) {
                try {
                    return Integer.parseInt(value.getNodeValue());
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Integer found = findOrientationTag(children.item(i));
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    private BufferedImage applyOrientation(BufferedImage image, int orientation) {
        int width = image.getWidth();
        int height = image.getHeight();
        AffineTransform transform = new AffineTransform();

        switch (orientation) {
            case 2 -> transform.concatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
            case 3 ->
                    transform.concatenate(
                            AffineTransform.getRotateInstance(Math.PI, width / 2.0, height / 2.0));
            case 4 -> {
                transform.concatenate(AffineTransform.getScaleInstance(1.0, -1.0));
                transform.concatenate(AffineTransform.getTranslateInstance(0, -height));
            }
            case 5 -> {
                transform.concatenate(AffineTransform.getRotateInstance(Math.PI / 2));
                transform.concatenate(AffineTransform.getScaleInstance(1.0, -1.0));
            }
            case 6 -> {
                transform.concatenate(AffineTransform.getTranslateInstance(height, 0));
                transform.concatenate(AffineTransform.getRotateInstance(Math.PI / 2));
            }
            case 7 -> {
                transform.concatenate(AffineTransform.getTranslateInstance(height, width));
                transform.concatenate(AffineTransform.getRotateInstance(Math.PI * 3 / 2));
                transform.concatenate(AffineTransform.getScaleInstance(-1.0, 1.0));
            }
            case 8 -> {
                transform.concatenate(AffineTransform.getTranslateInstance(0, width));
                transform.concatenate(AffineTransform.getRotateInstance(Math.PI * 3 / 2));
            }
            default -> {
                return image;
            }
        }

        boolean swapDimensions = orientation >= 5;
        BufferedImage rotated =
                new BufferedImage(
                        swapDimensions ? height : width,
                        swapDimensions ? width : height,
                        image.getType() == 0 ? BufferedImage.TYPE_INT_RGB : image.getType());
        Graphics2D graphics = rotated.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        new AffineTransformOp(transform, AffineTransformOp.TYPE_BILINEAR).filter(image, rotated);
        graphics.dispose();
        return rotated;
    }

    private BufferedImage resize(BufferedImage image, int longEdge) {
        int width = image.getWidth();
        int height = image.getHeight();
        if (Math.max(width, height) <= longEdge) {
            return image;
        }

        double scale = (double) longEdge / Math.max(width, height);
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));

        BufferedImage resized =
                new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = resized.createGraphics();
        graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(
                RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return resized;
    }

    private byte[] encodeJpeg(BufferedImage image) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        ImageWriteParam params = writer.getDefaultWriteParam();
        params.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
        params.setCompressionQuality(JPEG_QUALITY);

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        try (MemoryCacheImageOutputStream imageOutputStream =
                new MemoryCacheImageOutputStream(output)) {
            writer.setOutput(imageOutputStream);
            writer.write(null, new IIOImage(image, null, null), params);
        } finally {
            writer.dispose();
        }
        return output.toByteArray();
    }
}
