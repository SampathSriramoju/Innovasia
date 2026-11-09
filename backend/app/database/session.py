from typing import Generator
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, Session
from app.core.config import settings
from app.database.base import Base

# Support SQLite or PostgreSQL seamlessly
connect_args = {}
if "sqlite" in settings.SYNC_DATABASE_URL:
    connect_args = {"check_same_thread": False}

engine = create_engine(
    settings.SYNC_DATABASE_URL,
    connect_args=connect_args,
    pool_pre_ping=True
)

SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)

def get_db() -> Generator[Session, None, None]:
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()

def init_db():
    from app.database import models  # Ensure all model tables are registered on Base.metadata
    Base.metadata.create_all(bind=engine)
    if "sqlite" in settings.SYNC_DATABASE_URL:
        from sqlalchemy import inspect, text
        inspector = inspect(engine)
        with engine.connect() as conn:
            for table_name, table in Base.metadata.tables.items():
                if inspector.has_table(table_name):
                    existing_cols = {c["name"] for c in inspector.get_columns(table_name)}
                    for col in table.columns:
                        if col.name not in existing_cols:
                            col_type = col.type.compile(engine.dialect)
                            conn.execute(text(f"ALTER TABLE {table_name} ADD COLUMN {col.name} {col_type}"))
            conn.commit()
