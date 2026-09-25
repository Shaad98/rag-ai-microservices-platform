from dataclasses import dataclass

import jwt
from jwt import InvalidTokenError

from ai_service.security.exceptions import JwtAuthenticationException

from ai_service.config.settings import settings


# class JwtAuthenticationException(Exception):
#     """Raised when JWT authentication fails."""


@dataclass(frozen=True)
class AuthenticatedUser:
    user_id: str
    role: str
    email: str | None = None


class JwtService:

    def __init__(self) -> None:
        self.public_key = self._load_public_key(
            settings.jwt_public_key_path
        )

    @staticmethod
    def _load_public_key(path) -> str:
        try:
            return path.read_text(encoding="utf-8")
        except OSError as exc:
            raise RuntimeError(
                f"Unable to read JWT public key: {path}"
            ) from exc

    def extract_all_claims(
        self,
        token: str,
    ) -> dict:
        try:
            return jwt.decode(
                token,
                self.public_key,
                algorithms=[settings.jwt_algorithm],
                options={
                    "require": [
                        "sub",
                        "exp",
                        "role",
                    ],
                },
            )

        except InvalidTokenError as exc:
            raise JwtAuthenticationException(
                "Invalid or expired JWT token"
            ) from exc

    def extract_user_id(
        self,
        claims: dict,
    ) -> str:
        user_id = claims.get("sub")

        if not isinstance(user_id, str) or not user_id.strip():
            raise JwtAuthenticationException(
                "JWT user ID is missing"
            )

        return user_id

    def extract_role(
        self,
        claims: dict,
    ) -> str:
        role = claims.get("role")

        if not isinstance(role, str) or not role.strip():
            raise JwtAuthenticationException(
                "JWT role is missing"
            )

        return role

    def extract_email(
        self,
        claims: dict,
    ) -> str | None:
        email = claims.get("email")

        if email is None:
            return None

        if not isinstance(email, str):
            raise JwtAuthenticationException(
                "JWT email is invalid"
            )

        return email