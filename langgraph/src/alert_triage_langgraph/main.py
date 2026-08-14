from __future__ import annotations

import logging
from collections.abc import AsyncIterator
from contextlib import asynccontextmanager

import uvicorn
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse

from .api import router
from .config import get_settings
from .errors import (
    AlertTriageError,
    AnalysisNotFoundError,
    ConfigurationError,
    InvalidReviewStateError,
)
from .runtime import ApplicationRuntime


@asynccontextmanager
async def lifespan(app: FastAPI) -> AsyncIterator[None]:
    settings = get_settings()
    logging.basicConfig(
        level=getattr(logging, settings.log_level.upper(), logging.INFO),
        format="%(asctime)s %(levelname)s %(name)s %(message)s",
    )
    runtime = ApplicationRuntime(settings)
    await runtime.start()
    app.state.runtime = runtime
    try:
        yield
    finally:
        await runtime.close()


def create_app() -> FastAPI:
    application = FastAPI(
        title="Alert Triage Agent — LangGraph",
        version="0.1.0",
        lifespan=lifespan,
    )
    application.include_router(router)

    @application.get("/health")
    async def health(request: Request) -> dict[str, str]:
        runtime = getattr(request.app.state, "runtime", None)
        return {"status": "UP" if runtime and runtime.analysis_service else "STARTING"}

    @application.exception_handler(AnalysisNotFoundError)
    async def not_found_handler(_: Request, exc: AnalysisNotFoundError) -> JSONResponse:
        return JSONResponse(status_code=404, content={"error": str(exc)})

    @application.exception_handler(InvalidReviewStateError)
    async def conflict_handler(_: Request, exc: InvalidReviewStateError) -> JSONResponse:
        return JSONResponse(status_code=409, content={"error": str(exc)})

    @application.exception_handler(ConfigurationError)
    async def configuration_handler(_: Request, exc: ConfigurationError) -> JSONResponse:
        return JSONResponse(status_code=503, content={"error": str(exc)})

    @application.exception_handler(AlertTriageError)
    async def application_error_handler(_: Request, exc: AlertTriageError) -> JSONResponse:
        return JSONResponse(status_code=502, content={"error": str(exc)})

    return application


app = create_app()


def run() -> None:
    settings = get_settings()
    uvicorn.run(
        "alert_triage_langgraph.main:app",
        host=settings.server_host,
        port=settings.server_port,
        reload=False,
    )


if __name__ == "__main__":
    run()
