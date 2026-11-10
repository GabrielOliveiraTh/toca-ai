package com.mixtape.infrastructure.qr;

import com.mixtape.application.port.QrCodes;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class QrService implements QrCodes {
    private static final int MARGIN = 2;

    @Override
    public String svg(String text) {
        BitMatrix matrix = encode(text);
        int width = matrix.getWidth();
        int height = matrix.getHeight();
        StringBuilder path = new StringBuilder();
        for (int y = 0; y < height; y++) {
            int x = 0;
            while (x < width) {
                if (!matrix.get(x, y)) {
                    x++;
                    continue;
                }
                int start = x;
                while (x < width && matrix.get(x, y)) {
                    x++;
                }
                int run = x - start;
                path.append('M').append(start).append(' ').append(y)
                        .append('h').append(run).append("v1h-").append(run).append('z');
            }
        }
        return "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 " + width + " " + height
                + "\" shape-rendering=\"crispEdges\" role=\"img\" aria-label=\"QR code\">"
                + "<rect width=\"" + width + "\" height=\"" + height + "\" fill=\"#ffffff\"/>"
                + "<path d=\"" + path + "\" fill=\"#000000\"/></svg>";
    }

    private static BitMatrix encode(String text) {
        try {
            return new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, 0, 0, Map.of(
                    EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M,
                    EncodeHintType.MARGIN, MARGIN,
                    EncodeHintType.CHARACTER_SET, "UTF-8"));
        } catch (WriterException e) {
            throw new IllegalArgumentException("Could not generate the QR code for this text.");
        }
    }
}
