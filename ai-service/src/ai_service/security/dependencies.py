from typing import Annotated

from fastapi import Depends, HTTPException, status
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer

from ai_service.security.jwt_service import (
    AuthenticatedUser,
    JwtAuthenticationException,
    JwtService,
)


bearer_scheme = HTTPBearer(
    auto_error=False
)

jwt_service = JwtService()


def get_current_user(
    credentials: Annotated[
        HTTPAuthorizationCredentials | None,
        Depends(bearer_scheme),
    ],
) -> AuthenticatedUser:

    # =========================================
    # NO AUTHORIZATION HEADER
    # =========================================

    if credentials is None:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Authentication is required",
            headers={
                "WWW-Authenticate": "Bearer",
            },
        )

    # =========================================
    # INVALID AUTHORIZATION SCHEME
    # =========================================

    if credentials.scheme.lower() != "bearer":
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid Authorization header",
            headers={
                "WWW-Authenticate": "Bearer",
            },
        )

    token = credentials.credentials.strip()

    # =========================================
    # EMPTY TOKEN
    # =========================================

    if not token:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Bearer token is missing",
            headers={
                "WWW-Authenticate": "Bearer",
            },
        )

    # =========================================
    # PARSE + VALIDATE JWT
    # =========================================

    try:
        claims = jwt_service.extract_all_claims(token)

        user_id = jwt_service.extract_user_id(
            claims
        )

        role = jwt_service.extract_role(
            claims
        )

        email = jwt_service.extract_email(
            claims
        )

        return AuthenticatedUser(
            user_id=user_id,
            role=role,
            email=email,
        )

    except JwtAuthenticationException as exc:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail=str(exc),
            headers={
                "WWW-Authenticate": "Bearer",
            },
        ) from exc