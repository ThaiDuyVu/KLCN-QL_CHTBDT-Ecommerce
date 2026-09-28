from app.shared.contracts.enums import Intent, MessageRole, RequestedField

def test_intent_is_string_enum() -> None:
    assert Intent.PRODUCT_DISCOVERY == "PRODUCT_DISCOVERY"
    assert Intent.ORDER_STATUS.value == "ORDER_STATUS"


def test_requested_field_is_string_enum() -> None:
    assert RequestedField.PRICE == "PRICE"
    assert RequestedField.STOCK.value == "STOCK"

def test_message_role_is_string_enum() -> None:
    assert MessageRole.USER == "USER"
    assert MessageRole.ASSISTANT.value == "ASSISTANT"