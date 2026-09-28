from datetime import date, datetime
from decimal import Decimal
from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field

from app.shared.contracts.enums import MessageRole
from app.shared.contracts.knowledge import KnowledgeChunkMatch
from app.shared.contracts.product import ProductMatch
from app.shared.contracts.query import QueryPlan

class ChatMessage(BaseModel):
    model_config = ConfigDict(extra="forbid")

    role: MessageRole
    content: str = Field(min_length=1)
    created_at: datetime


class AuthenticatedCustomer(BaseModel):
    model_config = ConfigDict(extra="forbid")

    user_id: UUID
    customer_id: UUID
    display_name: str


class QueryContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    customer: AuthenticatedCustomer
    session_id: UUID

    current_message: str = Field(min_length=1)

    recent_messages: list[ChatMessage] = Field(
        default_factory=list
    )

class CartItemContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    variant_id: UUID
    product_name: str = Field(min_length=1)
    sku: str = Field(min_length=1)

    quantity: int = Field(ge=1)
    effective_price: Decimal = Field(ge=0)

    available_quantity: int | None = Field(
        default=None,
        ge=0,
    )


class CartContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    cart_id: UUID
    warehouse_id: UUID | None = None
    warehouse_name: str | None = None

    items: list[CartItemContext] = Field(
        default_factory=list
    )

    subtotal: Decimal = Field(default=Decimal("0"), ge=0)


class PaymentContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    payment_id: UUID
    payment_method: str
    status: str
    amount: Decimal = Field(ge=0)


class OrderContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    order_id: UUID
    order_code: str = Field(min_length=1)
    order_date: datetime

    status: str

    total_amount: Decimal = Field(ge=0)

    payment: PaymentContext | None = None


class WarrantyContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    warranty_id: UUID
    order_id: UUID

    product_name: str = Field(min_length=1)
    sku: str = Field(min_length=1)

    serial_number: str | None = None
    imei_numbers: list[str] = Field(default_factory=list)

    start_date: date
    end_date: date

    status: str
    eligible: bool


class WarrantyTicketContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    ticket_id: UUID
    ticket_code: str = Field(min_length=1)

    warranty_id: UUID

    product_name: str = Field(min_length=1)
    sku: str = Field(min_length=1)

    issue_description: str = Field(min_length=1)
    resolution_note: str | None = None

    status: str

    created_at: datetime
    resolved_at: datetime | None = None


class CustomerContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    cart: CartContext | None = None

    recent_orders: list[OrderContext] = Field(
        default_factory=list
    )

    warranties: list[WarrantyContext] = Field(
        default_factory=list
    )

    warranty_tickets: list[WarrantyTicketContext] = Field(
        default_factory=list
    )

class GroundedContext(BaseModel):
    model_config = ConfigDict(extra="forbid")

    query_plan: QueryPlan

    user_query: str = Field(min_length=1)

    relevant_history: list[ChatMessage] = Field(
        default_factory=list
    )

    products: list[ProductMatch] = Field(
        default_factory=list
    )

    knowledge_chunks: list[KnowledgeChunkMatch] = Field(
        default_factory=list
    )

    customer_context: CustomerContext | None = None