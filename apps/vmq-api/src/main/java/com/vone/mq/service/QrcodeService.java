package com.vone.mq.service;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.EncodeHintType;
import com.google.zxing.MultiFormatReader;
import com.google.zxing.Result;
import com.google.zxing.client.j2se.BufferedImageLuminanceSource;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.qrcode.QRCodeWriter;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.OutputStream;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@Service
public class QrcodeService {
    public static final int ENCODE_SIZE = 200;
    public static final int MAX_BASE64_CHARS = 3 * 1024 * 1024;
    public static final long MAX_FILE_BYTES = 2L * 1024 * 1024;
    public static final int MAX_ENCODE_CHARS = 8192;

    public void writePng(String content, OutputStream outputStream) throws Exception {
        BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, ENCODE_SIZE, ENCODE_SIZE);
        MatrixToImageWriter.writeToStream(matrix, "png", outputStream);
    }

    public String decodeBase64(String base64) throws Exception {
        byte[] bytes = Base64.getDecoder().decode(base64);
        return decode(bytes);
    }

    public String decode(byte[] bytes) throws Exception {
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
        if (image == null) {
            throw new IllegalArgumentException("invalid qrcode image");
        }
        Map hints = new HashMap();
        hints.put(EncodeHintType.CHARACTER_SET, "utf-8");
        BinaryBitmap binaryBitmap = new BinaryBitmap(new HybridBinarizer(new BufferedImageLuminanceSource(image)));
        Result result = new MultiFormatReader().decode(binaryBitmap, hints);
        return result.getText();
    }
}
