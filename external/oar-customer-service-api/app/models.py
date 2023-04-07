from typing import Dict
from pydantic import BaseModel, EmailStr


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


class Record(BaseModel):
    id: str
    caseNum: str
    userInfo: UserInfo


class RecordUpdate(BaseModel):
    Approval_Status__c: str


class CreateRecord(BaseModel):
    userInfo: UserInfo


class EmailInfo(BaseModel):
    recipient: EmailStr
    recordId: str
    subject: str
    content: str

    class Config:
        allow_population_by_field_name = True
        arbitrary_types_allowed = True
        schema_extra = {
            "example": {
                "recordId": "bca9a7508cda11eda5b42a58cc9fff89",
                "recipient": "elmimouni.o.i@gmail.com",
                "subject": "Test email",
                "content": """<h1>Test Email</h1>
                              <div>Congratulations, it is working.</div>
                           """,
            }
        }


class EmailStatus(BaseModel):
    email_info: EmailInfo
    status_code: int
