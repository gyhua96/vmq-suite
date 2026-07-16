package com.vone.mq.service;

import com.vone.mq.dao.PayQrcodeDao;
import com.vone.mq.dto.CommonRes;
import com.vone.mq.entity.PayQrcode;
import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class AdminQrcodeServiceTest {
    private static final String QRCODE_CONTENT_REQUIRED_MESSAGE = "\u8bf7\u4f20\u5165\u4e8c\u7ef4\u7801\u5185\u5bb9";
    private static final String QRCODE_NOT_FOUND_MESSAGE = "\u4e8c\u7ef4\u7801\u4e0d\u5b58\u5728";
    private static final String INVALID_PAY_TYPE_MESSAGE = "\u652f\u4ed8\u65b9\u5f0f\u9519\u8bef=>1|\u5fae\u4fe1 2|\u652f\u4ed8\u5b9d";

    private AdminQrcodeService service;
    private PayQrcodeDao payQrcodeDao;

    @Before
    public void setUp() {
        service = new AdminQrcodeService();
        payQrcodeDao = mock(PayQrcodeDao.class);
        ReflectionTestUtils.setField(service, "payQrcodeDao", payQrcodeDao);
    }

    @Test
    public void addPayQrcodeSavesValidFixedQrcode() {
        PayQrcode payQrcode = qrcode(1, 49.95, "pay-url");

        CommonRes result = service.addPayQrcode(payQrcode);

        assertEquals(1, result.getCode());
        verify(payQrcodeDao).save(payQrcode);
    }

    @Test
    public void addPayQrcodeRejectsMissingQrcodeObject() {
        CommonRes result = service.addPayQrcode(null);

        assertEquals(-1, result.getCode());
        assertEquals(QRCODE_CONTENT_REQUIRED_MESSAGE, result.getMsg());
    }

    @Test
    public void addPayQrcodeRejectsMissingPayUrl() {
        PayQrcode payQrcode = qrcode(1, 49.95, null);

        CommonRes result = service.addPayQrcode(payQrcode);

        assertEquals(-1, result.getCode());
        assertEquals(QRCODE_CONTENT_REQUIRED_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).save(payQrcode);
    }

    @Test
    public void addPayQrcodeRejectsNonPositivePrice() {
        PayQrcode payQrcode = qrcode(1, 0, "pay-url");

        CommonRes result = service.addPayQrcode(payQrcode);

        assertEquals(-1, result.getCode());
        assertEquals("\u8ba2\u5355\u91d1\u989d\u5fc5\u987b\u5927\u4e8e0", result.getMsg());
        verify(payQrcodeDao, never()).save(payQrcode);
    }

    @Test
    public void addPayQrcodeRejectsInvalidType() {
        PayQrcode payQrcode = qrcode(3, 49.95, "pay-url");

        CommonRes result = service.addPayQrcode(payQrcode);

        assertEquals(-1, result.getCode());
        assertEquals(INVALID_PAY_TYPE_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).save(payQrcode);
    }

    @Test
    public void deletePayQrcodeDeletesById() {
        when(payQrcodeDao.existsById(99L)).thenReturn(true);

        CommonRes result = service.deletePayQrcode(99L);

        assertEquals(1, result.getCode());
        verify(payQrcodeDao).deleteById(99L);
    }

    @Test
    public void deletePayQrcodeReturnsBusinessErrorWhenMissing() {
        when(payQrcodeDao.existsById(99L)).thenReturn(false);

        CommonRes result = service.deletePayQrcode(99L);

        assertEquals(-1, result.getCode());
        assertEquals(QRCODE_NOT_FOUND_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).deleteById(99L);
    }

    @Test
    public void deletePayQrcodeReturnsBusinessErrorWhenIdIsNull() {
        CommonRes result = service.deletePayQrcode(null);

        assertEquals(-1, result.getCode());
        assertEquals(QRCODE_NOT_FOUND_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).deleteById(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    public void updatePayQrcodeSavesExistingQrcode() {
        PayQrcode payQrcode = qrcode(2, 19.90, "new-pay-url");
        payQrcode.setId(99L);
        when(payQrcodeDao.existsById(99L)).thenReturn(true);

        CommonRes result = service.updatePayQrcode(payQrcode);

        assertEquals(1, result.getCode());
        verify(payQrcodeDao).save(payQrcode);
    }

    @Test
    public void updatePayQrcodeRejectsMissingId() {
        PayQrcode payQrcode = qrcode(1, 19.90, "pay-url");

        CommonRes result = service.updatePayQrcode(payQrcode);

        assertEquals(-1, result.getCode());
        assertEquals(QRCODE_NOT_FOUND_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).save(payQrcode);
    }

    @Test
    public void updatePayQrcodeRejectsMissingEntity() {
        PayQrcode payQrcode = qrcode(1, 19.90, "pay-url");
        payQrcode.setId(99L);
        when(payQrcodeDao.existsById(99L)).thenReturn(false);

        CommonRes result = service.updatePayQrcode(payQrcode);

        assertEquals(-1, result.getCode());
        assertEquals(QRCODE_NOT_FOUND_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).save(payQrcode);
    }

    @Test
    public void updatePayQrcodeRejectsInvalidPayload() {
        PayQrcode payQrcode = qrcode(3, 19.90, "pay-url");
        payQrcode.setId(99L);
        when(payQrcodeDao.existsById(99L)).thenReturn(true);

        CommonRes result = service.updatePayQrcode(payQrcode);

        assertEquals(-1, result.getCode());
        assertEquals(INVALID_PAY_TYPE_MESSAGE, result.getMsg());
        verify(payQrcodeDao, never()).save(payQrcode);
    }

    @Test
    public void getPayQrcodesDefaultsInvalidPagingParameters() {
        when(payQrcodeDao.findAll(isA(Specification.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<PayQrcode>(Collections.emptyList()));

        service.getPayQrcodes(null, 0, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(payQrcodeDao).findAll(isA(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(0, pageable.getPageNumber());
        assertEquals(10, pageable.getPageSize());
        assertEquals("id: DESC", pageable.getSort().toString());
    }

    @Test
    public void getPayQrcodesKeepsValidPagingParameters() {
        when(payQrcodeDao.findAll(isA(Specification.class), org.mockito.ArgumentMatchers.any(Pageable.class)))
                .thenReturn(new PageImpl<PayQrcode>(Collections.emptyList()));

        service.getPayQrcodes(2, 30, null);

        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(payQrcodeDao).findAll(isA(Specification.class), captor.capture());
        Pageable pageable = captor.getValue();
        assertEquals(1, pageable.getPageNumber());
        assertEquals(30, pageable.getPageSize());
        assertEquals("id: DESC", pageable.getSort().toString());
    }

    private PayQrcode qrcode(int type, double price, String payUrl) {
        PayQrcode payQrcode = new PayQrcode();
        payQrcode.setType(type);
        payQrcode.setPrice(price);
        payQrcode.setPayUrl(payUrl);
        return payQrcode;
    }
}
