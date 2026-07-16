package com.vone.mq.service;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.util.Base64;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class QrcodeServiceTest {
    private final QrcodeService qrcodeService = new QrcodeService();

    @Test
    public void writesAndDecodesPngQrcode() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        qrcodeService.writePng("vmq://pay/test", outputStream);

        byte[] bytes = outputStream.toByteArray();
        assertTrue(bytes.length > 0);
        assertEquals("vmq://pay/test", qrcodeService.decode(bytes));
    }

    @Test
    public void decodesBase64Qrcode() throws Exception {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        qrcodeService.writePng("https://example.com/pay?id=1", outputStream);
        String base64 = Base64.getEncoder().encodeToString(outputStream.toByteArray());

        assertEquals("https://example.com/pay?id=1", qrcodeService.decodeBase64(base64));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsNonImageBytes() throws Exception {
        qrcodeService.decode("not-an-image".getBytes("UTF-8"));
    }
}
