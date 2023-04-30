import logging.config
import os
import random
import uuid
from datetime import datetime, timedelta
from typing import Optional, Tuple

import jwt
from fastapi import Depends, FastAPI, Header, HTTPException, Query
from fastapi.openapi.utils import get_openapi
from fastapi.responses import FileResponse, HTMLResponse
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from jwt.exceptions import InvalidTokenError
from tinydb import TinyDB, table
from tinydb.table import Table

from app import VERSION
from app.email_sender import EmailSenderFactory
from app.models import (
    CreateRecord,
    EmailInfo,
    EmailStatus,
    OauthToken,
    Record,
    RecordUpdate,
    RecordUpdateResponse,
    RecordWrapper,
)

# Define path for different endpoints
PDRCASE_OAUTH2_ENDPOINT = os.environ["PDRCASE_OAUTH2_ENDPOINT"]
PDRCASE_GET_ENDPOINT = os.environ["PDRCASE_GET_ENDPOINT"]
PDRCASE_CREATE_ENDPOINT = os.environ["PDRCASE_CREATE_ENDPOINT"]
PDRCASE_UPDATE_ENDPOINT = os.environ["PDRCASE_UPDATE_ENDPOINT"]
PDRCASE_EMAIL_ENDPOINT = os.environ["PDRCASE_EMAIL_ENDPOINT"]
PDRCASE_TEST_ENDPOINT = os.environ["PDRCASE_TEST_ENDPOINT"]

# Get uvicorn logger
logger = logging.getLogger("uvicorn")

from fastapi.openapi.docs import (
    get_redoc_html,
    get_swagger_ui_html,
    get_swagger_ui_oauth2_redirect_html,
)
from fastapi.staticfiles import StaticFiles

app = FastAPI(title="OAR Customer Service API", docs_url=None, redoc_url=None)


def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
    openapi_schema = get_openapi(
        title="OAR Customer Service API",
        version=VERSION,
        description="This is a reference customers service API implementation using FastAPI. It provides several endpoints for creating, retrieving, updating, and deleting records, as well as sending emails. The goal of this app is to serve as a replacement of the Salesforce backend for the RPA Request Handler, in case of any sort of technical issues. The app uses TinyDB as a database.",
        routes=app.routes,
    )
    openapi_schema["info"]["x-logo"] = {
        "url": "https://fastapi.tiangolo.com/img/logo-margin/logo-teal.png"
    }
    # Modify the schema as needed
    app.openapi_schema = openapi_schema
    return app.openapi_schema


app.openapi = custom_openapi

app.mount("/static", StaticFiles(directory="static"), name="static")


@app.get("/docs", include_in_schema=False)
async def custom_swagger_ui_html():
    return get_swagger_ui_html(
        openapi_url=app.openapi_url,
        title=app.title + " - Swagger UI",
        oauth2_redirect_url=app.swagger_ui_oauth2_redirect_url,
        swagger_js_url="/static/swagger-ui-bundle.js",
        swagger_css_url="/static/swagger-ui.css",
        swagger_favicon_url="/static/customer-service.png",
    )


@app.get(app.swagger_ui_oauth2_redirect_url, include_in_schema=False)
async def swagger_ui_redirect():
    return get_swagger_ui_oauth2_redirect_html()


@app.get("/redoc", include_in_schema=False)
async def redoc_html():
    return get_redoc_html(
        openapi_url=app.openapi_url,
        title=app.title + " - ReDoc",
        redoc_js_url="/static/redoc.standalone.js",
        redoc_favicon_url="/static/customer-service.png",
    )


favicon_path = "static/favicon.ico"


@app.get("/favicon.ico", include_in_schema=False)
async def favicon():
    return FileResponse(favicon_path)


def get_db() -> TinyDB:
    """
    Returns an instance of TinyDB for storing records.

    Returns:
    - TinyDB: An instance of TinyDB for storing records.
    """
    records_db = TinyDB(
        "app/db.json",
        sort_keys=False,
        indent=4,
        separators=(",", ": "),
    )
    records_db.default_table_name = "records"
    try:
        yield records_db
    finally:
        records_db.close()


def get_public_keys_db() -> TinyDB:
    """
    Returns an instance of TinyDB for storing public keys.

    Returns:
    - TinyDB: An instance of TinyDB for storing public keys.
    """
    # root_dir = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    # public_keys_file = os.path.join(root_dir, "keys", "public_keys.json")
    public_keys_db = TinyDB(
        "keys/public_keys.json",
        sort_keys=False,
        indent=4,
        separators=(",", ": "),
    )
    public_keys_db.default_table_name = "public_keys"
    try:
        yield public_keys_db
    finally:
        public_keys_db.close()


