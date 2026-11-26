package kr.suhsaechan.secbrief.advisory.image;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.zip.CRC32;
import javax.imageio.ImageIO;
import kr.suhsaechan.secbrief.common.exception.CustomException;
import kr.suhsaechan.secbrief.common.exception.constant.ErrorCode;
import org.junit.jupiter.api.Test;

/**
 * 이미지 줄이기: 큰 그림 > 긴 변 800px JPEG, 작은 그림은 그대로, 투명 > 흰색, GIF·WebP 디코드, 깨진 파일·거대 픽셀·너무 작은 그림 거부
 */
class ArticleImageResizerTest {

    private static final Color OPAQUE_RED = new Color(200, 30, 30);
    private static final Color TRANSPARENT = new Color(0, 0, 0, 0);
    // JPEG 손실 압축 오차 허용 (채널당)
    private static final int COLOR_TOLERANCE = 12;

    private final ArticleImageResizer resizer = new ArticleImageResizer();

    @Test
    void shrinksLargePngToJpegWithLongSideEightHundred() throws IOException {
        byte[] resizedBytes = resizer.toResizedJpeg(encode(createImage(2400, 1200, OPAQUE_RED), "png"));

        assertThat(isJpeg(resizedBytes)).isTrue();
        BufferedImage resizedImage = ImageIO.read(new ByteArrayInputStream(resizedBytes));
        assertThat(resizedImage.getWidth()).isEqualTo(ArticleImageResizer.MAXIMUM_LONG_SIDE_PIXELS);
        assertThat(resizedImage.getHeight()).isEqualTo(400);
        assertColorNear(resizedImage.getRGB(400, 200), OPAQUE_RED);
    }

    @Test
    void shrinksTallImageByHeight() throws IOException {
        BufferedImage resizedImage = decode(resizer.toResizedJpeg(encode(createImage(1000, 4000, OPAQUE_RED), "png")));

        assertThat(resizedImage.getWidth()).isEqualTo(200);
        assertThat(resizedImage.getHeight()).isEqualTo(800);
    }

    @Test
    void keepsSizeOfSmallImage() throws IOException {
        BufferedImage resizedImage = decode(resizer.toResizedJpeg(encode(createImage(300, 200, OPAQUE_RED), "png")));

        assertThat(resizedImage.getWidth()).isEqualTo(300);
        assertThat(resizedImage.getHeight()).isEqualTo(200);
    }

    @Test
    void fillsTransparentBackgroundWithWhite() throws IOException {
        byte[] resizedBytes = resizer.toResizedJpeg(encode(createImage(400, 300, TRANSPARENT), "png"));

        assertThat(isJpeg(resizedBytes)).isTrue();
        assertColorNear(decode(resizedBytes).getRGB(200, 150), Color.WHITE);
    }

    @Test
    void reencodesJpegAndDecodesGif() throws IOException {
        BufferedImage opaqueImage = new BufferedImage(1600, 900, BufferedImage.TYPE_INT_RGB);

        assertThat(decode(resizer.toResizedJpeg(encode(opaqueImage, "jpg"))).getWidth()).isEqualTo(800);
        assertThat(decode(resizer.toResizedJpeg(encode(opaqueImage, "gif"))).getHeight()).isEqualTo(450);
    }

    @Test
    void decodesWebp() throws IOException {
        // fixture: 240x160 단색 WebP
        BufferedImage resizedImage = decode(resizer.toResizedJpeg(readFixture("sample.webp")));

        assertThat(resizedImage.getWidth()).isEqualTo(240);
        assertThat(resizedImage.getHeight()).isEqualTo(160);
        assertColorNear(resizedImage.getRGB(120, 80), OPAQUE_RED);
    }

    @Test
    void rejectsBytesThatAreNotImage() {
        assertRejected("<html><body>not an image</body></html>".getBytes(), "unsupported image format");
        assertRejected(new byte[0], "unsupported image format");
    }

    @Test
    void rejectsUnsupportedFormat() throws IOException {
        assertRejected(encode(new BufferedImage(300, 200, BufferedImage.TYPE_INT_RGB), "bmp"), "unsupported image format");
    }

