import os
from datetime import datetime, timedelta
from unittest import mock

import jwt
import pytest
from dotenv import load_dotenv
from fastapi.testclient import TestClient
from tinydb import TinyDB, table

from app.main import app, get_db, get_public_keys_db
from app.models import RecordUpdate, RecordWrapper

# Define path for different endpoints
PDRCASE_OAUTH2_ENDPOINT = os.environ["PDRCASE_OAUTH2_ENDPOINT"]
PDRCASE_GET_ENDPOINT = os.environ["PDRCASE_GET_ENDPOINT"]
PDRCASE_CREATE_ENDPOINT = os.environ["PDRCASE_CREATE_ENDPOINT"]
PDRCASE_UPDATE_ENDPOINT = os.environ["PDRCASE_UPDATE_ENDPOINT"]
PDRCASE_EMAIL_ENDPOINT = os.environ["PDRCASE_EMAIL_ENDPOINT"]
PDRCASE_TEST_ENDPOINT = os.environ["PDRCASE_TEST_ENDPOINT"]

# Define test db
test_db = TinyDB(
    "tests/test_db.json", sort_keys=False, indent=4, separators=(",", ": ")
)
# Change default table name (_default) to records
test_db.default_table_name = "records"


# Define a mock version of `get_db` that returns the test database instance
def mock_get_db() -> TinyDB:
    return test_db


# Define test db for public keys
test_pks_db = TinyDB(
    "tests/test_public_keys.json", sort_keys=False, indent=4, separators=(",", ": ")
)
# Change default table name (_default) to public_keys
test_pks_db.default_table_name = "public_keys"


# Define a mock version of `get_public_keys_db` that returns the test database instance
def mock_get_pks_db() -> TinyDB:
    return test_pks_db


# Inject the test databases into the production code to test
app.dependency_overrides[get_db] = mock_get_db
app.dependency_overrides[get_public_keys_db] = mock_get_pks_db

# Create test client
client = TestClient(app)

# Load the .env file for testing
load_dotenv("tests/.test.env")


def generate_test_assertion():
    # Set the payload with the necessary claims
    payload = {
        "sub": "test_sub",
        "iss": "123",
        "aud": "http://test.localhost.com",
        "exp": datetime.utcnow() + timedelta(minutes=5),
    }
    private_key_path = "tests/keys/test_private_key.pem"
    # Load the private key from the PEM file
    with open(private_key_path, "rb") as f:
        private_key = f.read()

    # Encode the payload into a JWT token with the private key
    assertion = jwt.encode(payload, private_key, algorithm="RS256")
    print(assertion)
    return assertion


def test_get_token():
    client = TestClient(app)

    # Test with valid input
    params = {
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": generate_test_assertion(),
    }
    response = client.post("/services/oauth2/token", params=params)
    assert response.status_code == 200
    assert "access_token" in response.json()

    # Test with invalid grant type
    params = {
        "grant_type": "unsupported_grant_type",
        "assertion": generate_test_assertion(),
    }
    response = client.post("/services/oauth2/token", params=params)
    assert response.status_code == 400
    assert response.json()["detail"] == {"error": "unsupported_grant_type"}

    # Test with invalid assertion
    params = {
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": "invalid-jwt-assertion",
    }
    response = client.post("/services/oauth2/token", params=params)
    assert response.status_code == 400
    assert "error" in response.json()["detail"]


def generate_test_token():
    payload = {
        "sub": "test_sub",
        "iss": "123",
        "aud": "http://test.localhost.com",
        "exp": datetime.utcnow() + timedelta(minutes=5),
    }
    # Generate JWT token
    secret_key = os.environ["SECRET_KEY"]
    print(secret_key)
    token = jwt.encode(payload, secret_key)

    return token


TEST_TOKEN = generate_test_token()


