from fastapi.testclient import TestClient
from tinydb import TinyDB, table
from app.main import app, get_db
from app.models import RecordUpdate
import os
import pytest
from app.endpoints import (
    PDRCASE_GET_ENDPOINT,
    PDRCASE_CREATE_ENDPOINT,
    PDRCASE_UPDATE_ENDPOINT,
    PDRCASE_EMAIL_ENDPOINT,
    PDRCASE_TEST_ENDPOINT,
)

# Define test db
test_db = TinyDB(
    "tests/test_db.json", sort_keys=False, indent=4, separators=(",", ": ")
)
# Change default table name (_default) to records
test_db.default_table_name = "records"


# Define a mock version of `get_db` that returns the test database instance
def mock_get_db() -> TinyDB:
    return test_db


# Inject the test database into the production code to test
app.dependency_overrides[get_db] = mock_get_db

# Create test client
client = TestClient(app)

# TEST_TOKEN = "00Dt0000000GzGE!ARUAQNPVPsI9uTOB2NK7GXEf1bLLcK.lDvu0EvGmQpPxLY33gcOFoCkHQmqXQ34Y3vleRuRyFcNa6VY_8YDgEdqjAXyru.3W"
TEST_TOKEN = os.getenv("ACCESS_TOKEN")

# @pytest.fixture
# def test_db():
#     return TinyDB("tests/test_db.json")


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
    assert response.json() == {"record": test_record}

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
    assert response.json() == {"record": mock_record}


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
