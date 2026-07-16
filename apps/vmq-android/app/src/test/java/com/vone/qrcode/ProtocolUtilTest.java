package com.vone.qrcode;

import com.vone.vmq.ProtocolUtil;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ProtocolUtilTest {
    @Test
    public void appPushUrlKeepsLegacyMd5SignatureContract() {
        String timestamp = "1782870000000";
        String expectedSign = ProtocolUtil.md5("2" + "49.95" + timestamp + "secret");

        String url = ProtocolUtil.appPushUrl("example.com:8080", 2, "49.95", timestamp, "secret");

        assertEquals("https://example.com:8080/appPush?t=1782870000000&type=2&price=49.95&sign=" + expectedSign, url);
    }

    @Test
    public void appPushUrlKeepsExplicitHttpsHost() {
        String timestamp = "1782870000000";
        String expectedSign = ProtocolUtil.md5("1" + "1.0" + timestamp + "secret");

        String url = ProtocolUtil.appPushUrl("https://pay.example.com", 1, "1.0", timestamp, "secret");

        assertEquals("https://pay.example.com/appPush?t=1782870000000&type=1&price=1.0&sign=" + expectedSign, url);
    }

    @Test
    public void appPushUrlPreservesOriginalMoneyScaleForSignatureCompatibility() {
        String timestamp = "1782870000000";
        String expectedSign = ProtocolUtil.md5("1" + "1.00" + timestamp + "secret");

        String url = ProtocolUtil.appPushUrl("https://pay.example.com", 1, "1.00", timestamp, "secret");

        assertEquals("https://pay.example.com/appPush?t=1782870000000&type=1&price=1.00&sign=" + expectedSign, url);
    }

    @Test
    public void parseConfigSupportsLegacyHostAndKey() {
        ProtocolUtil.Config config = ProtocolUtil.parseConfig("example.com:8080/secret");

        assertEquals("example.com:8080", config.host);
        assertEquals("secret", config.key);
    }

    @Test
    public void parseConfigSupportsLegacyHostWithProtocol() {
        ProtocolUtil.Config config = ProtocolUtil.parseConfig("https://pay.example.com/secret");

        assertEquals("https://pay.example.com", config.host);
        assertEquals("secret", config.key);
    }

    @Test
    public void parseConfigSupportsVmqScheme() {
        ProtocolUtil.Config config = ProtocolUtil.parseConfig("vmq://config?url=https%3A%2F%2Fpay.example.com&key=secret%2Fwith%2Fslash");

        assertEquals("https://pay.example.com", config.host);
        assertEquals("secret/with/slash", config.key);
    }
}
