import { useCallback } from 'react';
import { Link, useParams } from 'react-router';
import PageHeader from '../../../components/ui/PageHeader';
import useProductRequest from '../../products/hooks/useProductRequest';
import CommerceState from '../../orders/components/CommerceState';
import { promotionApi } from '../api/promotionApi';
import PromotionForm from '../components/PromotionForm';
import '../promotions.css';
export default function PromotionFormPage() {
  const { promotionId } = useParams();
  const load = useCallback((signal) => promotionId ? promotionApi.detail(promotionId, signal) : Promise.resolve(null), [promotionId]);
  const { data, error, isLoading, retry } = useProductRequest(`promotion-form:${promotionId}`, load);
  return <><PageHeader eyebrow="Quản lý / Khuyến mãi" title={promotionId ? 'Sửa khuyến mãi' : 'Tạo khuyến mãi'} description="Giảm giá theo sản phẩm, áp dụng cho mọi phiên bản." /><Link className="commerce-back-link" to={promotionId ? `/promotions/${promotionId}` : '/promotions'}>← Quay lại</Link><CommerceState loading={isLoading} error={error} retry={retry} />{!isLoading && !error && <PromotionForm key={promotionId || 'new'} promotion={data} />}</>;
}
