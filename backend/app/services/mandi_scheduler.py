"""
Automated Mandi Price Background Scheduler for Bol Kisan.
Runs periodic data synchronization approximately every 12 hours.
Guarantees duplicate-job protection with a single-instance mutex lock.
Integrates with FastAPI lifespan startup and shutdown events.
"""

import asyncio
import logging
from typing import Optional, Dict, Any
from app.services.mandi_price_service import mandi_price_service

logger = logging.getLogger(__name__)

# Default 12-hour synchronization interval (43200 seconds)
DEFAULT_SYNC_INTERVAL_SECONDS = 12 * 3600


class MandiScheduler:
    """
    Background scheduler for automated AGMARKNET / data.gov.in Mandi price synchronizations.
    Features:
    - Runs approximately every 12 hours.
    - Single-instance mutex lock to strictly prevent concurrent or overlapping sync jobs.
    - Graceful cancellation on app shutdown.
    - FastAPI lifespan integration.
    """

    def __init__(self, interval_seconds: int = DEFAULT_SYNC_INTERVAL_SECONDS):
        self.interval_seconds = interval_seconds
        self._task: Optional[asyncio.Task] = None
        self._lock = asyncio.Lock()
        self._is_running = False

    @property
    def is_running(self) -> bool:
        return self._is_running

    @property
    def is_locked(self) -> bool:
        return self._lock.locked()

    async def start(self):
        """Starts the background scheduler loop if not already running."""
        if self._is_running:
            logger.info("MandiScheduler is already running.")
            return

        self._is_running = True
        self._task = asyncio.create_task(self._run_loop(), name="bolkisan_mandi_sync_scheduler")
        logger.info(
            "MandiScheduler started. Interval: %d seconds (~12 hours).",
            self.interval_seconds
        )

    async def stop(self):
        """Stops the background scheduler loop gracefully."""
        if not self._is_running:
            return

        self._is_running = False
        if self._task and not self._task.done():
            self._task.cancel()
            try:
                await self._task
            except asyncio.CancelledError:
                pass
        logger.info("MandiScheduler stopped successfully.")

    async def trigger_sync(self, sync_type: str = "scheduled") -> Dict[str, Any]:
        """
        Triggers a sync execution protected by the single-instance mutex lock.
        If another sync is currently running, skips execution and returns status.
        """
        if self._lock.locked():
            logger.warning("Mandi sync is already in progress. Skipping duplicate execution.")
            return {
                "status": "SKIPPED",
                "message": "A Mandi price sync job is currently in progress. Duplicate execution prevented."
            }

        async with self._lock:
            logger.info("Triggering %s Mandi price sync job...", sync_type)
            result = await mandi_price_service.sync_mandi_prices(sync_type=sync_type)
            logger.info("Completed %s Mandi price sync job: status=%s", sync_type, result.get("status"))
            return result

    async def _run_loop(self):
        """Main loop that fires every interval_seconds."""
        # Allow initial app bootstrap to finish before running first scheduled sync
        try:
            await asyncio.sleep(3)
            logger.info("MandiScheduler executing initial startup sync verification...")
            await self.trigger_sync(sync_type="scheduled")
        except asyncio.CancelledError:
            return
        except Exception as err:
            logger.warning("Initial startup sync encountered error: %s", err)

        while self._is_running:
            try:
                await asyncio.sleep(self.interval_seconds)
                if self._is_running:
                    await self.trigger_sync(sync_type="scheduled")
            except asyncio.CancelledError:
                break
            except Exception as err:
                logger.error("Error in MandiScheduler loop: %s", err, exc_info=True)
                # Sleep briefly before retrying to prevent busy loop on unexpected exceptions
                await asyncio.sleep(60)


# Singleton instance
mandi_scheduler = MandiScheduler()
