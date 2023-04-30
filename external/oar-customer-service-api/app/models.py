from pydantic import BaseModel, EmailStr
from datetime import datetime


class OauthToken(BaseModel):
    access_token: str
    instance_url: str

    class Config:
        schema_extra = {
            "example": {
                "access_token": "00D1g0000000abc!AQcAQLOpWfY8jJ4G4aP7....",
                "instance_url": "https://test.example.com",
            }
        }


class UserInfo(BaseModel):
    fullName: str
    organization: str
    email: EmailStr
    receiveEmails: str
    country: str
    approvalStatus: str
    productTitle: str = None
    subject: str
    description: str

    class Config:
        schema_extra = {
            "example": {
                "fullName": "Omar Ilias EL MIMOUNI",
                "organization": "NIST",
                "email": "omarilias.elmimouni@nist.gov",
                "receiveEmails": "True",
                "country": "United States",
                "approvalStatus": "Pending",
                "productTitle": "Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
                "subject": "849E1CC6FBE2C4C7E053B3570681FE987034",
                "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
            }
        }


class Record(BaseModel):
    id: str
    caseNum: str
    userInfo: UserInfo

    class Config:
        schema_extra = {
            "example": {
                "id": "5dc03d34-3cef-4238-9dcf-678302694c46",
                "caseNum": "5678",
                "userInfo": {
                    "fullName": "Omar Ilias EL MIMOUNI",
                    "organization": "NIST",
                    "email": "omarilias.elmimouni@nist.gov",
                    "receiveEmails": "True",
                    "country": "United States",
                    "approvalStatus": "Pending",
                    "productTitle": "Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory",
                    "subject": "849E1CC6FBE2C4C7E053B3570681FE987034",
                    "description": "Product Title:\n  Data from: Collaborative Guarded-Hot-Plate Tests between the National Institute of Standards and Technology and the National Physical Laboratory \n\n Purpose of Use: \nLearning and Research",
                },
            }
        }


class RecordWrapper(BaseModel):
    record: Record


class RecordUpdate(BaseModel):
    Approval_Status__c: str

    class Config:
        schema_extra = {
            "example": {"Approval_Status__c": "Approved_2023-04-25T10:00:00.000Z"}
        }


class RecordUpdateResponse(BaseModel):
    recordId: str
    approvalStatus: str

    class Config:
        schema_extra = {
            "example": {
                "recordId": "5dc03d34-3cef-4238-9dcf-678302694c46",
                "approvalStatus": "Approved_2023-04-25T10:00:00.000Z",
            }
        }


class CreateRecord(BaseModel):
    userInfo: UserInfo


class EmailInfo(BaseModel):
    recipient: EmailStr
    recordId: str
    subject: str
    content: str

    class Config:
        schema_extra = {
            "example": {
                "recordId": "bca9a7508cda11eda5b42a58cc9fff89",
                "recipient": "elmimouni.o.i@gmail.com",
                "subject": "Test email",
                "content": "<h1>Test Email</h1><div>Congratulations, it is working.</div>",
            }
        }


class EmailStatus(BaseModel):
    email_info: EmailInfo
    timestamp: str

    class Config:
        schema_extra = {
            "example": {
                "timestamp": datetime.now().strftime("%Y-%m-%dT%H:%M:%S.%fZ"),
                "email_info": {
                    "recordId": "bca9a7508cda11eda5b42a58cc9fff89",
                    "recipient": "elmimouni.o.i@gmail.com",
                    "subject": "Test email",
                    "content": "<h1>Test Email</h1><div>Congratulations, it is working.</div>",
                },
            }
        }
