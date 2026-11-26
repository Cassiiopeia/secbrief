package kr.suhsaechan.secbrief.advisory.image;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Iterator;
import java.util.Locale;
import java.util.Set;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageInputStream;
import javax.imageio.stream.ImageOutputStream;
import javax.imageio.stream.MemoryCacheImageInputStream;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.springframework.stereotype.Component;

/**
 * 기사 대표 이미지 줄이기: 디코드(JPEG·PNG·GIF·WebP) > 긴 변 800px 이하로 축소(확대 안 함) > 흰 배경 JPEG 로 다시 인코딩
 * 다시 인코딩하는 이유 2개: 용량 절감, 원본 파일에 든 메타데이터·이상 데이터 제거 (픽셀만 옮김)
 */
@Component
public class ArticleImageResizer {

    // 저장본의 긴 변 최대 크기 (픽셀)
    public static final int MAXIMUM_LONG_SIDE_PIXELS = 800;
    // 원본 픽셀 수 상한 (4000만): 작은 파일이 거대한 그림으로 풀리는 디코드 폭탄 방지, 헤더만 읽고 판정
    static final long MAXIMUM_SOURCE_PIXEL_COUNT = 40_000_000L;
    // 원본 긴 변 최소 크기 (픽셀): 추적용 1x1 같은 그림은 대표 이미지로 쓰지 않음
    static final int MINIMUM_LONG_SIDE_PIXELS = 100;
    private static final float JPEG_QUALITY = 0.82f;
    private static final String JPEG_FORMAT_NAME = "jpeg";
    // ImageReader.getFormatName 값 (소문자): 그 밖의 형식(BMP·TIFF 등)은 받지 않음
    private static final Set<String> SUPPORTED_FORMAT_NAMES = Set.of("jpeg", "jpg", "png", "gif", "webp");

    public ArticleImageResizer() {
        // WebP 플러그인 등록 (클래스 로더가 여럿인 실행 jar 에서도 찾게 다시 훑음)
        ImageIO.scanForPlugins();
    }

    /**
     * 원본 바이트 > 줄인 JPEG 바이트: 디코드 > 축소 + 흰 배경 > JPEG 인코딩
     */
    public byte[] toResizedJpeg(byte[] originalImageBytes) {
        BufferedImage decodedImage = decode(originalImageBytes);
        return encodeJpeg(drawResizedOnWhite(decodedImage));
    }

    // 형식 확인 > 헤더의 크기 확인 > 디코드 (큰 그림은 건너뛰며 읽어 메모리 절약)
    private static BufferedImage decode(byte[] originalImageBytes) {
        try (ImageInputStream imageInputStream =
                     new MemoryCacheImageInputStream(new ByteArrayInputStream(originalImageBytes))) {
            Iterator<ImageReader> imageReaders = ImageIO.getImageReaders(imageInputStream);
            if (!imageReaders.hasNext()) {
                throw rejected("unsupported image format");
            }
            ImageReader imageReader = imageReaders.next();
            try {
                return decodeWith(imageReader, imageInputStream);
            } finally {
                imageReader.dispose();
            }
        } catch (CustomException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            // 깨진 파일은 리더가 여러 종류의 예외를 던짐
            throw rejected("decode failed : " + exception.getClass().getSimpleName());
        }
    }

    private static BufferedImage decodeWith(ImageReader imageReader, ImageInputStream imageInputStream) throws IOException {
        if (!SUPPORTED_FORMAT_NAMES.contains(imageReader.getFormatName().toLowerCase(Locale.ROOT))) {
            throw rejected("unsupported image format");
        }
        // 메타데이터는 읽지 않음, 첫 장만 (움직이는 GIF 는 첫 장면)
        imageReader.setInput(imageInputStream, true, true);
        int sourceWidth = imageReader.getWidth(0);
        int sourceHeight = imageReader.getHeight(0);
        int sourceLongSide = Math.max(sourceWidth, sourceHeight);
        if (sourceWidth <= 0 || sourceHeight <= 0 || (long) sourceWidth * sourceHeight > MAXIMUM_SOURCE_PIXEL_COUNT) {
            throw rejected("too many pixels");
        }
        if (sourceLongSide < MINIMUM_LONG_SIDE_PIXELS) {
            throw rejected("too small");
        }

        // 목표의 2배 크기까지만 건너뛰며 읽음 (나머지 축소는 그릴 때 보간)
        ImageReadParam readParam = imageReader.getDefaultReadParam();
        int subsamplingStep = Math.max(1, sourceLongSide / (MAXIMUM_LONG_SIDE_PIXELS * 2));
        readParam.setSourceSubsampling(subsamplingStep, subsamplingStep, 0, 0);
        BufferedImage decodedImage = imageReader.read(0, readParam);
        if (decodedImage == null) {
            throw rejected("decode failed : empty image");
        }
        return decodedImage;
    }

    // 긴 변 800px 이하로 비율 유지 축소 (작은 그림은 그대로), 투명한 곳은 흰색
    private static BufferedImage drawResizedOnWhite(BufferedImage sourceImage) {
        int sourceLongSide = Math.max(sourceImage.getWidth(), sourceImage.getHeight());
        double scale = Math.min(1.0, (double) MAXIMUM_LONG_SIDE_PIXELS / sourceLongSide);
        int targetWidth = Math.max(1, (int) Math.round(sourceImage.getWidth() * scale));
        int targetHeight = Math.max(1, (int) Math.round(sourceImage.getHeight() * scale));

        // 알파 없는 RGB: JPEG 는 투명을 담지 못함
        BufferedImage targetImage = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = targetImage.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, targetWidth, targetHeight);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.drawImage(sourceImage, 0, 0, targetWidth, targetHeight, null);
        } finally {
            graphics.dispose();
        }
        return targetImage;
    }

    private static byte[] encodeJpeg(BufferedImage image) {
        ImageWriter jpegWriter = ImageIO.getImageWritersByFormatName(JPEG_FORMAT_NAME).next();
        ByteArrayOutputStream jpegBytes = new ByteArrayOutputStream();
        try (ImageOutputStream imageOutputStream = new MemoryCacheImageOutputStream(jpegBytes)) {
            ImageWriteParam writeParam = jpegWriter.getDefaultWriteParam();
            writeParam.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            writeParam.setCompressionQuality(JPEG_QUALITY);
            jpegWriter.setOutput(imageOutputStream);
            // 메타데이터 없이 픽셀만 기록
            jpegWriter.write(null, new IIOImage(image, null, null), writeParam);
        } catch (IOException exception) {
            throw rejected("encode failed : " + exception.getClass().getSimpleName());
        } finally {
            jpegWriter.dispose();
        }
        return jpegBytes.toByteArray();
    }

    // 거부·실패 공통: EXTERNAL_API_ERROR (detail 에 원인)
    private static CustomException rejected(String detail) {
        return new CustomException(ErrorCode.EXTERNAL_API_ERROR, detail);
    }
}
