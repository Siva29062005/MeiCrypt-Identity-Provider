package com.meicrypt.identity.mfa.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.util.Base64;
import java.util.EnumMap;
import java.util.Map;

/**
 * Small helper that renders any string (in Phase 9 that is always an
 * {@code otpauth://} URI) into a PNG QR code, returned as base64 for direct
 * embedding into JSON responses / <img src="data:image/png;base64,…"/> tags.
 */
@Component
public class QrCodeService {

    public String generatePngBase64(String content, int size) {
        try {
            QRCodeWriter writer = new QRCodeWriter();
            Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
            hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
            hints.put(EncodeHintType.MARGIN, 1);
            BitMatrix matrix = writer.encode(content, BarcodeFormat.QR_CODE, size, size, hints);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", baos);
            return Base64.getEncoder().encodeToString(baos.toByteArray());
        } catch (WriterException | java.io.IOException ex) {
            throw new IllegalStateException("Failed to generate QR code", ex);
        }
    }
}
