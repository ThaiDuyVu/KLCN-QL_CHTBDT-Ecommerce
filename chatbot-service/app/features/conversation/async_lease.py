"""Wait for in-flight blocking work before releasing a session lease on cancellation."""
import asyncio
from contextlib import asynccontextmanager


async def settle(task):
    """Resource cleanup must finish even if the caller is cancelled more than once."""
    while not task.done():
        try:
            await asyncio.shield(task)
        except asyncio.CancelledError:
            continue
    return task.result()


async def blocking(call, *args, **kwargs):
    task = asyncio.create_task(asyncio.to_thread(call, *args, **kwargs))
    try:
        return await asyncio.shield(task)
    except asyncio.CancelledError:
        try:
            await settle(task)
        except Exception:
            pass  # Preserve cancellation; do not stringify transport/DB exceptions.
        raise


@asynccontextmanager
async def request_lease(repository, session_id, customer_id):
    lease = repository.request_lock(session_id, customer_id)
    entering = asyncio.create_task(asyncio.to_thread(lease.__enter__))
    try:
        await asyncio.shield(entering)
    except asyncio.CancelledError:
        try:
            await settle(entering)
        except Exception:
            pass
        else:
            await settle(asyncio.create_task(asyncio.to_thread(lease.__exit__, None, None, None)))
        raise
    try:
        yield
    finally:
        await blocking(lease.__exit__, None, None, None)
