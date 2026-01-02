package com.example.dacs4.network;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;

public final class JpegUtils {

    private JpegUtils() {
    }

    /**
     * Encode BufferedImage to JPEG bytes with specified quality.
     * 
     * @param image   The image to encode
     * @param quality Quality from 0.1f to 1.0f (recommended: 0.5f - 0.7f)
     * @return JPEG bytes
     * @throws IOException If encoding fails
     */
    public static byte[] encodeJpeg(BufferedImage image, float quality) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream(64 * 1024);

        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) {
            throw new IOException("No JPEG writer found");
        }
        ImageWriter writer = writers.next();

        ImageWriteParam param = writer.getDefaultWriteParam();
        if (param.canWriteCompressed()) {
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(clamp(quality));
        }

        try (ImageOutputStream ios = ImageIO.createImageOutputStream(baos)) {
            writer.setOutput(ios);
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
        return baos.toByteArray();
    }

    private static float clamp(float q) {
        if (q < 0.05f)
            return 0.05f;
        if (q > 1.0f)
            return 1.0f;
        return q;
    }
}
