package org.ninestick.codekin.file.storage;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Locale;
import javax.imageio.*;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.ninestick.codekin.file.domain.enums.FileCode;
import org.ninestick.codekin.model.common.BusinessException;
import org.ninestick.codekin.model.file.enums.UploadType;
import org.springframework.stereotype.Component;

/**
 * 头像和简历证件照共用 ImageIO 与 TwelveMonkeys 校验，根据用途限制生成不含源元数据的单张 PNG。
 *
 * @author ninestick
 */
@Component
public class AvatarImageProcessor {
    /** 先读取尺寸并限制像素数，随后解码和缩小；不依赖 MIME、扩展名或前端图片校验。 */
    public byte[] process(byte[] source, String declaredContentType, UploadType type) {
        if (source.length < 1 || source.length > type.getMaxBytes()) throw new BusinessException(FileCode.SIZE_LIMIT);
        try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(source))) {
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new BusinessException(FileCode.INVALID_IMAGE);
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, true, true);
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!type.getFormats().containsKey(format)
                        || !type.getFormats().get(format).equals(declaredContentType)) {
                    throw new BusinessException(FileCode.INVALID_IMAGE);
                }
                // WebP 动画的帧数查询返回未知值；拒绝多帧或未知帧数，不解码完整动画。
                if ("webp".equals(format) && reader.getNumImages(false) != 1) throw new BusinessException(FileCode.INVALID_IMAGE);
                int width = reader.getWidth(0);
                int height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > type.getMaxWidth() || height > type.getMaxHeight()
                        || (long) width * height > type.getMaxPixels()) {
                    throw new BusinessException(FileCode.PIXEL_LIMIT);
                }
                BufferedImage image = reader.read(0);
                if (image == null) throw new BusinessException(FileCode.INVALID_IMAGE);
                double ratio = Math.min(1D, (double) type.getOutputMaxSide() / Math.max(width, height));
                int targetWidth = Math.max(1, (int) Math.round(width * ratio));
                int targetHeight = Math.max(1, (int) Math.round(height * ratio));
                BufferedImage normalized = new BufferedImage(targetWidth, targetHeight, BufferedImage.TYPE_INT_ARGB);
                Graphics2D graphics = normalized.createGraphics();
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
                    graphics.drawImage(image, 0, 0, targetWidth, targetHeight, null);
                } finally {
                    graphics.dispose();
                    image.flush();
                }
                try (var output = new ByteArrayOutputStream()) {
                    if (!ImageIO.write(normalized, FileStorageConstants.PNG_FORMAT, output)) {
                        throw new BusinessException(FileCode.INVALID_IMAGE);
                    }
                    byte[] bytes = output.toByteArray();
                    if (bytes.length > type.getMaxBytes()) throw new BusinessException(FileCode.SIZE_LIMIT);
                    return bytes;
                } finally {
                    normalized.flush();
                }
            } finally {
                reader.dispose();
            }
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new BusinessException(FileCode.INVALID_IMAGE);
        }
    }
}
