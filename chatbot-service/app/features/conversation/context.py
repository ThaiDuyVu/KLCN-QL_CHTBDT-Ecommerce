"""Model context is completed adjacent turns, while raw audit history stays untouched."""
from app.features.conversation.models import SenderType


def completed_messages(history, window):
    completed = []
    pending = None
    for message in history:
        if message.sender_type == SenderType.USER:
            pending = message  # Earlier consecutive USER is an uncompleted audit attempt.
        elif message.sender_type == SenderType.ASSISTANT:
            if pending is not None:
                completed.extend((pending, message))
            pending = None
    # Do not split a completed pair at the context-window boundary.
    limit = max(0, min(window, 100)) // 2 * 2
    return completed[-limit:] if limit else []
