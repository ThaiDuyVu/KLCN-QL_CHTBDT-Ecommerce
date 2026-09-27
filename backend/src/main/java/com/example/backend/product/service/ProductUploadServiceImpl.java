package com.example.backend.product.service;

import com.example.backend.product.dto.ProductImageRequest;
import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.SpecificationRequest;
import com.example.backend.product.dto.ProductResponse;
import com.example.backend.product.exception.ProductUploadException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
public class ProductUploadServiceImpl implements ProductUploadService {
    private final ProductService productService;
    private final ProductImageService imageService;
    private final SpecificationService specificationService;
    private final Path directory;

    public ProductUploadServiceImpl(ProductService productService, ProductImageService imageService, SpecificationService specificationService,
            @Value("${app.product-images.directory:./uploads/products}") String directory) {
        this.productService = productService;
        this.imageService = imageService;
        this.specificationService = specificationService;
        this.directory = Path.of(directory).toAbsolutePath().normalize();
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request, List<MultipartFile> images, int primaryImageIndex) {
        return create(request, images, primaryImageIndex, List.of());
    }

    @Override
    @Transactional
    public ProductResponse create(ProductRequest request, List<MultipartFile> images, int primaryImageIndex,
                                  List<SpecificationRequest> specifications) {
        List<MultipartFile> files = images == null ? List.of() : images;
        if (files.size() > 10) throw new ProductUploadException(400, "Chỉ được chọn tối đa 10 ảnh");
        if (primaryImageIndex < 0 || (!files.isEmpty() && primaryImageIndex >= files.size()))
            throw new ProductUploadException(400, "Ảnh chính không hợp lệ");
        List<Path> written = new ArrayList<>();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) written.forEach(ProductUploadServiceImpl::remove);
            }
        });
        ProductResponse product = productService.createProduct(request);
        for (int i = 0; i < files.size(); i++) {
            String filename = store(files.get(i), written);
            ProductImageRequest image = new ProductImageRequest();
            image.setImageUrl("/api/product-media/" + filename);
            image.setIsPrimary(i == primaryImageIndex);
            imageService.createImage(product.productId(), image);
        }
        for (SpecificationRequest specification : specifications) {
            specificationService.createSpecification(product.productId(), specification);
        }
        return productService.getProductById(product.productId(), null);
    }

    private String store(MultipartFile file, List<Path> written) {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024)
            throw new ProductUploadException(400, "Mỗi ảnh phải có dữ liệu và không vượt quá 5 MB");
        // Decode and re-encode actual raster content; never trust the filename or MIME header.
        try (var stream = file.getInputStream();
             ImageInputStream input = ImageIO.createImageInputStream(stream)) {
            if (input == null) throw new ProductUploadException(400, "Không đọc được ảnh");
            var readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) throw new ProductUploadException(400, "Chỉ hỗ trợ ảnh JPG hoặc PNG hợp lệ");
            ImageReader reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(java.util.Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png"))
                    throw new ProductUploadException(400, "Chỉ hỗ trợ ảnh JPG hoặc PNG");
                reader.setInput(input);
                long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
                if (pixels > 20_000_000) throw new ProductUploadException(400, "Ảnh không được vượt quá 20 megapixel");
                var image = reader.read(0);
                String filename = UUID.randomUUID() + (format.equals("jpeg") ? ".jpg" : ".png");
                Files.createDirectories(directory);
                Path target = directory.resolve(filename);
                written.add(target);
                if (!ImageIO.write(image, format, target.toFile())) throw new IOException("Image encoder unavailable");
                return filename;
            } finally { reader.dispose(); }
        } catch (IOException exception) {
            throw new ProductUploadException(400, "Không đọc hoặc lưu được ảnh. Hãy kiểm tra file và thử lại");
        }
    }

    @Override
    public Resource read(String filename) {
        if (!filename.matches("[a-f0-9-]{36}\\.(jpg|png)")) throw new ProductUploadException(404, "Không tìm thấy ảnh");
        Path target = directory.resolve(filename);
        if (!Files.isRegularFile(target)) throw new ProductUploadException(404, "Không tìm thấy ảnh");
        return new FileSystemResource(target);
    }

    private static void remove(Path path) {
        try { Files.deleteIfExists(path); }
        catch (IOException exception) {
            org.slf4j.LoggerFactory.getLogger(ProductUploadServiceImpl.class).warn("Không thể dọn file ảnh sau rollback: {}", path);
        }
    }
}
