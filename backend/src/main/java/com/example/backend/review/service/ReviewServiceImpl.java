package com.example.backend.review.service;

import com.example.backend.auth.entity.Customer;
import com.example.backend.auth.entity.Employee;
import com.example.backend.auth.repository.EmployeeRepository;
import com.example.backend.cart.repository.CustomerCartRepository;
import com.example.backend.order.entity.Order;
import com.example.backend.order.entity.OrderItem;
import com.example.backend.order.entity.OrderStatus;
import com.example.backend.order.exception.CommerceException;
import com.example.backend.order.repository.OrderItemRepository;
import com.example.backend.order.repository.OrderRepository;
import com.example.backend.product.entity.Product;
import com.example.backend.product.entity.ProductVariant;
import com.example.backend.product.repository.ProductRepository;
import com.example.backend.product.repository.ProductVariantRepository;
import com.example.backend.review.dto.*;
import com.example.backend.review.entity.Review;
import com.example.backend.review.entity.ReviewStatus;
import com.example.backend.review.repository.ReviewRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReviewServiceImpl implements ReviewService {
    private final ReviewRepository reviews;
    private final CustomerCartRepository customers;
    private final OrderRepository orders;
    private final OrderItemRepository orderItems;
    private final ProductVariantRepository variants;
    private final ProductRepository products;
    private final EmployeeRepository employees;

    public ReviewServiceImpl(ReviewRepository reviews, CustomerCartRepository customers, OrderRepository orders,
                             OrderItemRepository orderItems, ProductVariantRepository variants, ProductRepository products,
                             EmployeeRepository employees) {
        this.reviews = reviews; this.customers = customers; this.orders = orders; this.orderItems = orderItems;
        this.variants = variants; this.products = products; this.employees = employees;
    }

    @Override
    @Transactional
    public ReviewResponse create(UUID userId, CreateReviewRequest request) {
        Customer customer = customer(userId);
        OrderItem item = orderItems.findById(request.orderItemId())
                .orElseThrow(() -> new CommerceException(404, "Không tìm thấy sản phẩm đã mua"));
        Order order = orders.findById(item.getOrderId()).filter(o -> o.getCustomerId().equals(customer.getCustomerId()))
                .orElseThrow(() -> new CommerceException(404, "Không tìm thấy sản phẩm đã mua"));
        if (order.getStatus() != OrderStatus.DELIVERED) throw new CommerceException(409, "Chỉ đánh giá được sản phẩm của đơn đã giao");
        if (reviews.existsByCustomerIdAndOrderItemId(customer.getCustomerId(), item.getOrderItemId())) {
            throw new CommerceException(409, "Bạn đã đánh giá sản phẩm này");
        }
        ProductVariant variant = variants.findById(item.getVariantId()).orElseThrow(() -> new CommerceException(409, "Sản phẩm không còn tồn tại"));
        Review review = new Review();
        review.setCustomerId(customer.getCustomerId());
        review.setProductId(variant.getProduct().getProductId());
        review.setOrderItemId(item.getOrderItemId());
        review.setRating(request.rating());
        review.setReviewText(request.reviewText() == null || request.reviewText().isBlank() ? null : request.reviewText().strip());
        review.setCreatedAt(OffsetDateTime.now());
        review.setStatus(ReviewStatus.VISIBLE);
        try {
            review = reviews.saveAndFlush(review);
        } catch (DataIntegrityViolationException exception) {
            // uq_reviews_customer_order_item: a concurrent submit of the same line.
            throw new CommerceException(409, "Bạn đã đánh giá sản phẩm này");
        }
        return map(List.of(review)).get(0);
    }

    @Override
    public List<ReviewResponse> mineForOrder(UUID userId, UUID orderId) {
        Customer customer = customer(userId);
        List<UUID> lines = orderItems.findByOrderIdOrderByVariantIdAscOrderItemIdAsc(orderId).stream().map(OrderItem::getOrderItemId).toList();
        return lines.isEmpty() ? List.of() : map(reviews.findByCustomerIdAndOrderItemIdIn(customer.getCustomerId(), lines));
    }

    @Override
    public ReviewPageResponse forProduct(UUID productId, int page, int size) {
        Page<Review> result = reviews.findByProductIdAndStatus(productId, ReviewStatus.VISIBLE, pageable(page, size));
        Object[] stats = reviews.stats(productId).stream().findFirst().orElse(new Object[]{null, 0L});
        Double average = stats[0] == null ? null : Math.round(((Number) stats[0]).doubleValue() * 10) / 10.0;
        return new ReviewPageResponse(map(result.getContent()), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), average, ((Number) stats[1]).longValue());
    }

    @Override
    public ReviewPageResponse list(ReviewStatus status, int page, int size) {
        Page<Review> result = status == null ? reviews.findAll(pageable(page, size)) : reviews.findByStatus(status, pageable(page, size));
        return new ReviewPageResponse(map(result.getContent()), result.getNumber(), result.getSize(), result.getTotalElements(),
                result.getTotalPages(), null, null);
    }

    @Override
    @Transactional
    public ReviewResponse moderate(UUID userId, UUID reviewId, ModerateReviewRequest request) {
        if ((request.replyText() == null || request.replyText().isBlank()) && request.status() == null) {
            throw new CommerceException(400, "Nhập phản hồi hoặc chọn trạng thái hiển thị");
        }
        Employee employee = employees.findByUser_UserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ nhân viên"));
        Review review = reviews.findById(reviewId).orElseThrow(() -> new CommerceException(404, "Không tìm thấy đánh giá"));
        if (request.replyText() != null && !request.replyText().isBlank()) {
            review.setReplyText(request.replyText().strip());
            review.setRepliedAt(OffsetDateTime.now());
        }
        if (request.status() != null) review.setStatus(request.status());
        review.setEmployeeId(employee.getEmployeeId());
        return map(List.of(reviews.saveAndFlush(review))).get(0);
    }

    private Customer customer(UUID userId) {
        return customers.findByUser_UserId(userId).orElseThrow(() -> new CommerceException(403, "Tài khoản chưa có hồ sơ khách hàng"));
    }

    private static PageRequest pageable(int page, int size) {
        if (page < 0 || size < 1 || size > 100 || (long) page * size > Integer.MAX_VALUE) {
            throw new CommerceException(400, "page >= 0, size từ 1–100, offset trong giới hạn");
        }
        return PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("reviewId")));
    }

    private List<ReviewResponse> map(List<Review> rows) {
        if (rows.isEmpty()) return List.of();
        Map<UUID, String> customerNames = customers.findAllById(rows.stream().map(Review::getCustomerId).distinct().toList())
                .stream().collect(Collectors.toMap(Customer::getCustomerId, Customer::getFullName));
        Map<UUID, String> productNames = products.findAllById(rows.stream().map(Review::getProductId).distinct().toList())
                .stream().collect(Collectors.toMap(Product::getProductId, Product::getProductName));
        Map<UUID, String> employeeNames = employees.findAllById(rows.stream().map(Review::getEmployeeId).filter(Objects::nonNull).distinct().toList())
                .stream().collect(Collectors.toMap(Employee::getEmployeeId, Employee::getFullName));
        return rows.stream().map(r -> new ReviewResponse(r.getReviewId(), r.getProductId(), productNames.get(r.getProductId()),
                r.getOrderItemId(), customerNames.get(r.getCustomerId()), r.getRating(), r.getReviewText(), r.getReplyText(),
                r.getEmployeeId() == null ? null : employeeNames.get(r.getEmployeeId()), r.getCreatedAt(), r.getRepliedAt(),
                r.getStatus())).toList();
    }
}
