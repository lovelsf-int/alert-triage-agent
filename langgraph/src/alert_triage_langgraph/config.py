from __future__ import annotations

import re
from functools import lru_cache

from pydantic import Field, SecretStr, field_validator
from pydantic_settings import BaseSettings, SettingsConfigDict

from .errors import ConfigurationError

_ALLOWED_EMBEDDING_DIMENSIONS = {64, 128, 256, 512, 768, 1024, 1536, 2048}
_IDENTIFIER = re.compile(r"^[A-Za-z_][A-Za-z0-9_]*$")


class Settings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=".env",
        env_file_encoding="utf-8",
        case_sensitive=False,
        extra="ignore",
    )

    app_name: str = "alert-triage-langgraph"
    server_host: str = "0.0.0.0"
    server_port: int = Field(default=8081, ge=1, le=65535)
    log_level: str = "INFO"

    database_url: str = "postgresql://alert_agent:alert_agent@localhost:5432/alert_agent"
    db_min_pool_size: int = Field(default=1, ge=1, le=50)
    db_max_pool_size: int = Field(default=10, ge=1, le=100)
    knowledge_table: str = "langgraph_alert_knowledge"

    deepseek_api_key: SecretStr = SecretStr("")
    deepseek_api_base: str = "https://api.deepseek.com/v1"
    deepseek_model: str = "deepseek-v4-flash"
    deepseek_temperature: float = Field(default=0.1, ge=0.0, le=2.0)
    deepseek_max_tokens: int = Field(default=2500, ge=256, le=384000)

    dashscope_api_key: SecretStr = SecretStr("")
    dashscope_embedding_url: str = (
        "https://dashscope.aliyuncs.com/api/v1/services/embeddings/"
        "text-embedding/text-embedding"
    )
    embedding_model: str = "text-embedding-v4"
    embedding_dimensions: int = 1024
    embedding_batch_size: int = Field(default=10, ge=1, le=10)
    embedding_timeout_seconds: float = Field(default=45.0, gt=0.0, le=300.0)

    rag_similarity_threshold: float = Field(default=0.35, ge=-1.0, le=1.0)
    max_historical_cases: int = Field(default=3, ge=1, le=50)
    max_policy_rules: int = Field(default=3, ge=1, le=50)
    human_review_threshold: float = Field(default=0.85, ge=0.0, le=1.0)
    rag_seed_on_startup: bool = True

    @field_validator("embedding_dimensions")
    @classmethod
    def validate_embedding_dimensions(cls, value: int) -> int:
        if value not in _ALLOWED_EMBEDDING_DIMENSIONS:
            allowed = ", ".join(str(item) for item in sorted(_ALLOWED_EMBEDDING_DIMENSIONS))
            raise ValueError(f"embedding_dimensions must be one of: {allowed}")
        return value

    @field_validator("knowledge_table")
    @classmethod
    def validate_table_name(cls, value: str) -> str:
        if not _IDENTIFIER.fullmatch(value):
            raise ValueError("knowledge_table must be a simple PostgreSQL identifier")
        return value

    @field_validator("deepseek_api_base", "dashscope_embedding_url")
    @classmethod
    def strip_url_suffix(cls, value: str) -> str:
        return value.rstrip("/")

    def validate_runtime_secrets(self) -> None:
        missing: list[str] = []
        if not self.deepseek_api_key.get_secret_value():
            missing.append("DEEPSEEK_API_KEY")
        if not self.dashscope_api_key.get_secret_value():
            missing.append("DASHSCOPE_API_KEY")
        if missing:
            raise ConfigurationError(
                "Missing required environment variables: " + ", ".join(missing)
            )


@lru_cache(maxsize=1)
def get_settings() -> Settings:
    return Settings()
