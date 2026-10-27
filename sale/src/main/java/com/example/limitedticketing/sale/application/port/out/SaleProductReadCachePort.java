package com.example.limitedticketing.sale.application.port.out;

import com.example.limitedticketing.sale.application.port.out.dto.CacheFillLease;
import com.example.limitedticketing.sale.application.port.out.dto.CacheLookup;
import com.example.limitedticketing.sale.application.port.out.dto.SaleProductCacheQuery;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductDetail;
import com.example.limitedticketing.sale.application.port.out.dto.CachedSaleProductPage;

public interface SaleProductReadCachePort {
    CacheLookup<CachedSaleProductDetail> findDetail(Long saleProductId);

    CacheLookup<CachedSaleProductPage> findPage(SaleProductCacheQuery query);

    void storeDetail(Long saleProductId, CacheFillLease lease, CachedSaleProductDetail result);

    void storePage(SaleProductCacheQuery query, CacheFillLease lease, CachedSaleProductPage result);

    void releaseDetail(Long saleProductId, CacheFillLease lease);

    void releasePage(SaleProductCacheQuery query, CacheFillLease lease);

    void invalidate();
}