# Set table ID type (default int)
Table.document_id_class = str


def is_valid_oauth2_token(token: str) -> bool:
    """
    Checks if the given OAuth2 token is valid.

    Args:
    - token (str): The OAuth2 token to check.

    Returns:
    - bool: True if the token is valid, False otherwise.
    """
    try:
        # Load the secret key and expected audience from environment variables
        secret_key = os.environ["SECRET_KEY"]
        expected_audience = os.environ["AUDIENCE"]
        # Decode the JWT token and verify the signature
        jwt.decode(token, secret_key, algorithms=["HS256"], audience=expected_audience)
        # Return True if the token is valid
        return True

    except Exception as e:
        logger.error(e)
        # Return False if invalid
        return False


def get_oauth2_token(header: str) -> Optional[str]:
    """
    Extract the OAuth2 token from the "Authorization" header.

    Args:
    - header (str): The "Authorization" header value.

    Returns:
    - str: The OAuth2 token if it is present in the header, else None.
    """
    if not header:
        return None

    parts = header.split()
    if len(parts) == 2 and parts[0].lower() == "bearer":
        return parts[1]
    else:
        return None


###########################################
##                                       ##
##             API ENDPOINTS             ##
##                                       ##
###########################################


class TokenStore:
    """
    A class for storing OAuth2 access tokens.

    Attributes:
    - tokens (dict): A dictionary that maps usernames to access tokens.

    Methods:
    - get_token(username): Returns the access token for the given username, or None if not found.
    - set_token(username, token, expiration_time): Stores the access token and its expiration time for the given username.
    """

    def __init__(self):
        self.tokens = {}

    def get_token(self, username):
        """
        Returns the access token for the given username, or None if not found.

        Args:
        - username (str): The username for which to get the access token.

        Returns:
        - str or None: The access token for the given username, or None if not found.
        """
        existing_token = self.tokens.get(username)
        return existing_token if existing_token else None

    def set_token(self, username, token, expiration_time):
        """
        Stores the access token and its expiration time for the given username.

        Args:
        - username (str): The username for which to store the access token.
        - token (str): The access token to store.
        - expiration_time (datetime): The expiration time of the access token.
        """
        self.tokens[username] = {
            "access_token": token,
            "expiration_time": expiration_time,
        }


# TokenStore instance to hold the access tokens in memory
token_store = TokenStore()


def get_decoded_payload(assertion: str, db: TinyDB) -> dict:
    """
    Decodes the given JWT assertion and verifies its signature using the public key stored in the database.

    Args:
    - assertion (str): The JWT assertion to decode.
    - db (TinyDB): The TinyDB instance used to access the public keys database.

    Returns:
    - dict: The decoded JWT payload as a dictionary.

    Raises:
    - HTTPException: If the client_id in the JWT assertion is not found in the public keys database.

    """
    # Decode the JWT assertion without verifying the signature
    payload = jwt.decode(assertion, options={"verify_signature": False})
    # Extract client_id from the payload
    client_id = payload["iss"]
    # Get the public key for the client_id from the database
    table = db.table("public_keys")
    public_key_record = table.get(doc_id=client_id)

    # Raise an HTTPException if public key is not found
    if not public_key_record:
        raise HTTPException(
            status_code=400,
            detail={"error": "client_id not found in public keys"},
        )

    # Get the public key PEM from the record
    public_key_pem = public_key_record["public_key"]
    # Load the expected audience from an environment variable
    expected_audience = os.environ["AUDIENCE"]

    # Decode the JWT assertion using the public key and verify the signature
    # The expected_audience parameter is used to verify that the JWT token was intended for the correct audience
    payload = jwt.decode(
        assertion,
        public_key_pem.encode("utf-8"),
        algorithms=["RS256"],
        audience=expected_audience,
    )
    return payload


