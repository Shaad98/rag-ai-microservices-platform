import asyncio
from contextlib import asynccontextmanager

from fastapi import Depends, FastAPI

from ai_service.config.eureka import register_with_eureka
from ai_service.security.dependencies import get_current_user
from ai_service.security.jwt_service import AuthenticatedUser


@asynccontextmanager
async def lifespan(app: FastAPI):

    # Application startup
    await asyncio.to_thread(register_with_eureka)

    yield

    # Application shutdown
    # Eureka cleanup will be added here.


app = FastAPI(
    title="ShaadRAG AI Service",
    lifespan=lifespan,
)


@app.get("/ai/actuator/health")
def health():
    return {
        "status": "UP"
    }


@app.get("/ai/security-test")
def security_test(
    current_user: AuthenticatedUser = Depends(
        get_current_user
    ),
):
    return {
        "message": "JWT authentication successful",
        "userId": current_user.user_id,
        "email": current_user.email,
        "role": current_user.role,
    }