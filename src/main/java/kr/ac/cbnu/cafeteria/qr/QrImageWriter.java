package kr.ac.cbnu.cafeteria.qr;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.client.j2se.MatrixToImageWriter;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Component;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Map;

/**
 * QR 이미지 생성기(EIR-SW-05). qrCode 문자열만 담은 PNG를 만들고 개인 정보는 담지 않는다.
 * 화면에 한 변 200px 이상으로 표시할 수 있도록 기본 크기를 320px로 한다(EIR-HW-01).
 */
@Component
public class QrImageWriter {

    private static final int SIZE = 320;

    public byte[] toPng(String content) {
        try {
            BitMatrix matrix = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, SIZE, SIZE,
                    Map.of(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M, EncodeHintType.MARGIN, 2));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            MatrixToImageWriter.writeToStream(matrix, "PNG", out);
            return out.toByteArray();
        } catch (WriterException e) {
            throw new IllegalStateException("QR 이미지를 만들 수 없습니다", e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
