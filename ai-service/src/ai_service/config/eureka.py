import py_eureka_client.eureka_client as eureka_client

from ai_service.config.settings import settings


def register_with_eureka() -> None:
    eureka_client.init(
        eureka_server=settings.eureka_server,
        app_name=settings.eureka_app_name,
        instance_port=settings.eureka_instance_port,
    )