def test_get_record():
    # Create a test client for the FastAPI app
    client = TestClient(app)

    # Test case 1: Valid record ID and bearer token
    # Insert a test record into the database
    test_record = {
        "id": "3a6cf726-5aba-40f2-99ef-084ff95e720e",
        "caseNum": "392650",
        "userInfo": {
            "fullName": "John Doe",
            "organization": "NASA",
            "email": "jane.doe@test.gov",
            "receiveEmails": "True",
            "country": "United States",
            "approvalStatus": "Pending",
            "productTitle": "Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
            "subject": "RPA: ark:\\88434\\mds2\\2106",
            "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
        },
    }
    # Get records tables
    records_table = test_db.table("records")
    # Cleanup and empty table to avoid getting duplicates
    records_table.truncate()
    # Insert mock record into mock database
    records_table.insert(table.Document(test_record, doc_id=test_record["id"]))

    # Send a GET request to the get_record endpoint with the test record ID and bearer token
    response = client.get(
        f"{PDRCASE_GET_ENDPOINT}/{test_record['id']}",
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
    )

    # Check that the response status code is 200 OK
    assert response.status_code == 200
    # Check that the response body contains the expected record information
    assert response.json() == RecordWrapper(record=test_record)

    # Test case 2: Invalid record ID
    # Send a GET request to the get_record endpoint with an invalid record ID and bearer token
    response = client.get(
        f"{PDRCASE_GET_ENDPOINT}/invalid_id",
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
    )
    # Check that the response status code is 404 NOT FOUND
    assert response.status_code == 404
    # Check that the response body contains the expected error message
    assert response.json() == {"detail": "Record not found"}

    # Test case 3: Missing bearer token
    # Send a GET request to the get_record endpoint with a missing bearer token
    response = client.get(f"{PDRCASE_GET_ENDPOINT}/{test_record['id']}")
    # Check that the response status code is 401 UNAUTHORIZED
    assert response.status_code == 401
    # Check that the response body contains the expected error message
    assert response.json() == {"detail": "Bearer token is missing in header"}

    # Cleanup and empty table to avoid getting duplicates
    records_table.truncate()


@pytest.fixture
def mock_record():
    test_record = {
        "id": "3a6cf726-5aba-40f2-99ef-084ff95e720e",
        "caseNum": "392650",
        "userInfo": {
            "fullName": "John Doe",
            "organization": "NASA",
            "email": "jane.doe@test.gov",
            "receiveEmails": "True",
            "country": "United States",
            "approvalStatus": "Pending",
            "productTitle": "Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
            "subject": "RPA: ark:\\88434\\mds2\\2106",
            "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
        },
    }
    # Get records tables
    records_table = test_db.table("records")
    # Cleanup and empty table to avoid getting duplicates
    records_table.truncate()
    # Insert mock record into mock database
    records_table.insert(table.Document(test_record, doc_id=test_record["id"]))
    yield test_record
    records_table.truncate()


def test_get_record_success(mock_record):
    response = client.get(
        f"{PDRCASE_GET_ENDPOINT}/{mock_record['id']}",
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
    )
    assert response.status_code == 200
    assert response.json() == RecordWrapper(record=mock_record)


def test_get_record_missing_token(mock_record):
    response = client.get(
        f"{PDRCASE_GET_ENDPOINT}/{mock_record['id']}",
    )
    assert response.status_code == 401
    assert response.json() == {"detail": "Bearer token is missing in header"}


def test_get_record_invalid_token(mock_record):
    response = client.get(
        f"{PDRCASE_GET_ENDPOINT}/{mock_record['id']}",
        headers={"Authorization": "Bearer invalid_token"},
    )
    assert response.status_code == 401
    assert response.json() == {"detail": "Bearer token is invalid"}


def test_get_record_not_found():
    response = client.get(
        f"{PDRCASE_GET_ENDPOINT}/invalid_id",
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
    )
    assert response.status_code == 404
    assert response.json() == {"detail": "Record not found"}


