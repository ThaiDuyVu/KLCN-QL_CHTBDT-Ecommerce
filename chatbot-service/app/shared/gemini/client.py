"""Gemini REST adapter. Credentials stay in request headers, never URLs or traces."""
import httpx

from app.shared.ollama.client import OllamaError
from app.shared.tracing import event, traced


class GeminiError(OllamaError):
    """Provider failure with a safe message; never includes upstream body/headers."""


class GeminiClient:
    def __init__(self, settings, *, client=None):
        self.settings = settings
        self._owned = client is None
        self.client = client if client is not None else httpx.Client(timeout=settings.gemini_timeout_seconds)

    def close(self):
        if self._owned:
            self.client.close()

    @traced('gemini.chat', ('messages', 'schema'))
    def chat(self, messages, *, schema=None):
        key = self.settings.gemini_api_key.get_secret_value()
        if not self.settings.gemini_enabled or not key:
            raise GeminiError('Gemini chưa được bật hoặc chưa có API key')
        contents, instructions = [], []
        for message in messages:
            role, content = message['role'], message['content']
            if role == 'system':
                instructions.append({'text': content})
            elif role in {'user', 'assistant'}:
                contents.append({'role': 'model' if role == 'assistant' else 'user', 'parts': [{'text': content}]})
            else:
                raise GeminiError('Unsupported Gemini message role')
        config = {'temperature': 0, 'maxOutputTokens': self.settings.gemini_max_output_tokens}
        if schema is not None:
            config.update(responseMimeType='application/json', responseJsonSchema=schema)
        payload = {'contents': contents, 'generationConfig': config}
        if instructions:
            payload['systemInstruction'] = {'parts': instructions}
        if self.settings.gemini_model.startswith('gemini-2.5-'):
            config['thinkingConfig'] = {'thinkingBudget': 0}
        url = f'https://generativelanguage.googleapis.com/v1beta/models/{self.settings.gemini_model}:generateContent'
        try:
            response = self.client.post(url, headers={'x-goog-api-key': key}, json=payload,
                                        timeout=self.settings.gemini_timeout_seconds)
        except httpx.TimeoutException:
            raise GeminiError('Gemini timed out') from None
        except httpx.RequestError:
            raise GeminiError('Gemini connection failed') from None
        event('gemini.http_status', {'model': self.settings.gemini_model, 'status_code': response.status_code})
        if not response.is_success:
            guidance = {
                400: 'Yêu cầu Gemini không hợp lệ; cần kiểm tra cấu hình và dữ liệu gửi.',
                401: 'Gemini từ chối xác thực; cần kiểm tra API key.',
                403: 'Gemini từ chối quyền truy cập; cần kiểm tra quyền của key/project.',
                404: 'Không truy cập được model Gemini đã cấu hình; cần kiểm tra tên model và quyền truy cập.',
                429: 'Gemini giới hạn lượt gọi hoặc quota; kiểm tra hạn mức trong Google AI Studio.',
                500: 'Máy chủ Gemini gặp lỗi; vui lòng thử lại sau.',
                502: 'Dịch vụ Gemini tạm thời không khả dụng; vui lòng thử lại sau.',
                503: 'Dịch vụ Gemini tạm thời không khả dụng (có thể quá tải). Vui lòng thử lại sau; mã này chưa chứng minh key sai hoặc hết quota.',
                504: 'Máy chủ Gemini xử lý quá thời gian; vui lòng thử lại sau.',
            }.get(response.status_code, 'Gemini từ chối yêu cầu; cần kiểm tra mã lỗi upstream.')
            raise GeminiError(f'Gemini HTTP {response.status_code}. {guidance}')
        try:
            data = response.json()
            candidate = data['candidates'][0]
            if candidate.get('finishReason') != 'STOP':
                raise GeminiError('Gemini response incomplete or blocked')
            text = ''.join(p.get('text', '') for p in candidate['content']['parts'] if not p.get('thought'))
            if not text.strip():
                raise GeminiError('Gemini returned no text')
            usage = data.get('usageMetadata', {})
        except (ValueError, KeyError, IndexError, TypeError, AttributeError):
            raise GeminiError('Gemini returned an invalid response') from None
        if not isinstance(usage, dict):
            usage = {}
        event('gemini.usage', {'model': self.settings.gemini_model,
                             **{k: usage[k] for k in ('promptTokenCount', 'candidatesTokenCount', 'totalTokenCount', 'thoughtsTokenCount') if k in usage}})
        return text.strip()