def generate_access_token(payload: dict) -> Tuple[str, str]:
    """
    Generates an access token for the given JWT payload.
    Checks if a token already exists token and not expired.

    Args:
    - payload (dict): The JWT payload as a dictionary.

    Returns:
    - dict: A dictionary containing the access token and the instance URL.
    """
    # Extract client_id, username, audience, and expiration time
    client_id = payload["iss"]
    username = payload["sub"]
    audience = payload["aud"]
    expiration_time = payload["exp"]  # in minutes

    # Check if token is already stored for username
    existing_token = token_store.get_token(username)

    # If stored token is found and has not expired, return it
    if (
        existing_token
        and datetime.fromtimestamp(existing_token["expiration_time"])
        >= datetime.utcnow()
    ):
        instance_url = os.environ["INSTANCE_URL"]
        return (existing_token["access_token"], instance_url)
    else:
        # If stored token is not found or has expired, generate a new token
        exp_time = int(
            (datetime.utcnow() + timedelta(minutes=expiration_time)).timestamp()
        )

        # Set JWT payload
        payload = {
            "sub": username,
            "aud": audience,
            "iss": client_id,
            "exp": exp_time,
        }

        # Generate JWT token
        secret_key = os.environ["SECRET_KEY"]
        token = jwt.encode(payload, secret_key)

        # Set instance URL
        instance_url = os.environ["INSTANCE_URL"]

        # Store token and expiration time
        token_store.set_token(username, token, exp_time)

        return (token, instance_url)


# Get access token endpoint
@app.post(
    f"{PDRCASE_OAUTH2_ENDPOINT}",
    response_model=OauthToken,
    status_code=200,
    tags=["Auth"],
)
def get_token(
    grant_type: str = Query(..., description="The grant type."),
    assertion: str = Query(..., description="The JWT assertion."),
    db: TinyDB = Depends(get_public_keys_db),
):
    logger.info("Getting access token")
    if grant_type == "urn:ietf:params:oauth:grant-type:jwt-bearer":
        # Extract payload from assertion
        try:
            payload = get_decoded_payload(assertion, db)
        except (InvalidTokenError, ValueError) as e:
            raise HTTPException(status_code=400, detail={"error": str(e)})

        # Generate token
        access_token, instance_url = generate_access_token(payload)
        return OauthToken(access_token=access_token, instance_url=instance_url)

    else:
        raise HTTPException(status_code=400, detail={"error": "unsupported_grant_type"})


# Get record endpoint
@app.get(
    f"{PDRCASE_GET_ENDPOINT}/{{id}}",
    response_model=RecordWrapper,
    status_code=200,
    tags=["Record"],
)
async def get_record(
    id: str, Authorization: str = Header(None), db: TinyDB = Depends(get_db)
):
    """
    Retrieve a record by its ID.

    Args:
    - id (str): The ID of the record to retrieve.
    - Authorization (str, optional): A bearer token for authentication. Defaults to None.
    - db (TinyDB, optional): A dependency that provides a database connection. Defaults to Depends(get_db).

    Returns:
    - dict: A dictionary containing a single key-value pair, where the key is "record" and the value is a Record object.

    Raises:
    - HTTPException: If the bearer token is missing or invalid, or if the record is not found.
    ```
    """

    logger.info(f"Getting record with ID = {id}")
    token = get_oauth2_token(Authorization)
    # Check if token is missing in the header
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_valid_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")

    # Get records table
    records_table = db.table("records")

    # Retrieve the record from the "records" table
    record_data = records_table.get(doc_id=id)

    if record_data is None:
        # If the record is not found, raise an HTTPException with status code 404
        raise HTTPException(status_code=404, detail="Record not found")

    # Instantiate the Record object
    record = Record(**record_data)

    # Instantiate the RecordWrapper object
    return RecordWrapper(record=record)


