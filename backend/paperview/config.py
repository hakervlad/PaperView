import os
from dataclasses import dataclass, field
from pathlib import Path

@dataclass
class Settings:
    data_dir: Path = field(default_factory=lambda: Path(os.getenv('PAPERVIEW_DATA_DIR', '~/.local/share/paperview')).expanduser())
    app_token: str = field(default_factory=lambda: os.getenv('APP_TOKEN',''))
    llm_key: str = field(default_factory=lambda: os.getenv('LLM_API_KEY',''))
    llm_model: str = field(default_factory=lambda: os.getenv('LLM_MODEL',''))
    llm_base_url: str = field(default_factory=lambda: os.getenv('LLM_BASE_URL','https://api.openai.com/v1'))
    allowed_hosts: tuple = field(default_factory=lambda: tuple(x.strip() for x in os.getenv('LLM_ALLOWED_HOSTS','api.openai.com').split(',') if x.strip()))
    trusted_proxies: tuple = field(default_factory=lambda: tuple(x.strip() for x in os.getenv('TRUSTED_PROXIES','').split(',') if x.strip()))
    openalex_key: str = field(default_factory=lambda: os.getenv('OPENALEX_API_KEY',''))
    max_upload: int = 10 * 1024 * 1024
    max_pages: int = 100
    max_text: int = 120000
    max_chunks: int = 60
    pdf_timeout: int = 15
