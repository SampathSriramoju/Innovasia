import sqlalchemy as sa
from app.core.config import settings

engine = sa.create_engine(settings.SYNC_DATABASE_URL)
with engine.connect() as conn:
    exts = [r[0] for r in conn.execute(sa.text("SELECT name FROM pg_available_extensions WHERE name = 'vector';")).fetchall()]
    print("Vector in pg_available_extensions:", exts)
    if exts:
        try:
            conn.execute(sa.text("CREATE EXTENSION IF NOT EXISTS vector;"))
            conn.commit()
            print("Successfully enabled pgvector extension!")
        except Exception as e:
            print("Error enabling pgvector:", e)
    else:
        print("Vector extension not installed in PostgreSQL instance.")
