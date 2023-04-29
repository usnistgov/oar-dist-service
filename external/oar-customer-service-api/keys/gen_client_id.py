import argparse
import hashlib
from tinydb import TinyDB, Query, table
from tinydb.table import Table

# Define the path to the TinyDB database file
db_path = "keys/public_keys.json"

Table.document_id_class = str


# Define the function to generate a new client ID from a public key
def generate_client_id(public_key):
    # Hash the public key using SHA256
    sha256_hash = hashlib.sha256(public_key.encode("utf-8"))
    hashed_key = sha256_hash.hexdigest()

    # Take the first 16 characters of the hashed key as the client ID
    client_id = hashed_key[:32]

    return client_id


if __name__ == "__main__":
    # Parse the command-line arguments
    parser = argparse.ArgumentParser(
        description="Generate a new client ID for a given public key"
    )
    parser.add_argument(
        "--key",
        dest="public_key_file",
        help="the path to the PEM file containing the public key",
    )

    args, unknown = parser.parse_known_args()

    # Load the public key from the PEM file
    with open(args.public_key_file, "r") as f:
        public_key = f.read()

    # Generate a new client ID from the public key
    client_id = generate_client_id(public_key)

    # Store the public key and client ID in TinyDB
    db = TinyDB(db_path, sort_keys=False, indent=4, separators=(",", ": "))
    PublicKeys = Query()
    public_keys_table = db.table("public_keys")

    # Check if the public key is already in the database
    if not public_keys_table.search(PublicKeys.public_key == public_key):
        # Add the new public key and client ID to the database
        public_keys_table.insert(
            table.Document(
                {"client_id": client_id, "public_key": public_key}, doc_id=client_id
            )
        )
        print(f"New client ID '{client_id}' generated and stored for public key.")
    else:
        # Retrieve the existing client ID from the database
        existing_client_id = public_keys_table.search(
            PublicKeys.public_key == public_key
        )[0]["client_id"]
        print(f"Client ID '{existing_client_id}' already exists for public key.")
