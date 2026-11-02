"""Internal outcomes for proven owned-order read errors; no public DTO/status additions."""
PAYMENT_CARDINALITY_MESSAGE = 'Đơn không có đúng một payment; cần rà dữ liệu trước khi xử lý'
PAYMENT_UNAVAILABLE_MESSAGE = (
    'Không thể xác minh trạng thái thanh toán của đơn hàng này vì dữ liệu thanh toán '
    'chưa đầy đủ hoặc không nhất quán.'
)


class OwnedOrderPaymentIntegrityError(RuntimeError):
    """The existing owned detail GET reported a verified payment-cardinality conflict."""

    def __init__(self):
        super().__init__(PAYMENT_UNAVAILABLE_MESSAGE)
