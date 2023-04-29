from dotenv import load_dotenv
import os

load_dotenv()

VERSION = os.environ.get("VERSION", "1.1.0")