# Create Record endpoint
@app.post(
    PDRCASE_CREATE_ENDPOINT,
    response_model=RecordWrapper,
    status_code=200,
    tags=["Record"],
)
async def create_record(
    data: CreateRecord,
    Authorization: str = Header(None),
    db: TinyDB = Depends(get_db),
):
    """
    Create a new record in the database.

    Args:
    - data (CreateRecord): A CreateRecord object containing the data for the new record.
    - token (str): A bearer token for authentication.

    Returns:
    - dict: A dictionary containing a single key-value pair, where the key is "record" and the value is a Record object.

    Raises:
    - HTTPException: If the token is missing or invalid, or if there is an error inserting the record into the database.
    """
    logger.info(f"Creating a new record")
    # Check if token is missing in the header
    token = get_oauth2_token(Authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_valid_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")
    # Generate a unique ID and case number for the new record
    record = Record(
        id=str(uuid.uuid4()),
        caseNum=str(random.randint(100000, 999999)),
        userInfo=data.userInfo,
    )
    # Get records tables
    records_table = db.table("records")
    # Insert the new record into the database
    try:
        records_table.insert(table.Document(record.dict(), doc_id=record.id))
    except Exception as e:
        # If there is an error inserting the record, raise an HTTPException with status code 500
        raise HTTPException(status_code=500, detail="Error creating record" + str(e))

    # Create a RecordWrapper object and return it
    return RecordWrapper(record=record)


# Update Record endpoint
@app.patch(
    f"{PDRCASE_UPDATE_ENDPOINT}/{{id}}",
    response_model=RecordUpdateResponse,
    status_code=200,
    tags=["Record"],
)
async def update_record(
    id: str,
    update: RecordUpdate,
    Authorization: str = Header(...),
    db: TinyDB = Depends(get_db),
):
    """
    Update the approval status of a record.

    Args:
    - id (str): The ID of the record to update.
    - update (RecordUpdate): A RecordUpdate object containing the new approval status.
    - token (str): A bearer token for authentication.

    Returns:
    - dict: A dictionary containing two key-value pair, one pair for recordId, the second for approvalStatus.

    Raises:
    - HTTPException: If the token is missing or invalid, if the record is not found, or if there is an error updating the record in the database.
    """
    logger.info(f"Updating record with ID = {id}")
    # Check if token is missing in the header
    token = get_oauth2_token(Authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_valid_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")

    # Get the records table
    records_table = db.table("records")
    # Retrieve the record from the "records" table
    record = records_table.get(doc_id=id)
    if record is None:
        # If the record is not found, raise an HTTPException with status code 404
        raise HTTPException(status_code=404, detail="Record not found")

    # Update the approval status of the record in the database
    try:
        # Update the Approval Status of the record
        # db.update({"userInfo": {"approvalStatus": update.Approval_Status__c}}, Query().id == id)
        # Update the approval status in the user info section of the record
        record["userInfo"]["approvalStatus"] = update.Approval_Status__c

        # Update the record in the database
        db.update(record, doc_ids=[id])
    except Exception as e:
        # If there is an error updating the record, raise an HTTPException with status code 500
        raise HTTPException(status_code=500, detail="Error updating record")
    # Update the approval status of the record in the dictionary
    updated_record = records_table.get(doc_id=id)

    # Return a dictionary with the record ID and the new approval status
    return {
        "recordId": updated_record["id"],
        "approvalStatus": updated_record["userInfo"]["approvalStatus"],
    }


# Send email endpoint
@app.post(
    PDRCASE_EMAIL_ENDPOINT, response_model=EmailStatus, status_code=200, tags=["Record"]
)
async def send_email(
    email_info: EmailInfo,
    Authorization: str = Header(...),
    db: TinyDB = Depends(get_db),
):
    """
    Send an email with the provided email information.

    Args:
    - email_info (EmailInfo): An EmailInfo object containing the email information.
    - token (str): A bearer token for authentication.

    Returns:
    - EmailStatus: An EmailStatus object containing the email information and a timestamp.

    Raises:
    - HTTPException: If the token is missing or invalid.
    """
    logger.info(
        f"Sending email to '{email_info.recipient}' with subject '{email_info.subject}'"
    )
    # Check if token is missing in the header
    token = get_oauth2_token(Authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_valid_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")

    # Read the sender email and password from the .env file
    sender_email = os.environ["SENDER_EMAIL"]
    sender_password = os.environ["SENDER_PASSWORD"]
    # Set up the email content and headers
    recipient_email = email_info.recipient
    subject = email_info.subject
    content = email_info.content

    # Get email sender from factory
    email_sender = EmailSenderFactory.get_email_sender("smtp")

    # Send the email
    status_code, message = email_sender.send_email(
        sender_email, sender_password, recipient_email, subject, content
    )

    if status_code == 200:
        # Return an EmailStatus object with the email information and timestamp
        timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
        return EmailStatus(email_info=email_info, timestamp=timestamp)
    else:
        raise HTTPException(status_code=500, detail=message)


# Test API endpoint
@app.get(PDRCASE_TEST_ENDPOINT, status_code=200, tags=["Test"])
async def test_endpoint(Authorization: str = Header(...)):
    """
    Check if the service is running.
    """
    logger.info(f"Checking API status")
    token = get_oauth2_token(Authorization)
    # Check if token is missing in the header
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_valid_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")

    return "Service is up and running."
