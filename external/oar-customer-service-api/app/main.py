import os
import random
import re
import uuid
from typing import Dict
from typing import Optional

from fastapi import FastAPI, HTTPException, Request, Header, Depends
from fastapi.openapi.utils import get_openapi
from fastapi.responses import HTMLResponse
from fastapi.security import OAuth2PasswordBearer
from fastapi.staticfiles import StaticFiles
from fastapi.templating import Jinja2Templates
from tinydb import TinyDB, Query, table
from tinydb.table import Table

from app.email_sender import EmailSenderFactory
from app.models import CreateRecord, EmailInfo, EmailStatus, Record, RecordUpdate

from app.endpoints import (
    PDRCASE_GET_ENDPOINT,
    PDRCASE_CREATE_ENDPOINT,
    PDRCASE_UPDATE_ENDPOINT,
    PDRCASE_EMAIL_ENDPOINT,
    PDRCASE_TEST_ENDPOINT,
)

app = FastAPI()

# Serve Swagger UI files statically
app.mount("/swagger", StaticFiles(directory="swagger"), name="swagger")

# Serve custom index.html for Swagger UI
templates = Jinja2Templates(directory="templates")


oauth2_scheme = OAuth2PasswordBearer(tokenUrl="token")


@app.get("/docs", response_class=HTMLResponse, tags=["Doc"])
async def read_docs(request: Request):
    return templates.TemplateResponse("index.html", {"request": request})


# Custom OpenAPI documentation


def custom_openapi():
    if app.openapi_schema:
        return app.openapi_schema
    openapi_schema = get_openapi(
        title="Fake Salesforce Service",
        version="1.0.0",
        description="This is a mock Salesforce REST API implemented using FastAPI that provides several endpoints for"
        + "creating, retrieving, updating, and deleting records, as well as sending emails.\n\nThe goal of this app is to "
        + "provide a fake Salesforce backend to test the RPA Request Handler.\n\nThe app uses TinyDB as a fake database.",
        routes=app.routes,
    )
    # Modify the schema as needed
    app.openapi_schema = openapi_schema
    return app.openapi_schema


app.openapi = custom_openapi

# db = TinyDB('app/db.json', storage=JSONStorage, middlewares=[UUID4Middleware])


# Function to create a db, this will be used to inject mock db from unit tests
def get_db() -> TinyDB:
    records_db = TinyDB(
        "app/db.json", sort_keys=False, indent=4, separators=(",", ": ")
    )
    records_db.default_table_name = "records"
    try:
        yield records_db
    finally:
        records_db.close()


# Set table ID type (default int)
Table.document_id_class = str


# Check if the given string matches the format of an OAuth2 token
def is_oauth2_token(token: str) -> bool:
    # Regex pattern for matching an OAuth2 token
    pattern = r"^[A-Za-z0-9-_]+?\.[A-Za-z0-9-_]+?\.([A-Za-z0-9-_]+)?$"
    return bool(re.match(pattern, token))


def get_oauth2_token(header: str) -> Optional[str]:
    """
    Extract the OAuth2 token from the "Authorization" header.

    Args:
        header (str): The "Authorization" header value.

    Returns:
        str: The OAuth2 token if it is present in the header, else None.
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


# Get record endpoint
@app.get(
    f"{PDRCASE_GET_ENDPOINT}/{{id}}",
    response_model=Dict[str, Record],
    status_code=200,
    tags=["Record"],
)
async def get_record(
    id: str, Authorization: str = Header(None), db: TinyDB = Depends(get_db)
):
    """
    Retrieve a record by its ID.

    Args:
        id (str): The ID of the record to retrieve.
        token (str): A bearer token for authentication.

    Returns:
        dict: A dictionary containing a single key-value pair, where the key is "record"
            and the value is a Record object.

    Raises:
        HTTPException: If the token is missing or invalid, or if the record is not found.
    """
    token = get_oauth2_token(Authorization)
    # Check if token is missing in the header
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")

    # Get records table
    records_table = db.table("records")

    # Retrieve the record from the "records" table
    record = records_table.get(doc_id=id)

    if record is None:
        # If the record is not found, raise an HTTPException with status code 404
        raise HTTPException(status_code=404, detail="Record not found")

    # Create a Record object and return it
    return {"record": record}


# Create Record endpoint
@app.post(
    PDRCASE_CREATE_ENDPOINT,
    response_model=Dict[str, Record],
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
        data (CreateRecord): A CreateRecord object containing the data for the new record.
        token (str): A bearer token for authentication.

    Returns:
        dict: A dictionary containing a single key-value pair, where the key is "record"
            and the value is a Record object.

    Raises:
        HTTPException: If the token is missing or invalid, or if there is an error
            inserting the record into the database.
    """
    # Check if token is missing in the header
    token = get_oauth2_token(Authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_oauth2_token(token):
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
    # Return the new record in a dictionary format with a single key-value pair
    return {"record": record}


# Update Record endpoint
@app.patch(
    f"{PDRCASE_UPDATE_ENDPOINT}/{{id}}",
    response_model=Dict[str, str],
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
        id (str): The ID of the record to update.
        update (RecordUpdate): A RecordUpdate object containing the new approval status.
        token (str): A bearer token for authentication.

    Returns:
        dict: A dictionary containing a single key-value pair, where the key is "record"
            and the value is a Record object.

    Raises:
        HTTPException: If the token is missing or invalid, if the record is not found,
            or if there is an error updating the record in the database.
    """
    # Check if token is missing in the header
    token = get_oauth2_token(Authorization)
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_oauth2_token(token):
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
    token: str = Header(...),
    db: TinyDB = Depends(get_db),
):
    """
    Send an email with the provided email information.

    Args:
        email_info (EmailInfo): An EmailInfo object containing the email information.
        token (str): A bearer token for authentication.

    Returns:
        EmailStatus: An EmailStatus object containing the email information and the status code.

    Raises:
        HTTPException: If the token is missing or invalid.
    """
    # Check if token is missing in the header
    if not is_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    # Read the sender email and password from the .env file
    sender_email = os.getenv("SENDER_EMAIL")
    sender_password = os.getenv("SENDER_PASSWORD")

    # Set up the email content and headers
    recipient_email = email_info.recipient
    subject = email_info.subject
    content = email_info.content

    # Get email sender from factory
    email_sender = EmailSenderFactory.get_email_sender("smtp")

    # Send the email
    email_sender.send_email(
        sender_email, sender_password, recipient_email, subject, content
    )

    # Return an EmailStatus object with the email information and status code
    return EmailStatus(email_info=email_info, status_code=200)


@app.get(PDRCASE_TEST_ENDPOINT, tags=["Test"])
async def test_endpoint(Authorization: str = Header(...), status_code=200):
    """
    Check if the service is running.
    """
    token = get_oauth2_token(Authorization)
    # Check if token is missing in the header
    if not token:
        raise HTTPException(status_code=401, detail="Bearer token is missing in header")

    if not is_oauth2_token(token):
        raise HTTPException(status_code=401, detail="Bearer token is invalid")

    return "Service is up and running."
