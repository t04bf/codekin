package org.ninestick.codekin.file.storage;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.ninestick.codekin.file.domain.enums.FileCode;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.file.enums.UploadType;

/**
 * 实际图像解码与重编码边界，覆盖 PNG、JPEG、WebP、伪装文件及像素超限。
 *
 * @author ninestick
 */
class AvatarImageProcessorTest {
    /** 真正执行的图像处理器。 */
    private final AvatarImageProcessor processor = new AvatarImageProcessor();

    @Test
    void pngIsResizedAndReencoded() throws Exception {
        byte[] input = image(2048, 100, "png");
        byte[] result = processor.process(input, "image/png", UploadType.AVATAR);
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(512, output.getWidth());
        assertEquals(25, output.getHeight());
        assertNotEquals(input.length, result.length);
    }

    @Test
    void resumePortraitKeepsRatioAt1024AndDoesNotEnlargeSmallerImages() throws Exception {
        byte[] result = processor.process(image(1800, 2400, "png"), "image/png", UploadType.RESUME_PHOTO);
        BufferedImage output = ImageIO.read(new ByteArrayInputStream(result));
        assertEquals(768, output.getWidth());
        assertEquals(1024, output.getHeight());
        byte[] smaller = processor.process(image(450, 600, "jpeg"), "image/jpeg", UploadType.RESUME_PHOTO);
        BufferedImage originalSize = ImageIO.read(new ByteArrayInputStream(smaller));
        assertEquals(450, originalSize.getWidth());
        assertEquals(600, originalSize.getHeight());
    }

    @Test
    void jpegIsAcceptedAndStoredAsPng() throws Exception {
        byte[] result = processor.process(image(50, 20, "jpeg"), "image/jpeg", UploadType.AVATAR);
        assertArrayEquals(new byte[]{(byte) 137, 80, 78, 71}, java.util.Arrays.copyOf(result, 4));
    }

    @ParameterizedTest
    @EnumSource(UploadType.class)
    void webpUsesThirdPartyDecoderAndProducesPng(UploadType type) throws Exception {
        try (var input = getClass().getResourceAsStream("/images/sample.webp")) {
            assertNotNull(input);
            byte[] result = processor.process(input.readAllBytes(), "image/webp", type);
            assertNotNull(ImageIO.read(new ByteArrayInputStream(result)));
        }
    }

    @ParameterizedTest
    @EnumSource(UploadType.class)
    void fakeImageAndFalseMimeAreRejected(UploadType type) throws Exception {
        assertEquals(FileCode.INVALID_IMAGE, assertThrows(BusinessException.class,
                () -> processor.process("<html>fake.png</html>".getBytes(), "image/png", type)).getRecode());
        assertEquals(FileCode.INVALID_IMAGE, assertThrows(BusinessException.class,
                () -> processor.process(image(10, 10, "png"), "image/jpeg", type)).getRecode());
    }

    @ParameterizedTest
    @EnumSource(UploadType.class)
    void pixelLimitIsCheckedBeforeFullDecode(UploadType type) throws Exception {
        byte[] input = image(4097, 1, "png");
        assertEquals(FileCode.PIXEL_LIMIT, assertThrows(BusinessException.class,
                () -> processor.process(input, "image/png", type)).getRecode());
    }

    @Test
    void totalPixelsAreLimitedEvenWhenBothSidesFit() throws Exception {
        byte[] input = image(4000, 3001, "png");
        assertEquals(FileCode.PIXEL_LIMIT, assertThrows(BusinessException.class,
                () -> processor.process(input, "image/png", UploadType.AVATAR)).getRecode());
    }

    @Test
    void emptyAndOversizedFilesAreRejected() {
        assertEquals(FileCode.SIZE_LIMIT, assertThrows(BusinessException.class,
                () -> processor.process(new byte[0], "image/png", UploadType.AVATAR)).getRecode());
        assertEquals(FileCode.SIZE_LIMIT, assertThrows(BusinessException.class,
                () -> processor.process(new byte[(int) UploadType.AVATAR.getMaxBytes() + 1],
                        "image/png", UploadType.AVATAR)).getRecode());
    }

    /** 测试中生成真实图片，而非模拟解码返回值。 */
    private byte[] image(int width, int height, String format) throws IOException {
        try (var out = new ByteArrayOutputStream()) {
            assertTrue(ImageIO.write(new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB), format, out));
            return out.toByteArray();
        }
    }
}
