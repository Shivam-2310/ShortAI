package com.urlshortener.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageConfig;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

/**
 * Service for generating QR codes for short URLs.
 * Supports customization of size, colors, and format.
 */
@Service
public class QrCodeService {

    private static final Logger log = LoggerFactory.getLogger(QrCodeService.class);

    private final String baseUrl;
    private final int defaultSize;

    public QrCodeService(
            @Value("${app.base-url:http://localhost:8080}") String baseUrl,
            @Value("${qrcode.default-size:300}") int defaultSize) {
        this.baseUrl = baseUrl;
        this.defaultSize = defaultSize;
    }

    /**
     * Generate a QR code for a short URL.
     *
     * @param shortKey The short key
     * @return Base64 encoded PNG image
     */
    public String generateQrCode(String shortKey) {
        return generateQrCode(shortKey, defaultSize, "#000000", "#FFFFFF");
    }

    /**
     * Generate a QR code with custom size.
     *
     * @param shortKey The short key
     * @param size     Size in pixels (width and height)
     * @return Base64 encoded PNG image
     */
    public String generateQrCode(String shortKey, int size) {
        return generateQrCode(shortKey, size, "#000000", "#FFFFFF");
    }

    /**
     * Generate a QR code with custom size and colors.
     *
     * @param shortKey        The short key
     * @param size            Size in pixels (width and height)
     * @param foregroundColor Foreground color in hex (e.g., "#000000")
     * @param backgroundColor Background color in hex (e.g., "#FFFFFF")
     * @return Base64 encoded PNG image
     */
    public String generateQrCode(String shortKey, int size, String foregroundColor, String backgroundColor) {
        String url = baseUrl + "/" + shortKey;
        return generateQrCodeForUrl(url, size, foregroundColor, backgroundColor);
    }

    /**
     * Generate a QR code for any URL.
     */
    public String generateQrCodeForUrl(String url, int size, String foregroundColor, String backgroundColor) {
        log.debug("Generating QR code for URL: {} with size: {}", url, size);

        try {
            // Validate and normalize size
            int normalizedSize = Math.max(100, Math.min(size, 1000));

            // Configure QR code writer
            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, normalizedSize, normalizedSize, hints);

            // Parse colors
            int fgColor = parseColor(foregroundColor, 0xFF000000);
            int bgColor = parseColor(backgroundColor, 0xFFFFFFFF);

            // Convert to image
            MatrixToImageConfig config = new MatrixToImageConfig(fgColor, bgColor);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix, config);

            // Convert to Base64
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            byte[] imageBytes = baos.toByteArray();

            return Base64.getEncoder().encodeToString(imageBytes);

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code for URL: {}", url, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Generate a QR code as raw bytes (for direct file download).
     */
    public byte[] generateQrCodeBytes(String shortKey, int size, String foregroundColor, String backgroundColor) {
        String url = baseUrl + "/" + shortKey;

        try {
            int normalizedSize = Math.max(100, Math.min(size, 1000));

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, normalizedSize, normalizedSize, hints);

            int fgColor = parseColor(foregroundColor, 0xFF000000);
            int bgColor = parseColor(backgroundColor, 0xFFFFFFFF);

            MatrixToImageConfig config = new MatrixToImageConfig(fgColor, bgColor);
            BufferedImage image = MatrixToImageWriter.toBufferedImage(bitMatrix, config);

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(image, "PNG", baos);
            return baos.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code bytes for key: {}", shortKey, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    /**
     * Generate a QR code with a logo in the center.
     */
    public byte[] generateQrCodeWithLogo(String shortKey, int size, byte[] logoBytes) {
        String url = baseUrl + "/" + shortKey;

        try {
            int normalizedSize = Math.max(200, Math.min(size, 1000));

            Map<EncodeHintType, Object> hints = new HashMap<>();
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.H);
            hints.put(EncodeHintType.CHARACTER_SET, "UTF-8");
            hints.put(EncodeHintType.MARGIN, 2);

            QRCodeWriter qrCodeWriter = new QRCodeWriter();
            BitMatrix bitMatrix = qrCodeWriter.encode(url, BarcodeFormat.QR_CODE, normalizedSize, normalizedSize, hints);

            BufferedImage qrImage = MatrixToImageWriter.toBufferedImage(bitMatrix);

            // Add logo if provided
            if (logoBytes != null && logoBytes.length > 0) {
                BufferedImage logo = ImageIO.read(new java.io.ByteArrayInputStream(logoBytes));
                int logoSize = normalizedSize / 5;
                int logoX = (normalizedSize - logoSize) / 2;
                int logoY = (normalizedSize - logoSize) / 2;

                Graphics2D g = qrImage.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                // Draw white background for logo
                g.setColor(Color.WHITE);
                g.fillRect(logoX - 5, logoY - 5, logoSize + 10, logoSize + 10);

                // Draw logo
                g.drawImage(logo, logoX, logoY, logoSize, logoSize, null);
                g.dispose();
            }

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            ImageIO.write(qrImage, "PNG", baos);
            return baos.toByteArray();

        } catch (WriterException | IOException e) {
            log.error("Error generating QR code with logo for key: {}", shortKey, e);
            throw new RuntimeException("Failed to generate QR code", e);
        }
    }

    private int parseColor(String hexColor, int defaultColor) {
        if (hexColor == null || hexColor.isEmpty()) {
            return defaultColor;
        }
        try {
            String hex = hexColor.startsWith("#") ? hexColor.substring(1) : hexColor;
            return 0xFF000000 | Integer.parseInt(hex, 16);
        } catch (NumberFormatException e) {
            log.warn("Invalid color format: {}, using default", hexColor);
            return defaultColor;
        }
    }
}

