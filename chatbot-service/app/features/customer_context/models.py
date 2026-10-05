from uuid import UUID

from pydantic import BaseModel, ConfigDict, Field


class AuthenticatedCustomer(BaseModel):
    model_config = ConfigDict(extra="forbid")

    user_id: UUID
    customer_id: UUID = Field(default_factory=lambda: UUID(int=0))
    display_name: str

    @classmethod
    def from_backend_user(cls, payload: dict[str, object]) -> "AuthenticatedCustomer":
        user_id_value = payload.get("userId") or payload.get("user_id")
        display_name = payload.get("displayName") or payload.get("display_name")
        if user_id_value is None or display_name is None:
            raise ValueError("Missing user identity data")

        user_id = UUID(str(user_id_value))
        return cls(
            user_id=user_id,
            customer_id=user_id,
            display_name=str(display_name),
        )

    def model_post_init(self, __context: object) -> None:
        if self.customer_id == UUID(int=0):
            self.customer_id = self.user_id
