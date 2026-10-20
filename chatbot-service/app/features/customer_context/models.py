from uuid import UUID

from pydantic import BaseModel, ConfigDict


class AuthenticatedCustomer(BaseModel):
    model_config = ConfigDict(extra="forbid")

    user_id: UUID
    customer_id: UUID
    display_name: str

    @classmethod
    def from_backend_user(cls, payload: dict[str, object]) -> "AuthenticatedCustomer":
        user_id_value = payload.get("userId") or payload.get("user_id")
        customer_id_value = payload.get("customerId") or payload.get("customer_id")
        display_name = payload.get("displayName") or payload.get("display_name")
        if user_id_value is None or customer_id_value is None or display_name is None:
            raise ValueError("Missing user identity data")

        user_id = UUID(str(user_id_value))
        return cls(
            user_id=user_id,
            customer_id=UUID(str(customer_id_value)),
            display_name=str(display_name),
        )
