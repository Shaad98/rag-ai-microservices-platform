from pathlib import Path

from pydantic_settings import BaseSettings, SettingsConfigDict


PROJECT_ROOT = Path(__file__).resolve().parents[3]


class Settings(BaseSettings):
    jwt_public_key_path: Path = PROJECT_ROOT / "keys" / "public.pem"
    jwt_algorithm: str = "RS256"

    eureka_server: str = "http://localhost:8761/eureka"
    eureka_app_name: str = "AI-SERVICE"
    eureka_instance_port: int = 8000

    model_config = SettingsConfigDict(
        env_prefix="",
        extra="ignore",
    )


settings = Settings()