from typing import Union
from tinydb import TinyDB
from tinydb.storages import MemoryStorage, JSONStorage
from tinydb.middlewares import CachingMiddleware, Middleware
from uuid import UUID


class UUIDMiddleware(Middleware):
    """
    Middleware that automatically generates UUIDs for document IDs.
    """

    def __init__(self, storage_cls):
        # Any middleware *has* to call the super constructor
        # with storage_cls
        super().__init__(storage_cls)
        self.document_id_class = UUID

    # def write(self, table):
    #     def _write(records):
    #         for record in records:
    #             if "_id" not in record:
    #                 record["_id"] = self.document_id_class().hex
    #         return records

    #     return _write


def create_database(file_path: str) -> TinyDB:
    """
    Factory function that creates a new instance of TinyDB database using JSON storage.

    Args:
        file_path (str): The path to the JSON file to use.

    Returns:
        TinyDB: A new instance of TinyDB database.

    Raises:
        ValueError: If `file_path` is not provided.
    """

    if not file_path:
        raise ValueError("File path not provided.")

    return TinyDB(file_path, storage=JSONStorage, middlewares=[UUIDMiddleware])