def test_create_record_success():
    # Get records tables
    records_table = test_db.table("records")
    # Cleanup and empty table to avoid getting duplicates
    records_table.truncate()
    # Mock request payload
    payload = {
        "userInfo": {
            "fullName": "John Foe",
            "organization": "NASA",
            "email": "jane.doe@test.gov",
            "receiveEmails": "True",
            "country": "United States",
            "approvalStatus": "Pending",
            "productTitle": "Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
            "subject": "RPA: ark:\\88434\\mds2\\2106",
            "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
        }
    }

    # Send POST request to create record
    response = client.post(
        PDRCASE_CREATE_ENDPOINT,
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
        json=payload,
    )

    # Check if response is successful and contains the newly created record
    assert response.status_code == 200
    assert response.json()["record"]["userInfo"] == payload["userInfo"]

    # Delete the newly created record to cleanup the database
    records_table.remove(doc_ids=[response.json()["record"]["id"]])


def test_create_record_missing_token():
    response = client.post(
        PDRCASE_CREATE_ENDPOINT,
        json={
            "userInfo": {
                "fullName": "John Doe",
                "organization": "NASA",
                "email": "jane.doe@test.gov",
                "receiveEmails": "True",
                "country": "United States",
                "approvalStatus": "Pending",
                "productTitle": "Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
                "subject": "RPA: ark:\\88434\\mds2\\2106",
                "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
            }
        },
    )
    assert response.status_code == 401
    assert response.json() == {"detail": "Bearer token is missing in header"}


def test_update_record_success():
    # Add a record to the test database
    test_record = {
        "id": "123",
        "caseNum": "456",
        "userInfo": {
            "fullName": "John Doe",
            "organization": "NASA",
            "email": "jane.doe@test.gov",
            "receiveEmails": "True",
            "country": "United States",
            "approvalStatus": "Pending",
            "productTitle": "Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
            "subject": "RPA: ark:\\88434\\mds2\\2106",
            "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
        },
    }
    records_table = test_db.table("records")
    records_table.insert(table.Document(test_record, doc_id=test_record["id"]))

    # Define the update request payload
    update_payload = RecordUpdate(Approval_Status__c="Approved")

    # Call the update_record endpoint
    response = client.patch(
        f"{PDRCASE_UPDATE_ENDPOINT}/{test_record['id']}",
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
        json=update_payload.dict(),
    )

    # Check the response status code and content
    assert response.status_code == 200
    assert response.json() == {
        "recordId": test_record["id"],
        "approvalStatus": "Approved",
    }

    # Check that the record has been updated in the database
    updated_record = records_table.get(doc_id=test_record["id"])
    assert updated_record["userInfo"]["approvalStatus"] == "Approved"


def test_send_email_success():
    email_info = {
        "recordId": "bca9a7508cda11eda5b42a58cc9fff89",
        "recipient": "jane.doe@test.com",
        "subject": "Test Email",
        "content": "This is a test email.",
    }

    with mock.patch(
        "app.main.EmailSenderFactory.get_email_sender"
    ) as mock_email_sender:
        mock_email_sender.return_value.send_email = lambda *args: (
            200,
            "Email sent successfully",
        )

        response = client.post(
            PDRCASE_EMAIL_ENDPOINT,
            json=email_info,
            headers={"Authorization": f"Bearer {TEST_TOKEN}"},
        )

        assert response.status_code == 200

        email_status = response.json()
        assert "timestamp" in response.json()
        # Verify the timestamp is a valid ISO datetime string
        try:
            datetime.fromisoformat(email_status["timestamp"])
        except ValueError:
            assert False, "Invalid timestamp format"
        # Verify email_info matches
        assert email_status["email_info"] == email_info


def test_test_endpoint_success():
    response = client.get(
        PDRCASE_TEST_ENDPOINT,
        headers={"Authorization": f"Bearer {TEST_TOKEN}"},
    )
    assert response.status_code == 200
    assert response.text == '"Service is up and running."'