    @Test
    void rejectsTruncatedImage() throws IOException {
        byte[] pngBytes = encode(createNoiseImage(600, 400), "png");

        assertThatThrownBy(() -> resizer.toResizedJpeg(Arrays.copyOf(pngBytes, pngBytes.length / 3)))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.EXTERNAL_API_ERROR);
    }

    @Test
    void rejectsHugePixelCountFromHeaderOnly() throws IOException {
        // 그림 데이터 없이 헤더만 10000x10000 (1억 픽셀) 이라고 밝힌 PNG
        assertRejected(createPngHeaderOnly(10_000, 10_000), "too many pixels");
    }

    @Test
    void rejectsTrackingPixel() throws IOException {
        assertRejected(encode(createImage(1, 1, OPAQUE_RED), "png"), "too small");
    }

    private void assertRejected(byte[] imageBytes, String expectedDetail) {
        assertThatThrownBy(() -> resizer.toResizedJpeg(imageBytes))
                .isInstanceOf(CustomException.class)
                .extracting("detail").isEqualTo(expectedDetail);
    }

    private static void assertColorNear(int actualRgb, Color expected) {
        Color actual = new Color(actualRgb);
        assertThat(actual.getRed()).isBetween(expected.getRed() - COLOR_TOLERANCE, expected.getRed() + COLOR_TOLERANCE);
        assertThat(actual.getGreen()).isBetween(expected.getGreen() - COLOR_TOLERANCE, expected.getGreen() + COLOR_TOLERANCE);
        assertThat(actual.getBlue()).isBetween(expected.getBlue() - COLOR_TOLERANCE, expected.getBlue() + COLOR_TOLERANCE);
    }

    // JPEG 시작 표식 (FF D8)
    private static boolean isJpeg(byte[] imageBytes) {
        return imageBytes.length > 2 && (imageBytes[0] & 0xff) == 0xff && (imageBytes[1] & 0xff) == 0xd8;
    }

    private static BufferedImage createImage(int width, int height, Color fillColor) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = image.createGraphics();
        graphics.setBackground(fillColor);
        graphics.clearRect(0, 0, width, height);
        graphics.dispose();
        return image;
    }

    // 압축이 잘 안 되는 그림 (잘라도 헤더 뒤에 데이터가 충분히 남게)
    private static BufferedImage createNoiseImage(int width, int height) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                image.setRGB(x, y, (x * 31 + y * 17) * 2654435 & 0xffffff);
            }
        }
        return image;
    }

    private static byte[] encode(BufferedImage image, String formatName) throws IOException {
        ByteArrayOutputStream encodedBytes = new ByteArrayOutputStream();
        assertThat(ImageIO.write(image, formatName, encodedBytes)).isTrue();
        return encodedBytes.toByteArray();
    }

    private static BufferedImage decode(byte[] imageBytes) throws IOException {
        return ImageIO.read(new ByteArrayInputStream(imageBytes));
    }

    // PNG 서명 + IHDR 청크 (8비트 RGBA)
    private static byte[] createPngHeaderOnly(int width, int height) throws IOException {
        ByteArrayOutputStream headerData = new ByteArrayOutputStream();
        DataOutputStream headerDataStream = new DataOutputStream(headerData);
        headerDataStream.writeBytes("IHDR");
        headerDataStream.writeInt(width);
        headerDataStream.writeInt(height);
        headerDataStream.write(new byte[] {8, 6, 0, 0, 0});
        CRC32 checksum = new CRC32();
        checksum.update(headerData.toByteArray());

        ByteArrayOutputStream pngBytes = new ByteArrayOutputStream();
        DataOutputStream pngStream = new DataOutputStream(pngBytes);
        pngStream.write(new byte[] {(byte) 0x89, 'P', 'N', 'G', '\r', '\n', 0x1a, '\n'});
        pngStream.writeInt(headerData.size() - 4);
        pngStream.write(headerData.toByteArray());
        pngStream.writeInt((int) checksum.getValue());
        return pngBytes.toByteArray();
    }

    private static byte[] readFixture(String fileName) throws IOException {
        try (InputStream fixtureStream = ArticleImageResizerTest.class.getResourceAsStream("/fixtures/image/" + fileName)) {
            return fixtureStream.readAllBytes();
        }
    }
}
