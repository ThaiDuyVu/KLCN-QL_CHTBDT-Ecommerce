package com.example.backend.product.service;

import com.example.backend.product.dto.ProductRequest;
import com.example.backend.product.dto.SpecificationRequest;
import com.example.backend.product.dto.ProductResponse;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

public interface ProductUploadService {
    ProductResponse create(ProductRequest request, List<MultipartFile> images, int primaryImageIndex);
    ProductResponse create(ProductRequest request, List<MultipartFile> images, int primaryImageIndex,
                           List<SpecificationRequest> specifications);
    Resource read(String filename);
}
