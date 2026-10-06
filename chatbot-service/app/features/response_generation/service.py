from app.shared.contracts.context import GroundedContext
from app.shared.contracts.enums import Intent


class MockResponseGenerationService:
    def generate(self, context: GroundedContext) -> str:
        if context.query_plan.intent == Intent.GREETING:
            return (
                "Xin chào! Mình có thể hỗ trợ bạn "
                "tìm và tư vấn thiết bị điện tử."
            )

        if context.query_plan.intent in {
            Intent.PRODUCT_DISCOVERY, Intent.PRODUCT_DETAIL, Intent.PRODUCT_COMPARE
        }:
            if context.products:
                if context.query_plan.intent == Intent.PRODUCT_COMPARE:
                    return "Mình tìm thấy các sản phẩm để bạn so sánh."
                return (
                    "Mình tìm thấy một sản phẩm "
                    "phù hợp với nhu cầu của bạn."
                )

            return "Mình chưa tìm thấy sản phẩm phù hợp."

        return "Mình chưa hiểu yêu cầu của bạn."
