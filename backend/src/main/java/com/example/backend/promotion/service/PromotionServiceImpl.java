package com.example.backend.promotion.service;

import com.example.backend.promotion.dto.*;
import com.example.backend.promotion.entity.*;
import com.example.backend.promotion.exception.PromotionException;
import com.example.backend.promotion.repository.*;
import com.example.backend.product.entity.Product;
import com.example.backend.product.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.*;
import org.springframework.data.jpa.domain.Specification;
import java.math.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly=true)
public class PromotionServiceImpl implements PromotionService {
    private final PromotionRepository promotions;
    private final PromotionProductRepository links;
    private final ProductRepository products;
    public PromotionServiceImpl(PromotionRepository promotions, PromotionProductRepository links, ProductRepository products) {
        this.promotions=promotions; this.links=links; this.products=products;
    }
    @Override public PromotionPageResponse list(int page, int size, String keyword, PromotionStatus status) {
        if (page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE) throw new PromotionException(400,"page >= 0, size từ 1 đến 100");
        Specification<Promotion> filter=(root,query,cb)-> {
            var conditions=new ArrayList<jakarta.persistence.criteria.Predicate>();
            if (status!=null) conditions.add(cb.equal(root.get("status"),status));
            if (keyword!=null && !keyword.isBlank()) {
                String value=keyword.trim().toLowerCase(Locale.ROOT).replace("\\","\\\\").replace("%","\\%").replace("_","\\_");
                conditions.add(cb.like(cb.lower(root.get("promotionName")),"%"+value+"%",'\\'));
            }
            return cb.and(conditions.toArray(jakarta.persistence.criteria.Predicate[]::new));
        };
        var rows=promotions.findAll(filter,PageRequest.of(page,size,Sort.by(Sort.Order.desc("startDate"),Sort.Order.asc("promotionId"))));
        var grouped=productLinks(rows.getContent().stream().map(Promotion::getPromotionId).toList());
        var response=new PromotionPageResponse();
        response.setContent(rows.getContent().stream().map(p->response(p,grouped.getOrDefault(p.getPromotionId(),List.of()))).toList());
        response.setPage(rows.getNumber()); response.setSize(rows.getSize()); response.setTotalElements(rows.getTotalElements()); response.setTotalPages(rows.getTotalPages());
        return response;
    }
    @Override public PromotionResponse detail(UUID id) {
        var p=promotions.findById(id).orElseThrow(()->new PromotionException(404,"Không tìm thấy khuyến mãi"));
        return response(p,links.findById_PromotionIdIn(List.of(id)));
    }
    @Override @Transactional public PromotionResponse create(PromotionRequest request) { return save(null,request); }
    @Override @Transactional public PromotionResponse update(UUID id,PromotionRequest request) { return save(id,request); }
    private PromotionResponse save(UUID id, PromotionRequest request) {
        validate(request);
        var p=id==null?new Promotion():locked(id);
        var old=id==null?List.<PromotionProduct>of():links.findById_PromotionIdIn(List.of(id));
        var targetIds=new HashSet<>(request.getProductIds());
        var lockIds=new HashSet<>(targetIds); old.forEach(link->lockIds.add(link.getId().getProductId()));
        var lockedProducts=lockProducts(lockIds);
        checkOverlap(id,targetIds,request.getStartDate(),request.getEndDate(),request.getStatus());
        p.setPromotionName(request.getPromotionName().trim());
        p.setDescription(request.getDescription()==null?null:request.getDescription().trim());
        p.setDiscountType(request.getDiscountType()); p.setDiscountValue(request.getDiscountValue());
        p.setStartDate(request.getStartDate()); p.setEndDate(request.getEndDate()); p.setStatus(request.getStatus());
        promotions.saveAndFlush(p);
        if (id!=null) { links.deleteAll(old); links.flush(); }
        var newLinks=targetIds.stream().sorted().map(productId->new PromotionProduct(p,lockedProducts.get(productId))).toList();
        links.saveAllAndFlush(newLinks);
        return response(p,newLinks);
    }
    private void validate(PromotionRequest r) {
        if (r.getPromotionName()==null || r.getPromotionName().isBlank() || r.getPromotionName().trim().length()>255
                || r.getDiscountType()==null || r.getDiscountValue()==null || r.getStatus()==null
                || r.getStartDate()==null || r.getEndDate()==null || r.getProductIds()==null || r.getProductIds().contains(null)) {
            throw new PromotionException(400,"Dữ liệu khuyến mãi hoặc productIds không hợp lệ");
        }
        if (r.getEndDate().isBefore(r.getStartDate())) throw new PromotionException(400,"endDate phải >= startDate");
        if (r.getDiscountValue().signum()<0 || (r.getDiscountType()==DiscountType.PERCENTAGE && r.getDiscountValue().compareTo(new BigDecimal("100"))>0))
            throw new PromotionException(400,"PERCENTAGE phải từ 0 đến 100; FIXED_AMOUNT phải >= 0");
    }
    private Promotion locked(UUID id) { return promotions.lockById(id).orElseThrow(()->new PromotionException(404,"Không tìm thấy khuyến mãi")); }
    private Map<UUID,Product> lockProducts(Collection<UUID> ids) {
        Map<UUID,Product> result=new HashMap<>();
        for (UUID id:ids.stream().distinct().sorted().toList()) result.put(id,products.findByIdForUpdate(id).orElseThrow(()->new PromotionException(404,"Không tìm thấy Product: "+id)));
        return result;
    }
    private void checkOverlap(UUID excludeId,Collection<UUID> ids,OffsetDateTime start,OffsetDateTime end,PromotionStatus status) {
        if (status!=PromotionStatus.ACTIVE || ids.isEmpty()) return;
        for (var link:links.findOverlapping(ids,PromotionStatus.ACTIVE,start,end)) {
            if (!link.getId().getPromotionId().equals(excludeId)) throw new PromotionException(409,
                    "Khuyến mãi ACTIVE bị trùng thời gian với '"+link.getPromotion().getPromotionName()+"' trên sản phẩm '"+link.getProduct().getProductName()+"' ("+link.getId().getProductId()+")");
        }
    }
    @Override @Transactional public PromotionResponse status(UUID id,PromotionStatus status) {
        if (status==null) throw new PromotionException(400,"status là bắt buộc");
        var p=locked(id); var assigned=links.findById_PromotionIdIn(List.of(id));
        var ids=assigned.stream().map(link->link.getId().getProductId()).toList(); lockProducts(ids);
        checkOverlap(id,ids,p.getStartDate(),p.getEndDate(),status);
        p.setStatus(status); promotions.saveAndFlush(p); return response(p,assigned);
    }
    @Override @Transactional public void delete(UUID id) {
        var p=locked(id); var assigned=links.findById_PromotionIdIn(List.of(id));
        lockProducts(assigned.stream().map(link->link.getId().getProductId()).toList());
        links.deleteAll(assigned); links.flush(); promotions.delete(p); promotions.flush();
    }
    @Override public Map<UUID,Promotion> resolve(Collection<UUID> ids,OffsetDateTime now) {
        if (ids.isEmpty()) return Map.of();
        Map<UUID,Promotion> result=new HashMap<>();
        for (var link:links.findApplicable(ids,PromotionStatus.ACTIVE,now)) {
            if (result.put(link.getId().getProductId(),link.getPromotion())!=null) throw new PromotionException(409,"Product có nhiều Promotion hợp lệ; cần xử lý overlap trước khi bán");
        }
        return result;
    }
    @Override @Transactional public Map<UUID,Promotion> resolveForCheckout(Collection<UUID> ids,OffsetDateTime now) {
        lockProducts(ids); return resolve(ids,now);
    }
    @Override public PromotionPrice calculate(BigDecimal unitPrice,Promotion promotion) {
        BigDecimal original=unitPrice.setScale(2,RoundingMode.HALF_UP);
        BigDecimal discount=BigDecimal.ZERO.setScale(2);
        if (promotion!=null) discount=(promotion.getDiscountType()==DiscountType.PERCENTAGE
                ? original.multiply(promotion.getDiscountValue()).divide(new BigDecimal("100"),2,RoundingMode.HALF_UP)
                : promotion.getDiscountValue()).min(original).setScale(2,RoundingMode.HALF_UP);
        return new PromotionPrice(original,discount,original.subtract(discount),promotion==null?null:summary(promotion));
    }
    private PromotionSummaryResponse summary(Promotion p) {
        var r=new PromotionSummaryResponse(); r.setPromotionId(p.getPromotionId()); r.setPromotionName(p.getPromotionName());
        r.setDiscountType(p.getDiscountType()); r.setDiscountValue(p.getDiscountValue()); r.setStartDate(p.getStartDate()); r.setEndDate(p.getEndDate()); return r;
    }
    private Map<UUID,List<PromotionProduct>> productLinks(List<UUID> ids) {
        return ids.isEmpty()?Map.of():links.findById_PromotionIdIn(ids).stream().collect(Collectors.groupingBy(link->link.getId().getPromotionId()));
    }
    private PromotionResponse response(Promotion p,List<PromotionProduct> assigned) {
        var r=new PromotionResponse(); r.setPromotionId(p.getPromotionId()); r.setPromotionName(p.getPromotionName()); r.setDescription(p.getDescription());
        r.setDiscountType(p.getDiscountType()); r.setDiscountValue(p.getDiscountValue()); r.setStartDate(p.getStartDate()); r.setEndDate(p.getEndDate()); r.setStatus(p.getStatus());
        r.setProducts(assigned.stream().sorted(Comparator.comparing(link->link.getId().getProductId())).map(link->{
            var value=new PromotionProductResponse(); value.setProductId(link.getId().getProductId()); value.setProductName(link.getProduct().getProductName()); return value;
        }).toList()); return r;
    }
}
